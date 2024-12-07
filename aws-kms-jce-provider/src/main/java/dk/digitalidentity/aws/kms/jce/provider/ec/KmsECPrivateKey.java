package dk.digitalidentity.aws.kms.jce.provider.ec;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.math.BigInteger;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECParameterSpec;

import dk.digitalidentity.aws.kms.jce.provider.KmsKey;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class KmsECPrivateKey implements KmsKey, ECPrivateKey {
    private static final long serialVersionUID = -1052662970778444812L;
    
	@NonNull
    private final String id;
    private final String algorithm = "EC";
    private final String format = "PKCS#8";

    @Override
    public BigInteger getS() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getEncoded() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ECParameterSpec getParams() {
        throw new UnsupportedOperationException();
    }

}
