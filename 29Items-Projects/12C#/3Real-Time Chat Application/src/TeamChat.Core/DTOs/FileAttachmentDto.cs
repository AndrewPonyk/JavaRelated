namespace TeamChat.Core.DTOs;

public record FileAttachmentDto(
    string Id,
    string FileName,
    string ContentType,
    long SizeBytes,
    string Url,
    string? ThumbnailUrl
);

public record UploadFileResultDto(
    string Id,
    string FileName,
    string Url
);
