using Microsoft.ML.Data;

namespace TeamChat.Infrastructure.ML;

/// <summary>
/// Input data for sentiment analysis model.
/// </summary>
public class SentimentInput
{
    [LoadColumn(0)]
    public string? Text { get; set; }
}
