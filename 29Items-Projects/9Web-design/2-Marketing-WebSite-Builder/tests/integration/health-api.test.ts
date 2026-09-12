import { describe, expect, it } from "vitest";
import { GET } from "@/app/api/health/route";

describe("health API", () => {
  it("returns service status", async () => {
    const response = GET();
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload).toEqual({
      ok: true,
      data: {
        status: "ok",
        service: "marketing-website-builder"
      }
    });
  });
});
