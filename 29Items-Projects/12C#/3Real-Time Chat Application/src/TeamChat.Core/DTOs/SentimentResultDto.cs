using TeamChat.Core.Enums;

namespace TeamChat.Core.DTOs;

public record SentimentResultDto(
    Sentiment Sentiment,
    float Score,
    string Label
)
{
    public static SentimentResultDto FromScore(float score)
    {
        var sentiment = score switch
        {
            < 0.2f => Sentiment.VeryNegative,
            < 0.4f => Sentiment.Negative,
            < 0.6f => Sentiment.Neutral,
            < 0.8f => Sentiment.Positive,
            _ => Sentiment.VeryPositive
        };

        var label = sentiment switch
        {
            Sentiment.VeryNegative => "Very Negative",
            Sentiment.Negative => "Negative",
            Sentiment.Neutral => "Neutral",
            Sentiment.Positive => "Positive",
            Sentiment.VeryPositive => "Very Positive",
            _ => "Unknown"
        };

        return new SentimentResultDto(sentiment, score, label);
    }
}
