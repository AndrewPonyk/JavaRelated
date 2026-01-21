namespace TeamChat.Core.Entities;

/// <summary>
/// Represents a file attachment on a message.
/// </summary>
public class FileAttachment
{
    public string Id { get; set; } = string.Empty;
    public string FileName { get; set; } = string.Empty;
    public string ContentType { get; set; } = string.Empty;
    public long SizeBytes { get; set; }
    public string Url { get; set; } = string.Empty;

    /// <summary>
    /// Thumbnail URL for images.
    /// </summary>
    public string? ThumbnailUrl { get; set; }

    public DateTime UploadedAt { get; set; } = DateTime.UtcNow;
}
