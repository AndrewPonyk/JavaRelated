using Microsoft.AspNetCore.Mvc;
using TeamChat.Core.DTOs;
using TeamChat.Core.Interfaces;

namespace TeamChat.API.Controllers;

[ApiController]
[Route("api/[controller]")]
public class FilesController : ControllerBase
{
    private readonly IFileStorageService _fileStorageService;
    private readonly ILogger<FilesController> _logger;

    public FilesController(
        IFileStorageService fileStorageService,
        ILogger<FilesController> logger)
    {
        _fileStorageService = fileStorageService;
        _logger = logger;
    }

    /// <summary>
    /// Upload a file.
    /// </summary>
    [HttpPost("upload")]
    [RequestSizeLimit(10 * 1024 * 1024)] // 10 MB limit
    public async Task<ActionResult<UploadFileResultDto>> Upload(IFormFile file)
    {
        if (file == null || file.Length == 0)
        {
            return BadRequest("No file provided");
        }

        if (!_fileStorageService.IsAllowedFileType(file.ContentType, file.FileName))
        {
            return BadRequest("File type not allowed");
        }

        if (!_fileStorageService.IsAllowedFileSize(file.Length))
        {
            return BadRequest("File size exceeds limit");
        }

        await using var stream = file.OpenReadStream();
        var result = await _fileStorageService.UploadAsync(stream, file.FileName, file.ContentType);

        _logger.LogInformation("File {FileName} uploaded as {FileId}", file.FileName, result.Id);

        return Ok(result);
    }

    /// <summary>
    /// Download a file.
    /// </summary>
    [HttpGet("{id}")]
    public async Task<ActionResult> Download(string id)
    {
        var stream = await _fileStorageService.DownloadAsync(id);

        if (stream == null)
        {
            return NotFound();
        }

        // Determine content type from file extension
        var contentType = "application/octet-stream";

        return File(stream, contentType);
    }

    /// <summary>
    /// Delete a file.
    /// </summary>
    [HttpDelete("{id}")]
    public async Task<ActionResult> Delete(string id)
    {
        await _fileStorageService.DeleteAsync(id);

        _logger.LogInformation("File {FileId} deleted", id);

        return NoContent();
    }
}
