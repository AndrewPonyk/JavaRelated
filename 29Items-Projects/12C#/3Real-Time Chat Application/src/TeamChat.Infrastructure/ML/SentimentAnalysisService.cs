using Microsoft.Extensions.Logging;
using Microsoft.ML;
using TeamChat.Core.DTOs;
using TeamChat.Core.Interfaces;

namespace TeamChat.Infrastructure.ML;

/// <summary>
/// Service for analyzing sentiment of messages using ML.NET.
/// </summary>
public class SentimentAnalysisService : ISentimentAnalyzer
{
    private readonly ILogger<SentimentAnalysisService> _logger;
    private readonly PredictionEngine<SentimentInput, SentimentPrediction>? _predictionEngine;
    private readonly bool _isModelLoaded;

    public SentimentAnalysisService(ILogger<SentimentAnalysisService> logger)
    {
        _logger = logger;

        try
        {
            var modelPath = Path.Combine(AppContext.BaseDirectory, "ML", "Models", "sentiment_model.zip");

            if (File.Exists(modelPath))
            {
                var mlContext = new MLContext();
                var model = mlContext.Model.Load(modelPath, out _);
                _predictionEngine = mlContext.Model.CreatePredictionEngine<SentimentInput, SentimentPrediction>(model);
                _isModelLoaded = true;
                _logger.LogInformation("Sentiment analysis model loaded successfully from {Path}", modelPath);
            }
            else
            {
                _logger.LogWarning("Sentiment model not found at {Path}. Using fallback analysis.", modelPath);
                _isModelLoaded = false;
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to load sentiment analysis model. Using fallback analysis.");
            _isModelLoaded = false;
        }
    }

    public SentimentResultDto Analyze(string text)
    {
        if (string.IsNullOrWhiteSpace(text))
        {
            return SentimentResultDto.FromScore(0.5f); // Neutral
        }

        if (_isModelLoaded && _predictionEngine != null)
        {
            var prediction = _predictionEngine.Predict(new SentimentInput { Text = text });
            // Convert probability to 0-1 scale where 0.5 is neutral
            var score = prediction.IsPositive ? 0.5f + (prediction.Probability * 0.5f) : 0.5f - ((1 - prediction.Probability) * 0.5f);
            return SentimentResultDto.FromScore(score);
        }

        // Fallback: Simple keyword-based sentiment analysis
        return AnalyzeFallback(text);
    }

    public Task<SentimentResultDto> AnalyzeAsync(string text, CancellationToken cancellationToken = default)
    {
        return Task.FromResult(Analyze(text));
    }

    /// <summary>
    /// Simple fallback sentiment analysis when ML model is not available.
    /// </summary>
    private SentimentResultDto AnalyzeFallback(string text)
    {
        var lowerText = text.ToLowerInvariant();

        var positiveWords = new[] { "good", "great", "awesome", "excellent", "happy", "love", "thanks", "thank", "wonderful", "fantastic", "amazing", "nice", "cool", "perfect" };
        var negativeWords = new[] { "bad", "terrible", "awful", "hate", "angry", "sad", "wrong", "fail", "failed", "problem", "issue", "bug", "error", "broken" };

        var positiveCount = positiveWords.Count(word => lowerText.Contains(word));
        var negativeCount = negativeWords.Count(word => lowerText.Contains(word));

        float score;
        if (positiveCount == 0 && negativeCount == 0)
        {
            score = 0.5f; // Neutral
        }
        else
        {
            var total = positiveCount + negativeCount;
            score = (float)positiveCount / total;
        }

        return SentimentResultDto.FromScore(score);
    }
}
