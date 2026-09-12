package service

import (
	"context"
	"strings"
)

type Suggestion struct {
	Title      string  `json:"title"`
	Message    string  `json:"message"`
	Confidence float64 `json:"confidence"`
	Pattern    string  `json:"pattern"`
}

type SuggestionService struct {
	rules []suggestionRule
}

type suggestionRule struct {
	pattern    string
	title      string
	message    string
	confidence float64
}

func NewSuggestionService() *SuggestionService {
	return &SuggestionService{
		rules: []suggestionRule{
			{
				pattern:    "connection refused",
				title:      "Check dependent service availability",
				message:    "A dependency appears unreachable. Verify container health, port mappings, and service startup order.",
				confidence: 0.78,
			},
			{
				pattern:    "no such table",
				title:      "Run database migrations",
				message:    "The local schema is missing an expected table. Run migrations before retrying the command or test.",
				confidence: 0.82,
			},
			{
				pattern:    "access denied",
				title:      "Validate cloud or Docker permissions",
				message:    "The operation reached the target service but was denied. Check IAM policy, Docker socket access, or local credentials.",
				confidence: 0.72,
			},
		},
	}
}

func (s *SuggestionService) Suggest(ctx context.Context, logText string) []Suggestion {
	_ = ctx // TODO: Use context when remote LLM enrichment is introduced.

	normalized := strings.ToLower(logText)
	suggestions := make([]Suggestion, 0, len(s.rules))
	for _, rule := range s.rules {
		if strings.Contains(normalized, rule.pattern) {
			suggestions = append(suggestions, Suggestion{
				Title:      rule.title,
				Message:    rule.message,
				Confidence: rule.confidence,
				Pattern:    rule.pattern,
			})
		}
	}

	if len(suggestions) == 0 {
		suggestions = append(suggestions, Suggestion{
			Title:      "Collect more diagnostic context",
			Message:    "No known pattern matched. Capture command output, dependency versions, and environment details before escalating.",
			Confidence: 0.3,
			Pattern:    "fallback",
		})
	}

	return suggestions
}
