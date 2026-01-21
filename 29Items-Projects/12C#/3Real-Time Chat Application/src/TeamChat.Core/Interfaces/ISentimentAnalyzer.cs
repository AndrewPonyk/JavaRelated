using TeamChat.Core.DTOs;

namespace TeamChat.Core.Interfaces;

public interface ISentimentAnalyzer
{
    /// <summary>
    /// Analyzes the sentiment of the given text.
    /// </summary>
    /// <param name="text">The text to analyze.</param>
    /// <returns>Sentiment analysis result with score and label.</returns>
    SentimentResultDto Analyze(string text);

    /// <summary>
    /// Analyzes the sentiment of the given text asynchronously.
    /// </summary>
    Task<SentimentResultDto> AnalyzeAsync(string text, CancellationToken cancellationToken = default);
}
