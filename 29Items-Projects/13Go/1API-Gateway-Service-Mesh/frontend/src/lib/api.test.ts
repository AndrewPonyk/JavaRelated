import { describe, expect, it } from "vitest";

describe("api types", () => {
  it("keeps numeric route limits serializable", () => {
    const payload = JSON.stringify({ rateLimitPerMinute: 120 });
    expect(payload).toContain("120");
  });
});
