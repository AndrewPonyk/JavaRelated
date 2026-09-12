import { FormEvent, useEffect, useMemo, useState } from "react";
import DeckGL from "@deck.gl/react";
import { ScatterplotLayer } from "@deck.gl/layers";
import { MapContainer, TileLayer } from "react-leaflet";

import {
  ClassificationJob,
  Dataset,
  DatasetFeature,
  Layer,
  createDataset,
  createLayer,
  createPointFeature,
  fetchClassificationJobs,
  fetchDatasetSummary,
  fetchDatasets,
  fetchFeatures,
  fetchLayers,
  runClassification,
  searchNearby,
} from "../api/datasets";

const initialViewState = {
  longitude: 30.5234,
  latitude: 50.4501,
  zoom: 9,
  pitch: 0,
  bearing: 0,
};

type Summary = {
  dataset_id: string;
  feature_count: number;
  extent: Record<string, unknown> | null;
  classes: Record<string, number>;
};

function pointCoordinates(feature: DatasetFeature): [number, number] | null {
  if (feature.geometry.type !== "Point" || !Array.isArray(feature.geometry.coordinates)) {
    return null;
  }
  const [longitude, latitude] = feature.geometry.coordinates;
  if (typeof longitude !== "number" || typeof latitude !== "number") {
    return null;
  }
  return [longitude, latitude];
}

export function DatasetMapPanel() {
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [selectedDatasetId, setSelectedDatasetId] = useState("");
  const [features, setFeatures] = useState<DatasetFeature[]>([]);
  const [layers, setLayers] = useState<Layer[]>([]);
  const [jobs, setJobs] = useState<ClassificationJob[]>([]);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [nearbyCount, setNearbyCount] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function loadDatasets(signal?: AbortSignal) {
    const loaded = await fetchDatasets(signal);
    setDatasets(loaded);
    if (!selectedDatasetId && loaded[0]) {
      setSelectedDatasetId(loaded[0].id);
    }
  }

  async function loadDatasetDetails(datasetId: string, signal?: AbortSignal) {
    const [loadedFeatures, loadedLayers, loadedJobs, loadedSummary] = await Promise.all([
      fetchFeatures(datasetId, signal),
      fetchLayers(datasetId, signal),
      fetchClassificationJobs(datasetId, signal),
      fetchDatasetSummary(datasetId, signal),
    ]);
    setFeatures(loadedFeatures);
    setLayers(loadedLayers);
    setJobs(loadedJobs);
    setSummary(loadedSummary);
  }

  useEffect(() => {
    const controller = new AbortController();
    setIsLoading(true);
    loadDatasets(controller.signal)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : "Failed to load datasets"))
      .finally(() => setIsLoading(false));
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!selectedDatasetId) {
      setFeatures([]);
      setLayers([]);
      setJobs([]);
      setSummary(null);
      return;
    }
    const controller = new AbortController();
    loadDatasetDetails(selectedDatasetId, controller.signal).catch((err: unknown) =>
      setError(err instanceof Error ? err.message : "Failed to load dataset details"),
    );
    return () => controller.abort();
  }, [selectedDatasetId]);

  const deckLayers = useMemo(
    () => [
      new ScatterplotLayer({
        id: "dataset-points",
        data: features
          .map((feature) => ({ feature, position: pointCoordinates(feature) }))
          .filter((item): item is { feature: DatasetFeature; position: [number, number] } => item.position !== null),
        getPosition: (d) => d.position,
        getRadius: 550,
        getFillColor: [47, 111, 115, 190],
        pickable: true,
      }),
    ],
    [features],
  );

  async function refreshSelectedDataset() {
    await loadDatasets();
    if (selectedDatasetId) {
      await loadDatasetDetails(selectedDatasetId);
    }
  }

  async function handleCreateDataset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setIsSaving(true);
    const form = new FormData(event.currentTarget);
    try {
      const dataset = await createDataset({
        name: String(form.get("name") ?? "").trim(),
        description: String(form.get("description") ?? "").trim(),
        source_type: String(form.get("source_type")) as Dataset["source_type"],
      });
      event.currentTarget.reset();
      setSelectedDatasetId(dataset.id);
      await refreshSelectedDataset();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create dataset");
    } finally {
      setIsSaving(false);
    }
  }

  async function handleCreateFeature(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedDatasetId) {
      return;
    }
    setError(null);
    setIsSaving(true);
    const form = new FormData(event.currentTarget);
    try {
      const longitude = Number(form.get("longitude"));
      const latitude = Number(form.get("latitude"));
      if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) {
        throw new Error("Longitude and latitude must be valid numbers");
      }
      await createPointFeature({
        datasetId: selectedDatasetId,
        longitude,
        latitude,
        properties: {
          name: String(form.get("feature_name") ?? "").trim(),
          land_use: String(form.get("land_use") ?? "unclassified").trim(),
        },
      });
      event.currentTarget.reset();
      await loadDatasetDetails(selectedDatasetId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create feature");
    } finally {
      setIsSaving(false);
    }
  }

  async function handleCreateLayer() {
    if (!selectedDatasetId) {
      return;
    }
    setError(null);
    await createLayer({
      dataset_id: selectedDatasetId,
      name: "Operational WMS layer",
      layer_type: "wms",
      style: "default",
      is_public: true,
    });
    await loadDatasetDetails(selectedDatasetId);
  }

  async function handleRunClassification() {
    if (!selectedDatasetId) {
      return;
    }
    setError(null);
    setIsSaving(true);
    try {
      await runClassification(selectedDatasetId);
      await loadDatasetDetails(selectedDatasetId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to run classification");
    } finally {
      setIsSaving(false);
    }
  }

  async function handleNearbySearch() {
    if (!selectedDatasetId) {
      return;
    }
    const results = await searchNearby({
      datasetId: selectedDatasetId,
      longitude: 30.5234,
      latitude: 50.4501,
      radiusMeters: 10_000,
    });
    setNearbyCount(results.length);
  }

  return (
    <main className="app-shell">
      <section className="map-surface" aria-label="Geospatial map workspace">
        <MapContainer center={[50.4501, 30.5234]} zoom={9} className="leaflet-map">
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
        </MapContainer>
        <DeckGL
          controller
          initialViewState={initialViewState}
          layers={deckLayers}
          style={{ position: "absolute", top: "0", right: "0", bottom: "0", left: "0" }}
        />
      </section>

      <aside className="dataset-panel" aria-label="Dataset catalog">
        <header>
          <h1>Geospatial Analytics</h1>
          <p>PostGIS datasets, map layers, spatial analysis, and classification jobs.</p>
        </header>

        {isLoading && <p className="status">Loading datasets...</p>}
        {error && <p className="status status-error">{error}</p>}

        <form className="control-grid" onSubmit={handleCreateDataset}>
          <input name="name" minLength={1} maxLength={160} aria-label="Dataset name" required />
          <input name="description" maxLength={2000} aria-label="Dataset description" />
          <select name="source_type" defaultValue="vector">
            <option value="vector">Vector</option>
            <option value="raster">Raster</option>
            <option value="classification">Classification</option>
          </select>
          <button disabled={isSaving}>Create dataset</button>
        </form>

        <label className="field-label">
          Dataset
          <select value={selectedDatasetId} onChange={(event) => setSelectedDatasetId(event.target.value)}>
            <option value="">Select dataset</option>
            {datasets.map((dataset) => (
              <option key={dataset.id} value={dataset.id}>
                {dataset.name}
              </option>
            ))}
          </select>
        </label>

        <form className="control-grid" onSubmit={handleCreateFeature}>
          <input name="feature_name" maxLength={160} aria-label="Feature name" required />
          <input name="land_use" maxLength={80} aria-label="Land use" required />
          <input name="longitude" type="number" step="0.000001" min="-180" max="180" aria-label="Longitude" required />
          <input name="latitude" type="number" step="0.000001" min="-90" max="90" aria-label="Latitude" required />
          <button disabled={!selectedDatasetId || isSaving}>Add point</button>
        </form>

        <div className="action-row">
          <button onClick={handleCreateLayer} disabled={!selectedDatasetId || isSaving}>
            Add WMS layer
          </button>
          <button onClick={handleRunClassification} disabled={!selectedDatasetId || isSaving}>
            Classify
          </button>
          <button onClick={handleNearbySearch} disabled={!selectedDatasetId}>
            Nearby
          </button>
        </div>

        <section className="metrics-grid" aria-label="Dataset summary">
          <span>Features: {summary?.feature_count ?? features.length}</span>
          <span>Layers: {layers.length}</span>
          <span>Jobs: {jobs.length}</span>
          <span>Nearby: {nearbyCount ?? "-"}</span>
        </section>

        <ul className="dataset-list">
          {datasets.length === 0 && !isLoading && <li className="empty-state">No datasets yet.</li>}
          {datasets.map((dataset) => (
            <li key={dataset.id} className={dataset.id === selectedDatasetId ? "selected" : ""}>
              <strong>{dataset.name}</strong>
              <span>{dataset.source_type}</span>
              {dataset.description && <p>{dataset.description}</p>}
            </li>
          ))}
        </ul>
      </aside>
    </main>
  );
}
