// Worklist table. Standard data-display pattern: explicit loading / error /
// empty / data states via TanStack Query.

import { useStudies } from '@/hooks/useStudies';
import type { Study, StudyQuery } from '@/types/dicom';

interface StudyListProps {
  query?: StudyQuery;
  selectedId?: string | null;
  onSelect?: (study: Study) => void;
}

function formatDicomDate(d: string | null): string {
  if (!d || d.length !== 8) return '—';
  return `${d.slice(0, 4)}-${d.slice(4, 6)}-${d.slice(6, 8)}`;
}

export function StudyList({ query = {}, selectedId, onSelect }: StudyListProps) {
  const { data, isLoading, isError, error, refetch } = useStudies(query);

  if (isLoading) return <div className="state state--loading">Loading worklist…</div>;

  if (isError) {
    return (
      <div className="state state--error" role="alert">
        <p>Failed to load studies: {error.message}</p>
        <button onClick={() => refetch()}>Retry</button>
      </div>
    );
  }

  if (!data || data.items.length === 0) {
    return <div className="state state--empty">No studies match the current filter.</div>;
  }

  return (
    <table className="worklist">
      <thead>
        <tr>
          <th>Study Date</th>
          <th>Accession</th>
          <th>Description</th>
          <th>Modality</th>
          <th>Series</th>
        </tr>
      </thead>
      <tbody>
        {data.items.map((study) => (
          <tr
            key={study.id}
            className={`worklist__row${study.id === selectedId ? ' is-selected' : ''}`}
            onClick={() => onSelect?.(study)}
            tabIndex={0}
            onKeyDown={(e) => e.key === 'Enter' && onSelect?.(study)}
          >
            <td>{formatDicomDate(study.study_date)}</td>
            <td>{study.accession_number ?? '—'}</td>
            <td>{study.description ?? '—'}</td>
            <td>{study.modalities ?? '—'}</td>
            <td>{study.series_count}</td>
          </tr>
        ))}
      </tbody>
      <tfoot>
        <tr>
          <td colSpan={5}>
            {data.meta.total} studies · showing {data.items.length}
          </td>
        </tr>
      </tfoot>
    </table>
  );
}
