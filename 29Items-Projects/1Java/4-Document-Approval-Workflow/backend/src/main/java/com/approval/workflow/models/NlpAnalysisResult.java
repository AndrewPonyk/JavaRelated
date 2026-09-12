package com.approval.workflow.models;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates NLP sentiment, categorization, and urgency score from spaCy service.
 */
public class NlpAnalysisResult {
    private double sentimentScore;
    private String polarity;
    private String category;
    private double urgencyScore;
    private String recommendedRole;
    private List<String> extractedEntities;

    public NlpAnalysisResult() {
        this.extractedEntities = new ArrayList<>();
    }

    public JsonObject toJson() {
        return new JsonObject()
                .put("sentimentScore", sentimentScore)
                .put("polarity", polarity)
                .put("category", category)
                .put("urgencyScore", urgencyScore)
                .put("recommendedRole", recommendedRole)
                .put("extractedEntities", new JsonArray(extractedEntities));
    }

    public static NlpAnalysisResult fromJson(JsonObject json) {
        if (json == null) return new NlpAnalysisResult();
        NlpAnalysisResult result = new NlpAnalysisResult();
        result.setSentimentScore(json.getDouble("sentimentScore", json.getDouble("sentiment_score", 0.0)));
        result.setPolarity(json.getString("polarity", "NEUTRAL"));
        result.setCategory(json.getString("category", "GENERAL"));
        result.setUrgencyScore(json.getDouble("urgencyScore", json.getDouble("urgency_score", 0.0)));
        result.setRecommendedRole(json.getString("recommendedRole", json.getString("recommended_role", "TEAM_LEAD")));
        
        JsonArray entitiesArray = json.getJsonArray("extractedEntities", json.getJsonArray("extracted_entities"));
        if (entitiesArray != null) {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < entitiesArray.size(); i++) {
                list.add(entitiesArray.getString(i));
            }
            result.setExtractedEntities(list);
        }
        return result;
    }

    // Getters and Setters
    public double getSentimentScore() { return sentimentScore; }
    public void setSentimentScore(double sentimentScore) { this.sentimentScore = sentimentScore; }

    public String getPolarity() { return polarity; }
    public void setPolarity(String polarity) { this.polarity = polarity; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getUrgencyScore() { return urgencyScore; }
    public void setUrgencyScore(double urgencyScore) { this.urgencyScore = urgencyScore; }

    public String getRecommendedRole() { return recommendedRole; }
    public void setRecommendedRole(String recommendedRole) { this.recommendedRole = recommendedRole; }

    public List<String> getExtractedEntities() { return extractedEntities; }
    public void setExtractedEntities(List<String> extractedEntities) { this.extractedEntities = extractedEntities; }
}
