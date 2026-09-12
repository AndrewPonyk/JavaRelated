import { describe, expect, it } from "vitest";
import { parseShortUrlResponse, validateLongUrl } from "./validation";

describe("validateLongUrl", () => {
  it("accepts HTTP and HTTPS URLs", () => {
    expect(validateLongUrl("https://example.com/path?q=1")).toBeNull();
    expect(validateLongUrl("http://localhost:8080/test")).toBeNull();
  });

  it.each(["", "not-a-url", " file:///tmp/a", "ftp://example.com/file"])(
    "rejects invalid destination %s",
    (value) => expect(validateLongUrl(value)).not.toBeNull(),
  );

  it("rejects embedded credentials and overlong URLs", () => {
    expect(validateLongUrl("https://user:secret@example.com")).not.toBeNull();
    expect(validateLongUrl(`https://example.com/${"a".repeat(2048)}`)).not.toBeNull();
  });

  it("rejects control characters even when URL parsing would normalize them", () => {
    expect(validateLongUrl("https://example.com/\npath")).not.toBeNull();
  });
});

describe("parseShortUrlResponse", () => {
  const response = {
    short_code: "example",
    short_url: "https://short.example/example",
    long_url: "https://example.com/docs",
    visit_count: 0,
  };

  it("accepts a valid API response", () => {
    expect(parseShortUrlResponse(response)).toEqual(response);
  });

  it.each([
    null,
    { ...response, short_code: "bad/code" },
    { ...response, short_url: "javascript:alert(1)" },
    { ...response, visit_count: -1 },
  ])("rejects malformed API data", (value) => {
    expect(() => parseShortUrlResponse(value)).toThrow("invalid data");
  });
});
