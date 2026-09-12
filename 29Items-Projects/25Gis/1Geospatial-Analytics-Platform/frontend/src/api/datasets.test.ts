import { afterEach, describe, expect, it, vi } from "vitest";

import { createDataset, createPointFeature, fetchDatasets, runClassification } from "./datasets";

afterEach(() => {
  vi.restoreAllMocks();
});

function mockJsonResponse(payload: unknown, ok = true, status = 200) {
  return Promise.resolve({
    ok,
    status,
    json: () => Promise.resolve(payload),
  } as Response);
}

describe("dataset API client", () => {
  it("loads datasets", async () => {
    vi.spyOn(globalThis, "fetch").mockReturnValue(mockJsonResponse([{ id: "1", name: "Parcels" }]));

    const datasets = await fetchDatasets();

    expect(datasets).toHaveLength(1);
    expect(fetch).toHaveBeenCalledWith("http://localhost:8000/api/v1/datasets", expect.any(Object));
  });

  it("creates datasets with metadata", async () => {
    vi.spyOn(globalThis, "fetch").mockReturnValue(mockJsonResponse({ id: "1", name: "Roads" }));

    await createDataset({ name: "Roads", source_type: "vector" });

    const [, options] = vi.mocked(fetch).mock.calls[0];
    expect(options?.method).toBe("POST");
    expect(String(options?.body)).toContain("frontend");
  });

  it("creates point features as GeoJSON", async () => {
    vi.spyOn(globalThis, "fetch").mockReturnValue(mockJsonResponse({ id: "feature-1" }));

    await createPointFeature({
      datasetId: "dataset-1",
      longitude: 30,
      latitude: 50,
      properties: { land_use: "urban" },
    });

    const [, options] = vi.mocked(fetch).mock.calls[0];
    expect(String(options?.body)).toContain('"type":"Point"');
  });

  it("creates and runs classification jobs", async () => {
    vi.spyOn(globalThis, "fetch")
      .mockReturnValueOnce(mockJsonResponse({ id: "job-1", dataset_id: "dataset-1", status: "queued", metrics: {} }))
      .mockReturnValueOnce(mockJsonResponse({ id: "job-1", dataset_id: "dataset-1", status: "succeeded", metrics: {} }));

    const job = await runClassification("dataset-1");

    expect(job.status).toBe("succeeded");
    expect(fetch).toHaveBeenCalledTimes(2);
  });
});
