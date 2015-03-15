package com.android.server.wifi.anqp.eap;

import com.android.server.wifi.anqp.Constants;

import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An EAP Method, part of the NAI Realm ANQP element, specified in
 * IEEE802.11-2012 section 8.4.4.10, figure 8-420
 */
public class EAPMethod {
    private final EAP.EAPMethodID mEAPMethodID;
    private final Map<EAP.AuthInfoID, Set<AuthParam>> mAuthParams;

    public EAPMethod(ByteBuffer payload) throws ProtocolException {
        if (payload.remaining() < 3) {
            throw new ProtocolException("Runt EAP Method: " + payload.remaining());
        }

        int length = payload.get() & Constants.BYTE_MASK;
        int methodID = payload.get() & Constants.BYTE_MASK;
        int count = payload.get() & Constants.BYTE_MASK;

        mEAPMethodID = EAP.mapEAPMethod(methodID);
        mAuthParams = new EnumMap<EAP.AuthInfoID, Set<AuthParam>>(EAP.AuthInfoID.class);

        int realCount = 0;

        ByteBuffer paramPayload = payload.duplicate();
        paramPayload.limit(paramPayload.position() + length - 2);
        payload.position(payload.position() + length - 2);
        while (paramPayload.hasRemaining()) {
            int id = paramPayload.get() & Constants.BYTE_MASK;

            EAP.AuthInfoID authInfoID = EAP.mapAuthMethod(id);
            if (authInfoID == null) {
                throw new ProtocolException("Unknown auth parameter ID: " + id);
            }

            int len = paramPayload.get() & Constants.BYTE_MASK;
            if (len == 0 || len > paramPayload.remaining()) {
                throw new ProtocolException("Bad auth method length: " + len);
            }

            switch (authInfoID) {
                case ExpandedEAPMethod:
                    addAuthParam(new ExpandedEAPMethod(authInfoID, len, paramPayload));
                    break;
                case NonEAPInnerAuthType:
                    addAuthParam(new NonEAPInnerAuth(len, paramPayload));
                    break;
                case InnerAuthEAPMethodType:
                    addAuthParam(new InnerAuthEAP(len, paramPayload));
                    break;
                case ExpandedInnerEAPMethod:
                    addAuthParam(new ExpandedEAPMethod(authInfoID, len, paramPayload));
                    break;
                case CredentialType:
                    addAuthParam(new Credential(authInfoID, len, paramPayload));
                    break;
                case TunneledEAPMethodCredType:
                    addAuthParam(new Credential(authInfoID, len, paramPayload));
                    break;
                case VendorSpecific:
                    addAuthParam(new VendorSpecificAuth(len, paramPayload));
                    break;
            }

            realCount++;
        }
        if (realCount != count)
            throw new ProtocolException("Invalid parameter count: " + realCount +
                    ", expected " + count);
    }

    private void addAuthParam(AuthParam param) {
        Set<AuthParam> authParams = mAuthParams.get(param.getAuthInfoID());
        if (authParams == null) {
            authParams = new HashSet<AuthParam>();
            mAuthParams.put(param.getAuthInfoID(), authParams);
        }
        authParams.add(param);
    }

    public Map<EAP.AuthInfoID, Set<AuthParam>> getAuthParams() {
        return Collections.unmodifiableMap(mAuthParams);
    }

    public EAP.EAPMethodID getEAPMethodID() {
        return mEAPMethodID;
    }

    public boolean matchesAuthParams(EAPMethod other) {
        for (Map.Entry<EAP.AuthInfoID, Set<AuthParam>> entry : other.getAuthParams().entrySet()) {

            Set<AuthParam> myParams = mAuthParams.get(entry.getKey());
            if (myParams == null)
                continue;

            Set<AuthParam> otherParams = entry.getValue();

            Set<AuthParam> iterationSet;
            Set<AuthParam> seekSet;
            if (myParams.size() >= otherParams.size()) {
                seekSet = myParams;
                iterationSet = otherParams;
            } else {
                seekSet = otherParams;
                iterationSet = myParams;
            }

            for (AuthParam param : iterationSet) {
                if (seekSet.contains(param)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "EAPMethod{" +
                "mEAPMethodID=" + mEAPMethodID +
                ", mAuthParams=" + mAuthParams +
                '}';
    }
}
