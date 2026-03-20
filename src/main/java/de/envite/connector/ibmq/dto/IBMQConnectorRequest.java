package de.envite.connector.ibmq.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.envite.connector.ibmq.CircuitInputMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Input parameters for the IBMQ connector element template.
 *
 * <p>Carries all configuration needed to authenticate with IBM Cloud, target a backend,
 * and describe the quantum job to run — either as an OpenQASM circuit string or as
 * a raw Qiskit Runtime params JSON document.</p>
 */
@Data
public class IBMQConnectorRequest {

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

    /** Target quantum backend, e.g. <code>ibm_brisbane</code> or <code>ibmq_qasm_simulator</code>. */
    @NotEmpty
    private String backend;

    /**
     * Qiskit Runtime program to execute.
     * Supported values: <code>sampler</code>, <code>estimator</code>.
     */
    @NotEmpty
    private String programId;

    /**
     * Determines how the quantum circuit is provided.
     * <ul>
     *   <li>{@link CircuitInputMode#OPEN_QASM} – provide an OpenQASM 2/3 string via {@code circuit}.</li>
     *   <li>{@link CircuitInputMode#DIRECT_PARAMS} – provide the full Qiskit Runtime params JSON via {@code params}.</li>
     * </ul>
     */
    @NotNull
    @JsonProperty("CircuitInputMode")
    private CircuitInputMode circuitInputMode = CircuitInputMode.OPEN_QASM;

    /**
     * OpenQASM 2 or 3 circuit string.
     * Required when {@link #circuitInputMode} is {@link CircuitInputMode#OPEN_QASM}.
     * Only supported for the <em>sampler</em> primitive.
     */
    private String circuit;

    /**
     * OpenQASM version of the supplied {@link #circuit}.
     * <ul>
     *   <li>{@code 2} – OpenQASM 2.0</li>
     *   <li>{@code 3} – OpenQASM 3.0 (preferred for modern IBM Quantum backends)</li>
     * </ul>
     */
    private Integer qasmVersion = 3;

    /**
     * Number of shots (circuit repetitions) when using {@link CircuitInputMode#OPEN_QASM}.
     * Ignored in {@link CircuitInputMode#DIRECT_PARAMS} mode.
     */
    @Min(1)
    private Integer shots = 1024;

    /**
     * Full Qiskit Runtime job params as a JSON string.
     * Required when {@link #circuitInputMode} is {@link CircuitInputMode#DIRECT_PARAMS}.
     * Example for the sampler primitive:
     * <pre>{"pubs": [["&lt;circuit&gt;", null, 1024]]}</pre>
     */
    private String params;

    /** When <code>true</code> the connector polls until the job reaches a terminal state. */
    private Boolean waitForResult = true;

    /** Maximum time in seconds to wait for a result before failing. */
    @Min(1)
    private Integer timeoutSeconds = 300;

    /** Polling interval in seconds when <code>waitForResult</code> is <code>true</code>. */
    @Min(1)
    private Integer pollIntervalSeconds = 5;
}
