package dk.digitalidentity.aws.kms.jce.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dk.digitalidentity.aws.kms.jce.provider.ec.KmsECKeyFactory;
import dk.digitalidentity.aws.kms.jce.provider.rsa.KmsRSAKeyFactory;
import dk.digitalidentity.aws.kms.jce.provider.rsa.KmsRSAPublicKey;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.AliasListEntry;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.ListAliasesRequest;
import software.amazon.awssdk.services.kms.model.ListAliasesResponse;

@Slf4j
@RequiredArgsConstructor
public class KmsKeyStore extends KeyStoreSpi {
	// cache to prevent unneeded KMS API calls
	private Set<AliasListEntry> aliasCache = null;
	private Map<String, Key> keyCache = new HashMap<>();
	
    private Certificate[] chain;

    @NonNull
    private final KmsClient kmsClient;

    @Override
    public Enumeration<String> engineAliases() {
        return Collections.enumeration(getAliases().stream().map(AliasListEntry::aliasName).collect(Collectors.toSet()));
    }

    @Override
    public boolean engineContainsAlias(String alias) {
        String prefixedAlias = getPrefixedAlias(alias);
        return getAliases().stream().filter(e -> e.aliasName().equals(prefixedAlias)).findAny().isPresent();
    }

    @Override
    public int engineSize() {
        return getAliases().size();
    }

    @Override
    public Key engineGetKey(String alias, char[] chars) throws NoSuchAlgorithmException, UnrecoverableKeyException {
    	if (keyCache.containsKey(alias)) {
    		return keyCache.get(alias);
    	}

    	log.debug("kmsClient.describeKey()");

        String prefixedAlias = getPrefixedAlias(alias);
        AliasListEntry aliasListEntry = getAliases().stream().filter(e -> e.aliasName().equals(prefixedAlias)).findAny().orElse(null);
        if (aliasListEntry == null) return null;

        DescribeKeyResponse describeKeyResponse = kmsClient.describeKey(builder -> builder.keyId(aliasListEntry.targetKeyId()));
        if (!describeKeyResponse.keyMetadata().hasSigningAlgorithms()) {
            throw new IllegalStateException("Unsupported Key type. Only signing keys are supported. Alias = " + alias);
        }

        boolean rsa = describeKeyResponse.keyMetadata().signingAlgorithmsAsStrings().stream().filter(a -> a.startsWith("RSA")).findAny().isPresent();
        
        KmsRSAPublicKey publicKey = null;
        if (rsa) {
        	publicKey = KmsRSAKeyFactory.getPublicKey(kmsClient, describeKeyResponse.keyMetadata().keyId());
        }
        
        Key result = rsa ? KmsRSAKeyFactory.getPrivateKey(describeKeyResponse.keyMetadata().keyId(), publicKey.getModulus()) : KmsECKeyFactory.getPrivateKey(describeKeyResponse.keyMetadata().keyId());
        
        if (result != null) {
        	keyCache.put(alias, result);
        }

        return result;
    }

    private String getPrefixedAlias(String alias) {
        return alias.startsWith("alias/") ? alias : "alias/" + alias;
    }

    private Set<AliasListEntry> getAliases() {
    	if (aliasCache != null) {
    		return aliasCache;
    	}

    	log.debug("kmsClient.listAliases()");

        ListAliasesResponse listAliasesResponse = null;
        String marker = null;
        Set<AliasListEntry> aliases = new HashSet<>();

        do {
            listAliasesResponse = kmsClient.listAliases(ListAliasesRequest.builder().marker(marker).build());
            aliases.addAll(listAliasesResponse.aliases());
            marker = listAliasesResponse.nextMarker();
        } while (listAliasesResponse.truncated());

        // if we got something, cache it so we do not need to lookup again
        if (aliases != null && aliases.size() > 0) {
        	aliasCache = aliases;
        }
        
        return aliases;
    }

    @Override
    public void engineLoad(InputStream inputStream, char[] chars) throws IOException, NoSuchAlgorithmException, CertificateException {
    	CertificateFactory factory = CertificateFactory.getInstance("X.509");
    	Certificate certificate = factory.generateCertificate(inputStream);
    	
    	chain = new Certificate[1];
    	chain[0] = certificate;
    }

    @Override
    public boolean engineIsKeyEntry(String s) {
    	// is always a key entry
    	return true;
    }

    @Override
    public boolean engineIsCertificateEntry(String s) {
    	// is never a certificate entry
    	return false;
    }

    @Override
    public Certificate[] engineGetCertificateChain(String s) {
    	// set on creation, and just returned to make JCE life easier
    	return chain;
    }

    @Override
    public Certificate engineGetCertificate(String s) {
    	return chain[0];
    }

    /*
    UNSUPPORTED OPERATIONS
     */

    @Override
    public Date engineGetCreationDate(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineSetKeyEntry(String s, Key key, char[] chars, Certificate[] certificates) throws KeyStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineSetKeyEntry(String s, byte[] bytes, Certificate[] certificates) throws KeyStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineSetCertificateEntry(String s, Certificate certificate) throws KeyStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineDeleteEntry(String s) throws KeyStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String engineGetCertificateAlias(Certificate certificate) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineStore(OutputStream outputStream, char[] chars) throws IOException, NoSuchAlgorithmException, CertificateException {
        throw new UnsupportedOperationException();
    }
}