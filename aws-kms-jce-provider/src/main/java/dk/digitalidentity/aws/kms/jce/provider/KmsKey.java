package dk.digitalidentity.aws.kms.jce.provider;

import java.security.Key;

public interface KmsKey extends Key {
    String getId();
}
