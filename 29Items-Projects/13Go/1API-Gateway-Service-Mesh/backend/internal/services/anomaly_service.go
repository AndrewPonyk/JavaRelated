package services

import (
	"context"
	"math"

	"github.com/example/api-gateway/backend/internal/models"
)

type TrafficFeatures struct {
	RouteID       string  `json:"routeId"`
	TenantID      string  `json:"tenantId"`
	RequestRate   float64 `json:"requestRate"`
	ErrorRate     float64 `json:"errorRate"`
	P95LatencyMS  float64 `json:"p95LatencyMs"`
	BytesPerSec   float64 `json:"bytesPerSec"`
	DeployVersion string  `json:"deployVersion"`
}

type AnomalyDecision struct {
	Score     float64 `json:"score"`
	IsAnomaly bool    `json:"isAnomaly"`
	Reason    string  `json:"reason"`
}

type AnomalyService struct {
	gateway *GatewayService
}

func NewAnomalyService(gateway *GatewayService) *AnomalyService {
	return &AnomalyService{gateway: gateway}
}

func (s *AnomalyService) ScoreTraffic(ctx context.Context, features TrafficFeatures) (AnomalyDecision, error) {
	score := weightedScore(features)
	decision := AnomalyDecision{
		Score:     score,
		IsAnomaly: score >= 0.80,
		Reason:    classify(features, score),
	}
	if decision.IsAnomaly && s.gateway != nil && features.RouteID != "" && features.TenantID != "" {
		_, err := s.gateway.CreateAnomalyEvent(ctx, models.AnomalyEventInput{
			RouteID:  features.RouteID,
			TenantID: features.TenantID,
			Score:    decision.Score,
			Reason:   decision.Reason,
			Features: map[string]any{
				"requestRate":   features.RequestRate,
				"errorRate":     features.ErrorRate,
				"p95LatencyMs":  features.P95LatencyMS,
				"bytesPerSec":   features.BytesPerSec,
				"deployVersion": features.DeployVersion,
			},
		})
		if err != nil {
			return decision, err
		}
	}
	return decision, nil
}

func weightedScore(features TrafficFeatures) float64 {
	errorComponent := clamp(features.ErrorRate/0.30, 0, 1) * 0.45
	latencyComponent := clamp(features.P95LatencyMS/2500, 0, 1) * 0.30
	rateComponent := clamp(features.RequestRate/5000, 0, 1) * 0.15
	throughputComponent := clamp(features.BytesPerSec/50_000_000, 0, 1) * 0.10
	return math.Round((errorComponent+latencyComponent+rateComponent+throughputComponent)*10000) / 10000
}

func classify(features TrafficFeatures, score float64) string {
	switch {
	case features.ErrorRate >= 0.30:
		return "elevated error rate"
	case features.P95LatencyMS >= 2500:
		return "latency spike"
	case score >= 0.80:
		return "combined traffic anomaly"
	default:
		return "baseline traffic"
	}
}

func clamp(value float64, min float64, max float64) float64 {
	if value < min {
		return min
	}
	if value > max {
		return max
	}
	return value
}
