package dk.digitalidentity.aws.kms.jce.provider.rsa;

import java.math.BigInteger;
import java.security.interfaces.RSAPrivateKey;

import dk.digitalidentity.aws.kms.jce.provider.KmsKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class KmsRSAPrivateKey implements KmsKey, RSAPrivateKey {
    private static final long serialVersionUID = -2163610535208567862L;
    
	@NonNull
    private final String id;

	@NonNull
	private final BigInteger modulus;

    private final String algorithm = "RSA";
    private final String format = "X.509";

    @Override
    public BigInteger getPrivateExponent() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getEncoded() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BigInteger getModulus() {
    	return modulus;
    }
}
