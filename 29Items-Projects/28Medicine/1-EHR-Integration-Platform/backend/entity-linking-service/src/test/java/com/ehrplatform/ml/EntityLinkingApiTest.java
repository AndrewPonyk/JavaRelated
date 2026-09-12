package com.ehrplatform.ml;

import static org.assertj.core.api.Assertions.assertThat;

import com.ehrplatform.ml.controller.StratificationDtos.LinkRequest;
import com.ehrplatform.ml.controller.StratificationDtos.LinkResponse;
import com.ehrplatform.ml.controller.StratificationDtos.StratificationRequest;
import com.ehrplatform.ml.controller.StratificationDtos.StratificationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** End-to-end HTTP test for the entity-linking service. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EntityLinkingApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void stratificationEndpointReturnsTier() {
        StratificationRequest req = new StratificationRequest(
                "p1", "acute myocardial infarction chest pain elevated troponin", 3);

        ResponseEntity<StratificationResponse> resp =
                rest.postForEntity("/api/v1/stratification", req, StratificationResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().riskTier()).isEqualTo("HIGH");
    }

    @Test
    void linkEndpointReturnsConfidence() {
        LinkRequest req = new LinkRequest(
                "a", "chest pain troponin elevated",
                "b", "elevated troponin chest pain");

        ResponseEntity<LinkResponse> resp =
                rest.postForEntity("/api/v1/link", req, LinkResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().status()).isEqualTo("CANDIDATE");
    }

    @Test
    void stratificationValidationRejectsBadInput() {
        // topK = 0 violates @Min(1)
        StratificationRequest bad = new StratificationRequest("p1", "note", 0);
        ResponseEntity<String> resp =
                rest.postForEntity("/api/v1/stratification", bad, String.class);
        assertThat(resp.getStatusCode().is4xxClientError()).isTrue();
    }
}
