namespace TeamChat.Infrastructure.Storage;

public class FileStorageSettings
{
    public const string SectionName = "FileStorage";

    public string Provider { get; set; } = "Local"; // "Local" or "AzureBlob"
    public string LocalPath { get; set; } = "./uploads";
    public string BaseUrl { get; set; } = "/files";
    public int MaxFileSizeMB { get; set; } = 10;
    public List<string> AllowedExtensions { get; set; } = new()
    {
        ".jpg", ".jpeg", ".png", ".gif", ".webp",
        ".pdf", ".doc", ".docx", ".xls", ".xlsx",
        ".txt", ".md", ".zip"
    };
    public List<string> AllowedContentTypes { get; set; } = new()
    {
        "image/jpeg", "image/png", "image/gif", "image/webp",
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "text/plain", "text/markdown",
        "application/zip"
    };
}
