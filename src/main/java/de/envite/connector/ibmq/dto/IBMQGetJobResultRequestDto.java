package de.envite.connector.ibmq.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Input parameters for the IBMQ connector when checking the result of a previously submitted job
 * ({@link de.envite.connector.ibmq.OperationMode#GET_JOB_RESULT}).
 *
 * <p>Makes a single status request with no polling. Intended for use inside a BPMN polling loop
 * driven by a timer intermediate event, as an alternative to blocking inside the connector.
 * Authentication and endpoint configuration are inherited from {@link IBMQBaseRequest}.</p>
 */
@Value
@SuperBuilder
@Jacksonized
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IBMQGetJobResultRequestDto extends IBMQBaseRequest {

    /** ID of the previously submitted IBM Quantum job. */
    @NotEmpty
    String jobId;
}
