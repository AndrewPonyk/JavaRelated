using FluentAssertions;
using TeamChat.Core.Entities;
using TeamChat.Core.Enums;

namespace TeamChat.Tests.Unit;

public class MessageRepositoryTests
{
    [Fact]
    public void Message_ShouldInitializeWithDefaults()
    {
        // Arrange & Act
        var message = new Message();

        // Assert
        message.Id.Should().BeEmpty();
        message.ChannelId.Should().BeEmpty();
        message.ThreadId.Should().BeNull();
        message.SenderId.Should().BeEmpty();
        message.Content.Should().BeEmpty();
        message.Type.Should().Be(MessageType.Text);
        message.Reactions.Should().BeEmpty();
        message.IsEdited.Should().BeFalse();
        message.IsDeleted.Should().BeFalse();
        message.CreatedAt.Should().BeCloseTo(DateTime.UtcNow, TimeSpan.FromSeconds(1));
    }

    [Fact]
    public void Message_ShouldAllowSettingProperties()
    {
        // Arrange
        var message = new Message
        {
            Id = "msg-123",
            ChannelId = "channel-456",
            ThreadId = "thread-789",
            SenderId = "user-001",
            SenderName = "Test User",
            Content = "Hello, World!",
            Type = MessageType.Text,
            Sentiment = Sentiment.Positive,
            SentimentScore = 0.85f
        };

        // Assert
        message.Id.Should().Be("msg-123");
        message.ChannelId.Should().Be("channel-456");
        message.ThreadId.Should().Be("thread-789");
        message.SenderId.Should().Be("user-001");
        message.SenderName.Should().Be("Test User");
        message.Content.Should().Be("Hello, World!");
        message.Sentiment.Should().Be(Sentiment.Positive);
        message.SentimentScore.Should().Be(0.85f);
    }

    [Fact]
    public void Reaction_ShouldCalculateCount()
    {
        // Arrange
        var reaction = new Reaction
        {
            Type = ReactionType.ThumbsUp,
            UserIds = new List<string> { "user1", "user2", "user3" }
        };

        // Assert
        reaction.Count.Should().Be(3);
    }

    [Fact]
    public void FileAttachment_ShouldInitializeWithDefaults()
    {
        // Arrange & Act
        var attachment = new FileAttachment();

        // Assert
        attachment.Id.Should().BeEmpty();
        attachment.FileName.Should().BeEmpty();
        attachment.ContentType.Should().BeEmpty();
        attachment.SizeBytes.Should().Be(0);
        attachment.Url.Should().BeEmpty();
        attachment.ThumbnailUrl.Should().BeNull();
        attachment.UploadedAt.Should().BeCloseTo(DateTime.UtcNow, TimeSpan.FromSeconds(1));
    }
}
