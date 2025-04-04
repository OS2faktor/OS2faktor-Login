package dk.digitalidentity.aws.kms.jce.provider.rsa;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest;
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;

@Slf4j
public abstract class KmsRSAKeyFactory {

    /**
     * Retrieve KMS KeyPair reference (Private Key / Public Key) based on the keyId informed.
     * Also fetch the real Public Key from KMS.
     * Use only when it is necessary the real Public Key.
     *
     * @param kmsClient
     * @param keyId
     * @return
     */
    public static KeyPair getKeyPair(@NonNull KmsClient kmsClient, @NonNull String keyId) {
    	KmsRSAPublicKey publicKey = getPublicKey(kmsClient, keyId);
        return new KeyPair(publicKey, getPrivateKey(keyId, publicKey.getModulus()));
    }

    /**
     * Retrieve KMS KeyPair reference (Private Key / Public Key) based on the keyId informed.
     * The real Public Key is not fetched.
     *
     * @param keyId
     * @return
     */
    public static KeyPair getKeyPair(@NonNull String keyId) {
    	KmsRSAPublicKey publicKey = getPublicKey(keyId);
    	
        return new KeyPair(publicKey, getPrivateKey(keyId, publicKey.getModulus()));
    }

    /**
     * Retrieve KMS Private Key reference based on the keyId informed.
     *
     * @param keyId
     * @return
     */
    public static KmsRSAPrivateKey getPrivateKey(@NonNull String keyId, @NonNull BigInteger modulus) {
        return new KmsRSAPrivateKey(keyId, modulus);
    }

    /**
     * Retrieve KMS Public Key reference based on the keyId informed.
     *
     * @param keyId
     * @return
     */
    public static KmsRSAPublicKey getPublicKey(@NonNull String keyId) {
        return new KmsRSAPublicKey(keyId, null);
    }

    /**
     * Retrieve KMS Public Key reference based on the keyId informed.
     * Also fetch the real Public Key from KMS.
     * Use only when it is necessary the real Public Key.
     *
     * @param kmsClient
     * @param keyId
     * @return
     */
    @SneakyThrows
    public static KmsRSAPublicKey getPublicKey(@NonNull KmsClient kmsClient, @NonNull String keyId) {
    	log.debug("kmsClient.getPublicKey()");

        GetPublicKeyRequest getPublicKeyRequest = GetPublicKeyRequest.builder().keyId(keyId).build();
        GetPublicKeyResponse getPublicKeyResponse = kmsClient.getPublicKey(getPublicKeyRequest);

        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(getPublicKeyResponse.publicKey().asByteArray());
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return new KmsRSAPublicKey(keyId, (RSAPublicKey) keyFactory.generatePublic(keySpec));
    }

}
