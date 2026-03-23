package de.envite.connector.ibmq.model;

public enum CircuitInputMode {

    /**
     * The user provides an OpenQASM 2 or 3 circuit string directly.
     * The connector builds the Qiskit Runtime job params automatically.
     * Only supported for the <em>sampler</em> primitive.
     */
    OPEN_QASM,

    /**
     * The user provides the full Qiskit Runtime job <code>params</code> object as a JSON string.
     * Required for the <em>estimator</em> primitive or any advanced use case.
     */
    DIRECT_PARAMS
}
