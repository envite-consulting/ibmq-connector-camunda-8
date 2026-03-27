package de.envite.connector.ibmq.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;

public final class HttpHelper {

    private HttpHelper() {}

    /**
     * Extracts the response body, throwing an exception if the status is not 2xx or the body is absent.
     *
     * @param response the HTTP response to validate
     * @param context  short description of the operation, used in the error message
     * @return the non-null response body
     * @throws RuntimeException if the response is not successful or has no body
     */
    public static JsonNode requireBody(ResponseEntity<JsonNode> response, String context) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Unexpected response during %s: %s".formatted(
                    context, response.getStatusCode()));
        }
        return response.getBody();
    }
}
