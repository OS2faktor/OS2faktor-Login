package dk.digitalidentity.common.dao.model.enums;

public enum NameIdFormat {
    PERSISTENT("urn:oasis:names:tc:SAML:2.0:nameid-format:persistent"),
    TRANSIENT("urn:oasis:names:tc:SAML:2.0:nameid-format:transient"),
    EMAIL_ADDRESS("urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress"),
    UNSPECIFIED("urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified"),
    X509_SUBJECT_NAME("urn:oasis:names:tc:SAML:1.1:nameid-format:X509SubjectName"),
    WINDOWS_DOMAIN_QUALIFIED_NAME("urn:oasis:names:tc:SAML:1.1:nameid-format:WindowsDomainQualifiedName"),
    KERBEROS("urn:oasis:names:tc:SAML:2.0:nameid-format:kerberos"),
    ENTITY("urn:oasis:names:tc:SAML:2.0:nameid-format:entity");

    public String value;

    private NameIdFormat(String value) {
        this.value = value;
    }
}
