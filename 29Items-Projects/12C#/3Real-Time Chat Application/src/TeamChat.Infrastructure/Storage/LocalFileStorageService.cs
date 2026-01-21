using Microsoft.Extensions.Options;
using TeamChat.Core.DTOs;
using TeamChat.Core.Interfaces;

namespace TeamChat.Infrastructure.Storage;

public class LocalFileStorageService : IFileStorageService
{
    private readonly FileStorageSettings _settings;
    private readonly string _uploadPath;

    public LocalFileStorageService(IOptions<FileStorageSettings> settings)
    {
        _settings = settings.Value;
        _uploadPath = Path.GetFullPath(_settings.LocalPath);

        // Ensure upload directory exists
        if (!Directory.Exists(_uploadPath))
        {
            Directory.CreateDirectory(_uploadPath);
        }
    }

    public async Task<UploadFileResultDto> UploadAsync(
        Stream fileStream,
        string fileName,
        string contentType,
        CancellationToken cancellationToken = default)
    {
        var fileId = Guid.NewGuid().ToString();
        var extension = Path.GetExtension(fileName);
        var storedFileName = $"{fileId}{extension}";
        var filePath = Path.Combine(_uploadPath, storedFileName);

        await using var outputStream = File.Create(filePath);
        await fileStream.CopyToAsync(outputStream, cancellationToken);

        return new UploadFileResultDto(
            Id: fileId,
            FileName: fileName,
            Url: GetFileUrl(fileId + extension)
        );
    }

    public async Task<Stream?> DownloadAsync(string fileId, CancellationToken cancellationToken = default)
    {
        var files = Directory.GetFiles(_uploadPath, $"{fileId}.*");
        if (files.Length == 0)
            return null;

        var filePath = files[0];
        return await Task.FromResult<Stream>(File.OpenRead(filePath));
    }

    public Task DeleteAsync(string fileId, CancellationToken cancellationToken = default)
    {
        var files = Directory.GetFiles(_uploadPath, $"{fileId}.*");
        foreach (var file in files)
        {
            File.Delete(file);
        }
        return Task.CompletedTask;
    }

    public string GetFileUrl(string fileId)
    {
        return $"{_settings.BaseUrl}/{fileId}";
    }

    public bool IsAllowedFileType(string contentType, string fileName)
    {
        var extension = Path.GetExtension(fileName)?.ToLowerInvariant();

        var isExtensionAllowed = _settings.AllowedExtensions
            .Any(e => e.Equals(extension, StringComparison.OrdinalIgnoreCase));

        var isContentTypeAllowed = _settings.AllowedContentTypes
            .Any(c => c.Equals(contentType, StringComparison.OrdinalIgnoreCase));

        return isExtensionAllowed && isContentTypeAllowed;
    }

    public bool IsAllowedFileSize(long sizeBytes)
    {
        var maxSizeBytes = _settings.MaxFileSizeMB * 1024 * 1024;
        return sizeBytes <= maxSizeBytes;
    }
}
