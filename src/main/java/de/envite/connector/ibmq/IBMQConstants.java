package de.envite.connector.ibmq;

public final class IBMQConstants {

    private IBMQConstants() {}

    // -------------------------------------------------------------------------
    // IBM Cloud IAM authentication
    // -------------------------------------------------------------------------

    public static final String IAM_TOKEN_URL        = "https://iam.cloud.ibm.com/identity/token";
    public static final String IAM_GRANT_TYPE_KEY   = "grant_type";
    public static final String IAM_GRANT_TYPE_VALUE = "urn:ibm:params:oauth:grant-type:apikey";
    public static final String IAM_APIKEY_KEY       = "apikey";
    public static final String IAM_ACCESS_TOKEN     = "access_token";

    // -------------------------------------------------------------------------
    // Required request headers
    // -------------------------------------------------------------------------

    public static final String HEADER_SERVICE_CRN     = "Service-CRN";
    public static final String HEADER_IBM_API_VERSION = "IBM-API-Version";
    public static final String IBM_API_VERSION        = "2024-11-30";

    // -------------------------------------------------------------------------
    // URL paths
    // -------------------------------------------------------------------------

    public static final String API_PATH_JOBS = "/v1/jobs";
    public static final String API_PATH_RESULTS = "/results";
    public static final String UI_PATH_INSTANCES = "/instances/";
    public static final String UI_PATH_JOBS      = "/jobs/";

    // -------------------------------------------------------------------------
    // Job request fields
    // -------------------------------------------------------------------------

    public static final String FIELD_PROGRAM_ID = "program_id";
    public static final String FIELD_BACKEND    = "backend";
    public static final String FIELD_PARAMS     = "params";

    // -------------------------------------------------------------------------
    // Job response fields
    // -------------------------------------------------------------------------

    public static final String FIELD_ID     = "id";
    public static final String FIELD_STATUS = "status";

    // -------------------------------------------------------------------------
    // Job statuses
    // -------------------------------------------------------------------------

    public static final String STATUS_QUEUED     = "QUEUED";
    public static final String STATUS_COMPLETED  = "COMPLETED";
    public static final String STATUS_FAILED     = "FAILED";
    public static final String STATUS_CANCELLED  = "CANCELLED";
    public static final String STATUS_ERROR      = "ERROR";

    // -------------------------------------------------------------------------
    // Params fields
    // -------------------------------------------------------------------------

    public static final String FIELD_PUBS = "pubs";

    // -------------------------------------------------------------------------
    // Program IDs
    // -------------------------------------------------------------------------

    public static final String PROGRAM_SAMPLER   = "sampler";
    public static final String PROGRAM_ESTIMATOR = "estimator";
}
