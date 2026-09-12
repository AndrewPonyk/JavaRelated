import { afterEach, describe, expect, it, vi } from "vitest";

import { api } from "./client";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("api client", () => {
  it("loads vehicles", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => [{ id: "vehicle-1", name: "Van", license_plate: "AA1", status: "idle" }]
      })
    );

    const vehicles = await api.listVehicles();

    expect(vehicles).toHaveLength(1);
    expect(vehicles[0].name).toBe("Van");
  });

  it("surfaces backend error messages", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 409,
        json: async () => ({ error: { message: "Vehicle license plate already exists" } })
      })
    );

    await expect(api.listVehicles()).rejects.toThrow("Vehicle license plate already exists");
  });
});
