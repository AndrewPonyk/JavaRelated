import { useEffect, useRef, useState } from 'react';

import { filterNames, type FilterName } from '../contracts/editor';
import { useImageProcessor } from '../hooks/useImageProcessor';
import { usePresetLibrary } from '../hooks/usePresetLibrary';
import { exportCanvas, renderImage } from '../services/canvas';
import type { CropRect, ExportSettings } from '../types/image';
import { ModelStatus } from './ModelStatus';

const filterLabels: Record<FilterName, string> = {
  grayscale: 'Grayscale',
  invert: 'Invert',
  sepia: 'Sepia',
  brightness: 'Brightness',
  contrast: 'Contrast',
  saturation: 'Saturation',
  blur: 'Blur',
  sharpen: 'Sharpen',
};

function extension(mimeType: ExportSettings['mimeType']): string {
  if (mimeType === 'image/jpeg') return 'jpg';
  return mimeType === 'image/png' ? 'png' : 'webp';
}

function safeFileStem(fileName: string): string {
  const stem = fileName.replace(/\.[^.]+$/, '').normalize('NFKC');
  return (
    stem
      .replace(/[^\p{L}\p{N}._-]+/gu, '-')
      .replace(/^[.-]+|[.-]+$/g, '')
      .slice(0, 80) || 'image'
  );
}

function defaultCrop(width: number, height: number): CropRect {
  return { x: 0, y: 0, width, height };
}

function boundedCrop(crop: CropRect, width: number, height: number): CropRect {
  const finite = (value: number, fallback: number) => (Number.isFinite(value) ? value : fallback);
  const x = Math.max(0, Math.min(width - 1, Math.round(finite(crop.x, 0))));
  const y = Math.max(0, Math.min(height - 1, Math.round(finite(crop.y, 0))));
  return {
    x,
    y,
    width: Math.max(1, Math.min(width - x, Math.round(finite(crop.width, width - x)))),
    height: Math.max(1, Math.min(height - y, Math.round(finite(crop.height, height - y)))),
  };
}

export function ImageEditor() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const editor = useImageProcessor();
  const library = usePresetLibrary();
  const [filterName, setFilterName] = useState<FilterName>('grayscale');
  const [filterAmount, setFilterAmount] = useState(0);
  const [crop, setCrop] = useState<CropRect>({ x: 0, y: 0, width: 1, height: 1 });
  const [exportSettings, setExportSettings] = useState<ExportSettings>({
    mimeType: 'image/webp',
    quality: 0.85,
    maxWidth: null,
  });
  const [exportError, setExportError] = useState<string | null>(null);
  const [presetName, setPresetName] = useState('');
  const [projectId, setProjectId] = useState<string | null>(null);
  const [projectName, setProjectName] = useState('');
  const [projectRename, setProjectRename] = useState('');
  const [selectedPresetId, setSelectedPresetId] = useState<string | null>(null);
  const [presetUpdateName, setPresetUpdateName] = useState('');
  const [libraryError, setLibraryError] = useState<string | null>(null);
  const busy = ['decoding', 'processing', 'exporting'].includes(editor.status);
  const selectedPreset = library.presets.find((preset) => preset.id === selectedPresetId) ?? null;

  useEffect(() => {
    if (canvasRef.current && editor.imageData) renderImage(canvasRef.current, editor.imageData);
  }, [editor.imageData]);

  useEffect(() => {
    if (editor.sourceImageData)
      setCrop(defaultCrop(editor.sourceImageData.width, editor.sourceImageData.height));
  }, [editor.sourceImageData]);

  useEffect(() => {
    if (filterName === 'blur') setFilterAmount(1);
    else if (filterName === 'sharpen') setFilterAmount(25);
    else if (['grayscale', 'invert', 'sepia'].includes(filterName)) setFilterAmount(0);
    else setFilterAmount(0);
  }, [filterName]);

  useEffect(() => {
    setProjectRename(library.projects.find((project) => project.id === projectId)?.name ?? '');
  }, [library.projects, projectId]);

  useEffect(() => {
    setPresetUpdateName(selectedPreset?.name ?? '');
  }, [selectedPreset]);

  async function handleFile(file: File | undefined) {
    if (!file) return;
    setExportError(null);
    await editor.loadFile(file);
  }

  async function download() {
    const canvas = canvasRef.current;
    if (!canvas || !editor.metadata) return;
    setExportError(null);
    try {
      const blob = await exportCanvas(canvas, exportSettings);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${safeFileStem(editor.metadata.fileName)}-edited.${extension(exportSettings.mimeType)}`;
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 0);
    } catch (error) {
      setExportError(error instanceof Error ? error.message : 'Export failed.');
    }
  }

  function selectAspectRatio(ratio: number | null) {
    const source = editor.sourceImageData;
    if (!source) return;
    if (ratio === null) {
      setCrop(defaultCrop(source.width, source.height));
      return;
    }
    let width = source.width * 0.8;
    let height = width / ratio;
    if (height > source.height * 0.8) {
      height = source.height * 0.8;
      width = height * ratio;
    }
    setCrop(
      boundedCrop(
        { x: (source.width - width) / 2, y: (source.height - height) / 2, width, height },
        source.width,
        source.height,
      ),
    );
  }

  async function savePreset() {
    if (!presetName.trim()) {
      setLibraryError('Give the preset a name before saving it.');
      return;
    }
    setLibraryError(null);
    try {
      await library.savePreset(presetName.trim(), editor.recipe, projectId);
      setPresetName('');
    } catch (error) {
      setLibraryError(error instanceof Error ? error.message : 'Preset could not be saved.');
    }
  }

  async function createProject() {
    if (!projectName.trim()) {
      setLibraryError('Give the project a name before creating it.');
      return;
    }
    setLibraryError(null);
    try {
      const project = await library.createProject(projectName.trim());
      setProjectId(project.id);
      setProjectName('');
    } catch (error) {
      setLibraryError(error instanceof Error ? error.message : 'Project could not be created.');
    }
  }

  async function renameProject() {
    if (!projectId || !projectRename.trim()) {
      setLibraryError('Select a project and provide a name before renaming it.');
      return;
    }
    setLibraryError(null);
    try {
      await library.updateProject(projectId, projectRename.trim());
    } catch (error) {
      setLibraryError(error instanceof Error ? error.message : 'Project could not be renamed.');
    }
  }

  async function deleteSelectedProject() {
    if (!projectId) return;
    const project = library.projects.find((item) => item.id === projectId);
    if (
      !window.confirm(
        `Delete project "${project?.name ?? 'selected project'}"? Its presets will be ungrouped.`,
      )
    )
      return;
    setLibraryError(null);
    try {
      await library.deleteProject(projectId);
      setProjectId(null);
    } catch (error) {
      setLibraryError(error instanceof Error ? error.message : 'Project could not be deleted.');
    }
  }

  async function updateSelectedPreset() {
    if (!selectedPreset || !presetUpdateName.trim()) {
      setLibraryError('Select a preset and provide a name before updating it.');
      return;
    }
    setLibraryError(null);
    try {
      await library.updatePreset(selectedPreset.id, {
        name: presetUpdateName.trim(),
        projectId,
        recipe: editor.recipe,
      });
    } catch (error) {
      setLibraryError(error instanceof Error ? error.message : 'Preset could not be updated.');
    }
  }

  async function deletePreset(id: string) {
    const preset = library.presets.find((item) => item.id === id);
    if (!window.confirm(`Delete preset "${preset?.name ?? 'selected preset'}"?`)) return;
    setLibraryError(null);
    try {
      await library.deletePreset(id);
      if (selectedPresetId === id) setSelectedPresetId(null);
    } catch (error) {
      setLibraryError(error instanceof Error ? error.message : 'Preset could not be deleted.');
    }
  }

  const amountMin = filterName === 'blur' || filterName === 'sharpen' ? 1 : -100;
  const amountMax = filterName === 'blur' ? 8 : 100;
  const usesAmount = !['grayscale', 'invert', 'sepia'].includes(filterName);

  return (
    <section className="editor" aria-labelledby="editor-title">
      <div className="editor__intro">
        <div>
          <p className="eyebrow">Local by design</p>
          <h1 id="editor-title">Shape the image, not your privacy.</h1>
          <p>Pixels stay on this device while Rust and WebAssembly handle the heavy lifting.</p>
        </div>
        <ModelStatus />
      </div>

      <div className="editor__workspace">
        <aside className="controls" aria-label="Image controls">
          <label className="file-picker">
            <span>{editor.imageData ? 'Replace image' : 'Choose an image'}</span>
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              disabled={busy}
              onChange={(event) => void handleFile(event.currentTarget.files?.[0])}
            />
          </label>

          <div className="control-group">
            <h2>Adjustments</h2>
            <label>
              Effect
              <select
                value={filterName}
                disabled={!editor.imageData || busy}
                onChange={(event) => setFilterName(event.target.value as FilterName)}
              >
                {filterNames.map((name) => (
                  <option key={name} value={name}>
                    {filterLabels[name]}
                  </option>
                ))}
              </select>
            </label>
            {usesAmount ? (
              <label>
                Amount: {filterAmount}
                <input
                  type="range"
                  min={amountMin}
                  max={amountMax}
                  value={filterAmount}
                  disabled={!editor.imageData || busy}
                  onChange={(event) => setFilterAmount(Number(event.target.value))}
                />
              </label>
            ) : null}
            <button
              type="button"
              disabled={!editor.imageData || busy}
              onClick={() => void editor.applyFilter({ name: filterName, amount: filterAmount })}
            >
              Apply effect
            </button>
          </div>

          <div className="control-group">
            <h2>Crop</h2>
            <div className="button-grid crop-presets">
              <button
                type="button"
                disabled={!editor.sourceImageData || busy}
                onClick={() => selectAspectRatio(null)}
              >
                Original
              </button>
              <button
                type="button"
                disabled={!editor.sourceImageData || busy}
                onClick={() => selectAspectRatio(1)}
              >
                1:1
              </button>
              <button
                type="button"
                disabled={!editor.sourceImageData || busy}
                onClick={() => selectAspectRatio(4 / 3)}
              >
                4:3
              </button>
              <button
                type="button"
                disabled={!editor.sourceImageData || busy}
                onClick={() => selectAspectRatio(16 / 9)}
              >
                16:9
              </button>
            </div>
            <div className="crop-fields">
              {(['x', 'y', 'width', 'height'] as const).map((field) => (
                <label key={field}>
                  {field}
                  <input
                    type="number"
                    min={field === 'width' || field === 'height' ? 1 : 0}
                    step="1"
                    value={crop[field]}
                    disabled={!editor.sourceImageData || busy}
                    onChange={(event) =>
                      setCrop((current) => ({ ...current, [field]: Number(event.target.value) }))
                    }
                  />
                </label>
              ))}
            </div>
            <button
              type="button"
              disabled={!editor.sourceImageData || busy}
              onClick={() =>
                editor.sourceImageData &&
                void editor.crop(
                  boundedCrop(crop, editor.sourceImageData.width, editor.sourceImageData.height),
                )
              }
            >
              Apply crop
            </button>
            <button
              type="button"
              disabled={!editor.sourceImageData || busy}
              onClick={() => void editor.autoCrop()}
            >
              Auto-crop
            </button>
          </div>

          <div className="control-group">
            <h2>History</h2>
            <div className="button-grid">
              <button
                type="button"
                disabled={!editor.canUndo || busy}
                onClick={() => void editor.undo()}
              >
                Undo
              </button>
              <button
                type="button"
                disabled={!editor.canRedo || busy}
                onClick={() => void editor.redo()}
              >
                Redo
              </button>
            </div>
            <button
              type="button"
              disabled={!editor.imageData || busy}
              onClick={() => void editor.reset()}
            >
              Reset edits
            </button>
          </div>

          <div className="control-group">
            <h2>Export</h2>
            <label>
              Format
              <select
                value={exportSettings.mimeType}
                disabled={!editor.imageData || busy}
                onChange={(event) =>
                  setExportSettings((current) => ({
                    ...current,
                    mimeType: event.target.value as ExportSettings['mimeType'],
                  }))
                }
              >
                <option value="image/webp">WebP</option>
                <option value="image/jpeg">JPEG</option>
                <option value="image/png">PNG</option>
              </select>
            </label>
            <label>
              Quality: {Math.round(exportSettings.quality * 100)}
              <input
                type="range"
                min="0.1"
                max="1"
                step="0.05"
                value={exportSettings.quality}
                disabled={!editor.imageData || busy}
                onChange={(event) =>
                  setExportSettings((current) => ({
                    ...current,
                    quality: Number(event.target.value),
                  }))
                }
              />
            </label>
            <label>
              Maximum width
              <input
                type="number"
                min="1"
                placeholder="Original"
                value={exportSettings.maxWidth ?? ''}
                disabled={!editor.imageData || busy}
                onChange={(event) =>
                  setExportSettings((current) => ({
                    ...current,
                    maxWidth: event.target.value ? Number(event.target.value) : null,
                  }))
                }
              />
            </label>
            <button
              className="button-primary"
              type="button"
              disabled={!editor.imageData || busy}
              onClick={() => void download()}
            >
              Export image
            </button>
          </div>

          {library.enabled ? (
            <div className="control-group">
              <h2>Saved presets</h2>
              <label>
                Project
                <select
                  value={projectId ?? ''}
                  onChange={(event) => setProjectId(event.target.value || null)}
                >
                  <option value="">No project</option>
                  {library.projects.map((project) => (
                    <option key={project.id} value={project.id}>
                      {project.name}
                    </option>
                  ))}
                </select>
              </label>
              <div className="inline-form">
                <input
                  value={projectName}
                  maxLength={120}
                  placeholder="New project"
                  aria-label="New project name"
                  onChange={(event) => setProjectName(event.target.value)}
                />
                <button type="button" onClick={() => void createProject()}>
                  Add
                </button>
              </div>
              {projectId ? (
                <div className="inline-form">
                  <input
                    value={projectRename}
                    maxLength={120}
                    aria-label="Rename selected project"
                    onChange={(event) => setProjectRename(event.target.value)}
                  />
                  <button type="button" onClick={() => void renameProject()}>
                    Rename
                  </button>
                  <button type="button" onClick={() => void deleteSelectedProject()}>
                    Delete project
                  </button>
                </div>
              ) : null}
              <div className="inline-form">
                <input
                  value={presetName}
                  maxLength={80}
                  placeholder="Preset name"
                  aria-label="New preset name"
                  onChange={(event) => setPresetName(event.target.value)}
                />
                <button
                  type="button"
                  disabled={!editor.imageData || busy}
                  onClick={() => void savePreset()}
                >
                  Save
                </button>
              </div>
              {selectedPreset ? (
                <div className="inline-form">
                  <input
                    value={presetUpdateName}
                    maxLength={80}
                    aria-label="Rename selected preset"
                    onChange={(event) => setPresetUpdateName(event.target.value)}
                  />
                  <button
                    type="button"
                    disabled={!editor.imageData || busy}
                    onClick={() => void updateSelectedPreset()}
                  >
                    Update
                  </button>
                </div>
              ) : null}
              {library.loading ? <p className="hint">Loading saved presets…</p> : null}
              <ul className="preset-list">
                {library.presets.map((preset) => (
                  <li key={preset.id}>
                    <button
                      type="button"
                      aria-pressed={selectedPresetId === preset.id}
                      onClick={() => {
                        setSelectedPresetId(preset.id);
                        void editor.applyRecipe(preset.recipe);
                      }}
                    >
                      {preset.name}
                    </button>
                    <button
                      type="button"
                      aria-label={`Delete ${preset.name}`}
                      onClick={() => void deletePreset(preset.id)}
                    >
                      ×
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}

          <div className="control-group control-group--actions">
            <button type="button" disabled={!editor.imageData || busy} onClick={editor.clear}>
              Clear image
            </button>
          </div>
        </aside>

        <div className="preview-shell">
          {!editor.imageData && editor.status !== 'decoding' ? (
            <div className="empty-state">
              <span aria-hidden="true">◫</span>
              <p>Your image will appear here.</p>
              <small>JPEG, PNG, or WebP · processed locally</small>
            </div>
          ) : null}
          {editor.status === 'decoding' ? <p role="status">Decoding image…</p> : null}
          <canvas
            ref={canvasRef}
            className={editor.imageData ? 'preview' : 'preview preview--hidden'}
            aria-label="Edited image preview"
          />
          {editor.imageData ? (
            <p className="image-meta">
              {editor.imageData.width} × {editor.imageData.height} · {editor.metadata?.mimeType}
            </p>
          ) : null}
        </div>
      </div>

      <div aria-live="polite" aria-atomic="true">
        {editor.status === 'processing' ? <p className="notice">Processing image…</p> : null}
        {editor.error ? (
          <p className="notice notice--error" role="alert">
            {editor.error}
          </p>
        ) : null}
        {exportError ? (
          <p className="notice notice--error" role="alert">
            {exportError}
          </p>
        ) : null}
        {library.error || libraryError ? (
          <p className="notice notice--error" role="alert">
            {libraryError ?? library.error}
          </p>
        ) : null}
      </div>
    </section>
  );
}
