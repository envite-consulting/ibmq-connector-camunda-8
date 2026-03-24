package de.envite.connector.ibmq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.envite.connector.ibmq.dto.IBMQSubmitJobRequestDto;
import de.envite.connector.ibmq.model.CircuitInputMode;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import static de.envite.connector.ibmq.IBMQConstants.*;

/**
 * Builds the Qiskit Runtime job params payload from a connector request.
 *
 * <p>Supports two input modes:
 * <ul>
 *   <li>{@link CircuitInputMode#OPEN_QASM} – constructs a Sampler V2 PUB from an OpenQASM circuit string.</li>
 *   <li>{@link CircuitInputMode#DIRECT_PARAMS} – parses a caller-supplied Qiskit Runtime params JSON document.</li>
 * </ul>
 * </p>
 */
@AllArgsConstructor
@Component
public class IBMQParameterHandler {

    private final ObjectMapper objectMapper;

    /**
     * Builds the job params {@link JsonNode} for the given request.
     *
     * @param request the submit job request
     * @return params payload to be included in the job submission body
     */
    public JsonNode buildParams(IBMQSubmitJobRequestDto request) {
        return switch (request.getCircuitInputMode()) {
            case OPEN_QASM     -> buildOpenQasmParams(request);
            case DIRECT_PARAMS -> parseDirectParams(request);
        };
    }

    /**
     * Builds Qiskit Runtime Sampler V2 params from an OpenQASM 2 or 3 circuit string.
     * Resulting structure: {@code {"pubs": [["<circuit>", null, <shots>]]}}
     *
     * <p>The IBM Quantum backend detects the QASM version automatically. However,
     * OpenQASM 3 is preferred for modern backends. OpenQASM 2 circuits are accepted
     * by most simulators and real backends via automatic up-conversion.</p>
     */
    private JsonNode buildOpenQasmParams(IBMQSubmitJobRequestDto request) {
        if (request.getCircuit() == null || request.getCircuit().isBlank()) {
            throw new IllegalArgumentException("'circuit' must not be empty when circuitInputMode is OPEN_QASM");
        }

        ObjectNode params = objectMapper.createObjectNode();
        params.put("version", 2);
        ArrayNode pubs = params.putArray(FIELD_PUBS);

        // A PUB (Primitive Unified Bloc) for Sampler V2: [circuit, parameter_values, shots]
        ArrayNode pub = pubs.addArray();
        pub.add(request.getCircuit());
        pub.addNull();                  // no bound parameter values
        pub.add(request.getShots());

        return params;
    }

    private JsonNode parseDirectParams(IBMQSubmitJobRequestDto request) {
        if (request.getParams() == null || request.getParams().isBlank()) {
            throw new IllegalArgumentException("'params' must not be empty when circuitInputMode is DIRECT_PARAMS");
        }
        try {
            return objectMapper.readTree(request.getParams());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse 'params' as JSON: " + e.getMessage(), e);
        }
    }
}
