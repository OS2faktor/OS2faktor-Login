package dk.digitalidentity.util;

public class Constants {
    // Session Keys
    public static final String AUTHN_REQUEST = "AUTHN_REQUEST";
    public static final String LOGOUT_REQUEST = "LOGOUT_REQUEST";
    public static final String RELAY_STATE = "RelayState";
    public static final String SAMLRequest = "SAMLRequest";

    public static final String MFA_AUTHENTIFICATION_LEVEL = "MFA_AUTHENTIFICATION_LEVEL";
    public static final String MFA_CLIENTS = "MFA_CLIENTS";
    public static final String MFA_SELECTED_CLIENT = "MFA_CLIENTS";
    public static final String SUBSCRIPTION_KEY = "SubscriptionKey";
    public static final String LOGIN_STATE = "LOGIN_STATE";
    public static final String ENTITY_IDS = "ENTITY_IDS";
    public static final String AD_PERSON_ID = "AD_PERSON_ID";
    public static final String AUTHENTICATED_WITH_AD_PASSWORD = "AUTHENTICATED_WITH_AD_PASSWORD";

    public static final String PASSWORD_AUTHENTIFICATION_LEVEL = "PASSWORD_AUTHENTIFICATION_LEVEL";
    public static final String PASSWORD = "PASSWORD";

    public static final String AUTHENTICATED_WITH_NEMID = "AUTHENTICATED_WITH_NEMID";
    public static final String NEMID_PID = "NEMID_PID";
    public static final String AVAILABLE_PEOPLE = "AVAILABLE_PEOPLE";
    public static final String ACTIVATE_ACCOUNT_COMPLETED = "ACTIVATE_ACCOUNT_COMPLETED";


    // TODO Cleanup
    // Common Attributes
    public static final String ATTRIBUTE_VALUE_FORMAT = "urn:oasis:names:tc:SAML:2.0:attrname-format:basic";
    public static final String SPEC_VERSION = "https://data.gov.dk/model/core/specVersion";
    public static final String SPEC_VERSION_OIOSAML30 = "OIO-SAML-3.0";

    public static final String LEVEL_OF_ASSURANCE = "https://data.gov.dk/concept/core/nsis/loa";
    public static final String LEVEL_OF_ASSURANCE_HIGH = "https://data.gov.dk/concept/core/nsis/loa/High";
    public static final String LEVEL_OF_ASSURANCE_SUBSTANTIAL = "https://data.gov.dk/concept/core/nsis/loa/Substantial";
    public static final String LEVEL_OF_ASSURANCE_LOW = "https://data.gov.dk/concept/core/nsis/loa/Low";

    //Professional Person Attributes
    public static final String CVR = "https://data.gov.dk/model/core/eid/professional/cvr";
    public static final String CVR_VALUE = "20301823";

    public static final String ORGANISATION_NAME = "https://data.gov.dk/model/core/eid/professional/orgName";
    public static final String ORGANISATION_NAME_VALUE = "Digitaliseringsstyrelsen";

    // Logging constants
    public static final String INCOMING = "Incoming";
    public static final String OUTGOING = "Outgoing";
    public static final String SERVICE_PROVIDER = "SERVICE_PROVIDER";
    public static final String SESSION_INDEX = "SESSION_INDEX";
    public static final String PERSON_ID = "PERSON_ID";
}
