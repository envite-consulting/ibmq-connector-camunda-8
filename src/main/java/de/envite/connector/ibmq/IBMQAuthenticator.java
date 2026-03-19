package de.envite.connector.ibmq;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import static de.envite.connector.ibmq.HttpHelper.requireBody;
import static de.envite.connector.ibmq.IBMQConstants.*;

/**
 * Handles IBM Cloud IAM authentication.
 *
 * <p>Exchanges an IBM Cloud API key for a short-lived IAM bearer token
 * that authorises subsequent calls to the IBM Quantum Runtime API.</p>
 */
@Slf4j
@Component
public class IBMQAuthenticator {

    private final RestTemplate restTemplate;
    private final String iamUrl;

    public IBMQAuthenticator(
            RestTemplate restTemplate,
            @Value("${ibmq.iam-url:" + IAM_TOKEN_URL + "}") String iamUrl) {
        this.restTemplate = restTemplate;
        this.iamUrl = iamUrl;
    }

    /**
     * Exchanges an IBM Cloud API key for an IAM bearer access token.
     *
     * @param apiKey IBM Cloud API key
     * @return short-lived IAM access token
     */
    public String getAccessToken(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add(IAM_GRANT_TYPE_KEY, IAM_GRANT_TYPE_VALUE);
        body.add(IAM_APIKEY_KEY, apiKey);

        log.debug("[IBMQAuthenticator] Exchanging API key for IAM access token");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                iamUrl,
                new HttpEntity<>(body, headers),
                JsonNode.class
        );

        JsonNode responseBody = requireBody(response, "IAM token exchange");
        return responseBody.get(IAM_ACCESS_TOKEN).asText();
    }
}
