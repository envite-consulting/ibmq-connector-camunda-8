package de.envite.connector.ibmq;

public final class IBMQConstants {

  public static final String IAM_TOKEN_URL = "https://iam.cloud.ibm.com/identity/token";

  // -------------------------------------------------------------------------
  // IBM Cloud IAM authentication
  // -------------------------------------------------------------------------
  public static final String IAM_GRANT_TYPE_KEY = "grant_type";
  public static final String IAM_GRANT_TYPE_VALUE = "urn:ibm:params:oauth:grant-type:apikey";
  public static final String IAM_APIKEY_KEY = "apikey";
  public static final String IAM_ACCESS_TOKEN = "access_token";
  public static final String HEADER_SERVICE_CRN = "Service-CRN";

  // -------------------------------------------------------------------------
  // Required request headers
  // -------------------------------------------------------------------------
  public static final String HEADER_IBM_API_VERSION = "IBM-API-Version";
  public static final String IBM_API_VERSION = "2024-11-30";
  public static final String API_PATH_JOBS = "/v1/jobs";

  // -------------------------------------------------------------------------
  // URL paths
  // -------------------------------------------------------------------------
  public static final String API_PATH_RESULTS = "/results";
  public static final String UI_PATH_INSTANCES = "/instances/";
  public static final String UI_PATH_JOBS = "/jobs/";
  public static final String FIELD_PROGRAM_ID = "program_id";

  // -------------------------------------------------------------------------
  // Job request fields
  // -------------------------------------------------------------------------
  public static final String FIELD_BACKEND = "backend";
  public static final String FIELD_PARAMS = "params";
  public static final String FIELD_ID = "id";

  // -------------------------------------------------------------------------
  // Job response fields
  // -------------------------------------------------------------------------
  public static final String FIELD_STATUS = "status";
  public static final String STATUS_QUEUED = "QUEUED";

  // -------------------------------------------------------------------------
  // Job statuses
  // -------------------------------------------------------------------------
  public static final String STATUS_COMPLETED = "COMPLETED";
  public static final String STATUS_FAILED = "FAILED";
  public static final String STATUS_CANCELLED = "CANCELLED";
  public static final String STATUS_ERROR = "ERROR";
  public static final String FIELD_PUBS = "pubs";

  // -------------------------------------------------------------------------
  // Params fields
  // -------------------------------------------------------------------------
  public static final String PROGRAM_SAMPLER = "sampler";

  // -------------------------------------------------------------------------
  // Program IDs
  // -------------------------------------------------------------------------
  public static final String PROGRAM_ESTIMATOR = "estimator";

  private IBMQConstants() {
  }
}
