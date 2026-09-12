import "@testing-library/jest-dom/vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { UrlShortener } from "./UrlShortener";

describe("UrlShortener", () => {
  it("creates a short URL and renders the result", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ items: [], limit: 20 }))
      .mockResolvedValueOnce(jsonResponse({
        id: "1",
        shortCode: "docs",
        shortUrl: "http://localhost:8080/docs",
        originalUrl: "https://example.com/docs",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      }, 201))
      .mockResolvedValueOnce(jsonResponse({ items: [], limit: 20 }));
    vi.spyOn(globalThis, "fetch").mockImplementation(fetchMock);

    render(<UrlShortener />);

    await userEvent.type(screen.getByLabelText(/Destination URL/i), "https://example.com/docs");
    await userEvent.click(screen.getByRole("button", { name: /Shorten/i }));

    await waitFor(() => expect(screen.getByText("http://localhost:8080/docs")).toBeInTheDocument());
    expect(fetchMock).toHaveBeenCalledWith("http://localhost:8080/api/v1/urls", expect.objectContaining({ method: "POST" }));
  });
});

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" }
  });
}
