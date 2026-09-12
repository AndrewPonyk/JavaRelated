// Main reading screen: filter bar + worklist + viewer + ML assist panel.

import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { DicomViewer } from '@/components/DicomViewer';
import { MlResultsPanel } from '@/components/MlResultsPanel';
import { StudyList } from '@/components/StudyList';
import { useStudyImageIds } from '@/hooks/useStudies';
import type { Study, StudyQuery } from '@/types/dicom';

export function ViewerPage() {
  const queryClient = useQueryClient();
  const [filters, setFilters] = useState<StudyQuery>({ limit: 50 });
  const [selected, setSelected] = useState<Study | null>(null);
  const [triggering, setTriggering] = useState(false);

  const studyUid = selected?.study_instance_uid ?? null;
  const { data: imageIds = [], isLoading: imagesLoading } = useStudyImageIds(studyUid);

  async function runAnalysis() {
    if (!studyUid) return;
    setTriggering(true);
    try {
      const instances = await api.getStudyInstances(studyUid);
      await Promise.all(instances.map((i) => api.triggerMl(i.sop_instance_uid)));
      // Refresh the ML panel.
      await queryClient.invalidateQueries({ queryKey: ['ml-results', studyUid] });
    } finally {
      setTriggering(false);
    }
  }

  return (
    <div className="layout">
      <section className="layout__worklist">
        <h2>Worklist</h2>
        <FilterBar value={filters} onChange={setFilters} />
        <StudyList query={filters} selectedId={selected?.id} onSelect={setSelected} />
      </section>

      <section className="layout__viewer">
        {selected ? (
          <>
            <div className="viewer__toolbar">
              <div>
                <strong>{selected.description ?? 'Study'}</strong>{' '}
                <span className="muted">{selected.modalities}</span>
              </div>
              <button onClick={runAnalysis} disabled={triggering}>
                {triggering ? 'Analysing…' : 'Run AI analysis'}
              </button>
            </div>
            {imagesLoading ? (
              <div className="state state--loading">Loading images…</div>
            ) : (
              <DicomViewer imageIds={imageIds} />
            )}
            <MlResultsPanel studyInstanceUid={selected.study_instance_uid} />
          </>
        ) : (
          <p className="state state--empty">Select a study to begin reading.</p>
        )}
      </section>
    </div>
  );
}

function FilterBar({
  value,
  onChange,
}: {
  value: StudyQuery;
  onChange: (q: StudyQuery) => void;
}) {
  return (
    <div className="filterbar">
      <input
        placeholder="Patient ID"
        value={value.patient_id ?? ''}
        onChange={(e) => onChange({ ...value, patient_id: e.target.value || undefined })}
      />
      <select
        value={value.modality ?? ''}
        onChange={(e) => onChange({ ...value, modality: e.target.value || undefined })}
      >
        <option value="">All modalities</option>
        <option value="DX">DX</option>
        <option value="CR">CR</option>
        <option value="CT">CT</option>
        <option value="MR">MR</option>
      </select>
    </div>
  );
}
