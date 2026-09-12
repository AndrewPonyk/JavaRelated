package com.example.inventory.forecast;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ForecastClient {

    private final RestClient restClient;

    public ForecastClient(RestClient.Builder builder,
                          @Value("${app.forecast.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public Prediction predict(String sku, List<Double> demandHistory, int horizonDays) {
        Prediction prediction = restClient.post()
                .uri("/v1/forecasts")
                .body(new PredictionRequest(sku, demandHistory, horizonDays, "daily-demand-v1"))
                .retrieve()
                .body(Prediction.class);
        if (prediction == null || prediction.predictions() == null || prediction.predictions().isEmpty()) {
            throw new IllegalStateException("Forecast service returned an empty prediction.");
        }
        return prediction;
    }

    record PredictionRequest(
            String sku,
            @JsonProperty("demand_history") List<Double> demandHistory,
            @JsonProperty("horizon_days") int horizonDays,
            @JsonProperty("feature_version") String featureVersion) {
    }

    public record Prediction(
            String sku,
            List<Double> predictions,
            @JsonProperty("lower_bound") List<Double> lowerBound,
            @JsonProperty("upper_bound") List<Double> upperBound,
            @JsonProperty("model_version") String modelVersion,
            @JsonProperty("feature_version") String featureVersion,
            @JsonProperty("model_mae") Double modelMae,
            @JsonProperty("generated_at") Instant generatedAt) {
    }
}

