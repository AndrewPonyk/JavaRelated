import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { DatasetList } from "../types/dataset";
import { DatasetTable } from "./DatasetTable";

const LISTING: DatasetList = {
  items: [
    {
      id: "d-1",
      name: "sales.orders",
      layer: "silver",
      description: "orders",
      owner_email: "eng@example.com",
      s3_path: "s3://bucket/sales/orders",
      created_at: "2026-07-01T00:00:00Z",
      updated_at: "2026-07-02T00:00:00Z",
    },
  ],
  total: 1,
  limit: 50,
  offset: 0,
};

describe("DatasetTable", () => {
  it("shows a loading state, then the catalog rows", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(LISTING), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );

    render(<DatasetTable onSelect={vi.fn()} />);
    expect(screen.getByText(/loading catalog/i)).toBeTruthy();

    expect(await screen.findByText("sales.orders")).toBeTruthy();
    expect(screen.getByText("1 dataset")).toBeTruthy();
    // "silver" appears as both the filter chip and the row badge
    expect(screen.getAllByText("silver").length).toBeGreaterThanOrEqual(2);
  });

  it("shows an error banner with retry when the API is down", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("failed to fetch")));

    render(<DatasetTable onSelect={vi.fn()} />);
    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toContain("Could not load datasets");
    expect(screen.getByRole("button", { name: "Retry" })).toBeTruthy();
  });

  it("notifies the parent when a dataset is selected", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(JSON.stringify(LISTING), { status: 200 })),
    );
    const onSelect = vi.fn();

    render(<DatasetTable onSelect={onSelect} />);
    (await screen.findByText("sales.orders")).click();

    await waitFor(() => expect(onSelect).toHaveBeenCalledWith(LISTING.items[0]));
  });
});
