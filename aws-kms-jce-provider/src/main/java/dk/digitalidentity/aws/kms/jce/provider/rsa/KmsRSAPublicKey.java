package dk.digitalidentity.aws.kms.jce.provider.rsa;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;

import dk.digitalidentity.aws.kms.jce.provider.KmsPublicKey;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class KmsRSAPublicKey implements KmsPublicKey, RSAPublicKey {
    private static final long serialVersionUID = -8126502677752092492L;
    
	@NonNull
    private final String id;
    private final RSAPublicKey publicKey;

    @Override
    public BigInteger getPublicExponent() {
        return publicKey.getPublicExponent();
    }

    @Override
    public String getAlgorithm() {
        return publicKey.getAlgorithm();
    }

    @Override
    public String getFormat() {
        return publicKey.getFormat();
    }

    @Override
    public byte[] getEncoded() {
        return publicKey.getEncoded();
    }

    @Override
    public BigInteger getModulus() {
        return publicKey.getModulus();
    }

}
