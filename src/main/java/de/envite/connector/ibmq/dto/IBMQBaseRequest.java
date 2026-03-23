package de.envite.connector.ibmq.dto;

import de.envite.connector.ibmq.OperationMode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Fields common to all IBMQ connector operations.
 */
@Data
public class IBMQBaseRequest {

    /**
     * Determines which operation to perform.
     * Defaults to {@link OperationMode#SUBMIT_JOB} for backwards compatibility.
     */
    @NotNull
    private OperationMode operationMode = OperationMode.SUBMIT_JOB;

    /** IBM Cloud API key. Reference a Camunda secret via <code>{{secrets.IBMQ_API_KEY}}</code>. */
    @NotEmpty
    private String apiKey;

    /**
     * IBM Quantum service base URL.
     * Defaults to the IBM Quantum Platform endpoint.
     */
    private String ibmqUrl = "https://quantum.cloud.ibm.com/api";

    /**
     * IBM Quantum instance Cloud Resource Name (CRN).
     * Find it in IBM Cloud → Resource list → your Quantum Computing instance → Details.
     */
    @NotEmpty
    private String ibmqInstance;
}
