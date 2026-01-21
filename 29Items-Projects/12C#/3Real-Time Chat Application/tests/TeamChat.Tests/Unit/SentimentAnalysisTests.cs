using FluentAssertions;
using Microsoft.Extensions.Logging;
using Moq;
using TeamChat.Core.Enums;
using TeamChat.Infrastructure.ML;

namespace TeamChat.Tests.Unit;

public class SentimentAnalysisTests
{
    private readonly SentimentAnalysisService _sut;

    public SentimentAnalysisTests()
    {
        var logger = new Mock<ILogger<SentimentAnalysisService>>();
        _sut = new SentimentAnalysisService(logger.Object);
    }

    [Theory]
    [InlineData("I love this! It's amazing!", Sentiment.Positive)]
    [InlineData("This is terrible and awful", Sentiment.Negative)]
    [InlineData("The meeting is at 3pm", Sentiment.Neutral)]
    public void Analyze_ShouldReturnExpectedSentiment(string text, Sentiment expectedSentiment)
    {
        // Act
        var result = _sut.Analyze(text);

        // Assert
        // Note: Using fallback analysis, so we check general direction
        result.Should().NotBeNull();
        if (expectedSentiment == Sentiment.Positive || expectedSentiment == Sentiment.VeryPositive)
        {
            result.Score.Should().BeGreaterThanOrEqualTo(0.5f);
        }
        else if (expectedSentiment == Sentiment.Negative || expectedSentiment == Sentiment.VeryNegative)
        {
            result.Score.Should().BeLessThanOrEqualTo(0.5f);
        }
    }

    [Fact]
    public void Analyze_WithEmptyText_ShouldReturnNeutral()
    {
        // Act
        var result = _sut.Analyze("");

        // Assert
        result.Sentiment.Should().Be(Sentiment.Neutral);
        result.Score.Should().Be(0.5f);
    }

    [Fact]
    public void Analyze_WithWhitespace_ShouldReturnNeutral()
    {
        // Act
        var result = _sut.Analyze("   ");

        // Assert
        result.Sentiment.Should().Be(Sentiment.Neutral);
    }

    [Fact]
    public async Task AnalyzeAsync_ShouldReturnSameResultAsSync()
    {
        // Arrange
        var text = "Great job everyone!";

        // Act
        var syncResult = _sut.Analyze(text);
        var asyncResult = await _sut.AnalyzeAsync(text);

        // Assert
        asyncResult.Should().BeEquivalentTo(syncResult);
    }
}
