package dk.digitalidentity.util;

public class Constants {
    // Session Keys
    public static final String AUTHN_REQUEST = "AUTHN_REQUEST";
    public static final String AUTHN_INSTANT = "AUTHN_INSTANT";
    public static final String LOGOUT_REQUEST = "LOGOUT_REQUEST";
    public static final String RELAY_STATE = "RelayState";
    public static final String SAML_REQUEST = "SAMLRequest";

    public static final String NEMID_MITID_AUTHENTICATION_LEVEL = "NEMID_MITID_AUTHENTICATION_LEVEL";
    public static final String MFA_AUTHENTICATION_LEVEL = "MFA_AUTHENTICATION_LEVEL";
    public static final String MFA_AUTHENTICATION_LEVEL_TIMESTAMP = "MFA_AUTHENTICATION_LEVEL_TIMESTAMP";
    public static final String MFA_CLIENTS = "MFA_CLIENTS";
    public static final String MFA_CLIENT_REQUIRED_NSIS_LEVEL = "MFA_CLIENT_REQUIRED_NSIS_LEVEL";
    public static final String MFA_SELECTED_CLIENT = "MFA_SELECTED_CLIENT";
    public static final String SUBSCRIPTION_KEY = "SubscriptionKey";
    public static final String LOGIN_STATE = "LOGIN_STATE";
    public static final String ENTITY_IDS = "ENTITY_IDS";
    public static final String AD_PERSON_ID = "AD_PERSON_ID";
    public static final String AUTHENTICATED_WITH_AD_PASSWORD = "AUTHENTICATED_WITH_AD_PASSWORD";
    public static final String REQUESTED_USERNAME = "REQUESTED_USERNAME";

    public static final String PASSWORD_AUTHENTIFICATION_LEVEL = "PASSWORD_AUTHENTIFICATION_LEVEL";
    public static final String PASSWORD_AUTHENTIFICATION_LEVEL_TIMESTAMP = "PASSWORD_AUTHENTIFICATION_LEVEL_TIMESTAMP";
    public static final String PASSWORD = "PASSWORD";
    public static final String PASSWORD_CHANGE_FLOW = "PASSWORD_CHANGE_FLOW";
    public static final String PASSWORD_CHANGE_SUCCESS_REDIRECT = "PASSWORD_CHANGE_SUCCESS_REDIRECT";
    public static final String PASSWORD_EXPIRY_FLOW = "PASSWORD_EXPIRY_FLOW";
    public static final String PASSWORD_FORCE_CHANGE_FLOW = "PASSWORD_FORCE_CHANGE_FLOW";
    public static final String PASSWORD_CHANGE_FAILURE_REASON = "PASSWORD_CHANGE_FAILURE_REASON";
    public static final String LOGIN_REQUEST = "SP_LOGIN_REQUEST";
    public static final String APPROVE_CONDITIONS_FLOW = "APPROVE_CONDITIONS_FLOW";
    public static final String DECLINE_USER_ACTIVATION = "DECLINE_USER_ACTIVATION";

    public static final String NEMID_OR_MITID_AUTHENTICATION_FLOW = "NEMID_OR_MITID_AUTHENTICATION_FLOW";
    public static final String AUTHENTICATED_WITH_NEMID_OR_MITID = "AUTHENTICATED_WITH_NEMID_OR_MITID";
    public static final String DEDICATED_ACTIVATE_ACCOUNT_FLOW = "DEDICATED_ACTIVATE_ACCOUNT_FLOW";
    public static final String NEM_LOG_IN_BROKER_FLOW = "NEM_LOG_IN_BROKER_FLOW";
    public static final String CHOOSE_PASSWORD_RESET_OR_UNLOCK_ACCOUNT_FLOW = "CHOOSE_PASSWORD_RESET_OR_UNLOCK_ACCOUNT_FLOW";
    public static final String INSUFFICIENT_NSIS_LEVEL_FROM_MIT_ID = "INSUFFICIENT_NSIS_LEVEL_FROM_MIT_ID";
    public static final String NEMID_PID = "NEMID_PID";
    public static final String MIT_ID_NAME_ID = "MIT_ID_NAME_ID";
    public static final String AVAILABLE_PEOPLE = "AVAILABLE_PEOPLE";
    public static final String ACTIVATE_ACCOUNT_FLOW = "ACTIVATE_ACCOUNT_FLOW";

    public static final String IP_ADDRESS = "IP_ADDRESS";
    
    public static final String DO_NOT_USE_CURRENT_AD_PASSWORD = "DO_NOT_USE_CURRENT_AD_PASSWORD";

    public static final String PASSWORD_CHANGE_NOT_APPROVED_CONDITIONS = "PASSWORD_CHANGE_NOT_APPROVED_CONDITIONS";

    public static final String LOGIN_SELECT_CLAIMS_FLOW = "LOGIN_SELECT_CLAIMS_FLOW";
    public static final String LOGIN_SELECTABLE_CLAIMS = "LOGIN_SELECTABLE_CLAIMS";
    public static final String LOGIN_SELECTED_CLAIMS = "LOGIN_SELECTED_CLAIMS";

    // Common Attributes
    public static final String ATTRIBUTE_VALUE_FORMAT_BASIC = "urn:oasis:names:tc:SAML:2.0:attrname-format:basic";
    public static final String ATTRIBUTE_VALUE_FORMAT_URI = "urn:oasis:names:tc:SAML:2.0:attrname-format:uri";
    public static final String SPEC_VERSION = "https://data.gov.dk/model/core/specVersion";
    public static final String SPEC_VERSION_OIOSAML30 = "OIO-SAML-3.0";

    public static final String AUTHENTICATION_ASSURANCE_LEVEL = "https://data.gov.dk/concept/core/nsis/aal";
    public static final String LEVEL_OF_ASSURANCE = "https://data.gov.dk/concept/core/nsis/loa";
    public static final String LEVEL_OF_ASSURANCE_HIGH = "https://data.gov.dk/concept/core/nsis/loa/High";
    public static final String LEVEL_OF_ASSURANCE_SUBSTANTIAL = "https://data.gov.dk/concept/core/nsis/loa/Substantial";
    public static final String LEVEL_OF_ASSURANCE_LOW = "https://data.gov.dk/concept/core/nsis/loa/Low";
    
    // STIL constants
    public static final String STIL_LEVEL_OF_ASSURANCE_TOFAKTOR = "dk:unilogin:loa:ToFaktor";
    
    // professional attributes
    public static final String CVR = "https://data.gov.dk/model/core/eid/professional/cvr";
    public static final String ORGANISATION_NAME = "https://data.gov.dk/model/core/eid/professional/orgName";

    // logging constants
    public static final String INCOMING = "Incoming";
    public static final String OUTGOING = "Outgoing";
    public static final String SERVICE_PROVIDER = "SERVICE_PROVIDER";
    public static final String SESSION_INDEX = "SESSION_INDEX";
    public static final String PERSON_ID = "PERSON_ID";
    
    // NIST claim
    public static final String NIST_CLAIM = "dk:gov:saml:attribute:AssuranceLevel";
}
