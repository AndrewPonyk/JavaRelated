using Microsoft.ML.Data;

namespace TeamChat.Infrastructure.ML;

/// <summary>
/// Output prediction from sentiment analysis model.
/// </summary>
public class SentimentPrediction
{
    [ColumnName("PredictedLabel")]
    public bool IsPositive { get; set; }

    [ColumnName("Score")]
    public float Score { get; set; }

    [ColumnName("Probability")]
    public float Probability { get; set; }
}
