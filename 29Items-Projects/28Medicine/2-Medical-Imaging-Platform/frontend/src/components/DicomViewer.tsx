// Cornerstone3D viewport. Renders a DICOM stack with window/level, zoom, pan,
// and stack scroll. Pixels load via the wadouri loader from presigned URLs.

import { useEffect, useRef, useState } from 'react';
import { renderStack, type ViewerHandle } from '@/lib/cornerstone';

interface DicomViewerProps {
  imageIds: string[];
}

export function DicomViewer({ imageIds }: DicomViewerProps) {
  const elementRef = useRef<HTMLDivElement>(null);
  const [status, setStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let handle: ViewerHandle | null = null;
    let cancelled = false;

    async function setup() {
      if (!elementRef.current || imageIds.length === 0) {
        setStatus('idle');
        return;
      }
      setStatus('loading');
      setError(null);
      try {
        handle = await renderStack(elementRef.current, imageIds);
        if (!cancelled) setStatus('ready');
      } catch (e) {
        if (!cancelled) {
          setStatus('error');
          setError(e instanceof Error ? e.message : 'Failed to initialise viewer');
        }
      }
    }

    void setup();
    return () => {
      cancelled = true;
      handle?.destroy();
    };
  }, [imageIds]);

  return (
    <div className="viewer">
      {status === 'error' && (
        <div className="state state--error" role="alert">
          Viewer error: {error}
        </div>
      )}
      {imageIds.length === 0 && (
        <div className="state state--empty">No images to display.</div>
      )}
      <div
        ref={elementRef}
        className="viewer__viewport"
        onContextMenu={(e) => e.preventDefault()}
      />
      <p className="viewer__hint">
        {status === 'ready'
          ? `${imageIds.length} image(s) · Scroll: stack · Drag: W/L · Right-drag: zoom · Middle-drag: pan`
          : status === 'loading'
            ? 'Loading images…'
            : ''}
      </p>
    </div>
  );
}
