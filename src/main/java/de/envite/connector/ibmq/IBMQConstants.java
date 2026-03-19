package de.envite.connector.ibmq;

public final class IBMQConstants {

    private IBMQConstants() {}

    // -------------------------------------------------------------------------
    // IAM authentication
    // -------------------------------------------------------------------------

    public static final String IAM_TOKEN_URL        = "https://iam.cloud.ibm.com/identity/token";
    public static final String IAM_GRANT_TYPE_KEY   = "grant_type";
    public static final String IAM_GRANT_TYPE_VALUE = "urn:ibm:params:oauth:grant-type:apikey";
    public static final String IAM_APIKEY_KEY       = "apikey";
    public static final String IAM_ACCESS_TOKEN     = "access_token";

    // -------------------------------------------------------------------------
    // API paths
    // -------------------------------------------------------------------------

    public static final String PATH_JOBS    = "/v1/jobs";
    public static final String PATH_RESULTS = "/results";

    // -------------------------------------------------------------------------
    // Job request fields
    // -------------------------------------------------------------------------

    public static final String FIELD_PROGRAM_ID = "program_id";
    public static final String FIELD_BACKEND    = "backend";
    public static final String FIELD_HUB        = "hub";
    public static final String FIELD_GROUP      = "group";
    public static final String FIELD_PROJECT    = "project";
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
