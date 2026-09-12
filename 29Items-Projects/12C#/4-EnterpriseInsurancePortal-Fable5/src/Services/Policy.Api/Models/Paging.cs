namespace Policy.Api.Models;

public record PagedResult<T>(IReadOnlyList<T> Items, int TotalCount);

/// <summary>Normalized list-endpoint paging. Out-of-range input is clamped, never rejected.</summary>
public readonly record struct Paging(int Page, int PageSize)
{
    public const int DefaultPageSize = 50;
    public const int MaxPageSize = 200;

    public static Paging Normalize(int page, int pageSize) => new(
        Math.Max(page, 1),
        Math.Clamp(pageSize, 1, MaxPageSize));

    public int Skip => (Page - 1) * PageSize;
}
