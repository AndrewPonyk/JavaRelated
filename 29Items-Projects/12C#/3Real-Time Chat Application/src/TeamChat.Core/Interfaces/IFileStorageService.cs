using TeamChat.Core.DTOs;

namespace TeamChat.Core.Interfaces;

public interface IFileStorageService
{
    /// <summary>
    /// Uploads a file and returns the result with URL.
    /// </summary>
    Task<UploadFileResultDto> UploadAsync(
        Stream fileStream,
        string fileName,
        string contentType,
        CancellationToken cancellationToken = default);

    /// <summary>
    /// Downloads a file by its ID.
    /// </summary>
    Task<Stream?> DownloadAsync(string fileId, CancellationToken cancellationToken = default);

    /// <summary>
    /// Deletes a file by its ID.
    /// </summary>
    Task DeleteAsync(string fileId, CancellationToken cancellationToken = default);

    /// <summary>
    /// Gets the file URL for a given file ID.
    /// </summary>
    string GetFileUrl(string fileId);

    /// <summary>
    /// Validates if the file type is allowed.
    /// </summary>
    bool IsAllowedFileType(string contentType, string fileName);

    /// <summary>
    /// Validates if the file size is within limits.
    /// </summary>
    bool IsAllowedFileSize(long sizeBytes);
}
