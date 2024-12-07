package dk.digitalidentity.aws.kms.jce.provider;

import java.security.PublicKey;

public interface KmsPublicKey extends KmsKey {
    PublicKey getPublicKey();
}
