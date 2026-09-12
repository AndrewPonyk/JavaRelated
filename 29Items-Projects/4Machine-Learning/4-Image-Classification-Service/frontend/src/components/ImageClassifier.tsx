// Upload widget: pick/drop an image, classify it, and show all states.
import { useCallback, useState } from 'react';

import { useClassify } from '../hooks/useClassify';
import { ResultDisplay } from './ResultDisplay';

export function ImageClassifier() {
  const [preview, setPreview] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const { data, loading, error, classify, reset } = useClassify();

  const handleFile = useCallback(
    (file: File | undefined) => {
      if (!file) return;
      reset();
      setPreview(URL.createObjectURL(file));
      void classify(file);
    },
    [classify, reset],
  );

  return (
    <section className="card">
      <h2>Classify a product image</h2>

      <label
        className={`dropzone${dragging ? ' dragging' : ''}`}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          handleFile(e.dataTransfer.files?.[0]);
        }}
      >
        <input
          type="file"
          accept="image/*"
          hidden
          onChange={(e) => handleFile(e.target.files?.[0])}
        />
        <span>Drag & drop an image here, or click to browse</span>
      </label>

      {preview && (
        <div className="preview">
          <img src={preview} alt="preview" />
        </div>
      )}

      <div className="status" aria-live="polite">
        {loading && <p className="muted">Classifying…</p>}
        {error && <p className="error">⚠ {error}</p>}
        {data && <ResultDisplay result={data} />}
      </div>
    </section>
  );
}
