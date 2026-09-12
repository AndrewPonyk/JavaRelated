// ML eligibility screening: submit an EHR note for screening, then review the
// advisory ML rationale and record the authoritative human (e-signed) decision.
import { Fragment, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/api/client";
import { AsyncBoundary } from "@/components/AsyncBoundary";
import { isReadOnly, useAuth } from "@/auth/AuthContext";
import type { Decision, Paginated, Screening, Study, Subject } from "@/types";

export function EligibilityPage() {
  const { user } = useAuth();
  const readOnly = isReadOnly(user);
  const qc = useQueryClient();

  const screenings = useQuery({
    queryKey: ["screenings"],
    queryFn: () => api.get<Paginated<Screening>>("/screenings/"),
    refetchInterval: (q) =>
      (q.state.data?.results ?? []).some((s) => s.status === "PENDING") ? 2000 : false,
  });
  const studies = useQuery({
    queryKey: ["studies", { status: "OPEN" }],
    queryFn: () => api.get<Paginated<Study>>("/studies/?status=OPEN"),
  });
  const subjects = useQuery({
    queryKey: ["subjects"],
    queryFn: () => api.get<Paginated<Subject>>("/subjects/"),
  });

  const [study, setStudy] = useState("");
  const [subject, setSubject] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<string | null>(null);

  const refresh = () => qc.invalidateQueries({ queryKey: ["screenings"] });

  const create = useMutation({
    mutationFn: (body: { study: string; subject: string; note_text: string }) =>
      api.post<Screening>("/screenings/", body),
    onSuccess: () => {
      refresh();
      setNote("");
    },
  });

  const decide = useMutation({
    mutationFn: ({ id, decision }: { id: string; decision: Decision }) =>
      api.post<Screening>(`/screenings/${id}/decision/`, { decision }),
    onSuccess: refresh,
  });

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!study || !subject) return setError("Select a study and a subject.");
    if (note.trim().length < 5) return setError("Paste the clinical note to screen.");
    try {
      await create.mutateAsync({ study, subject, note_text: note });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to start screening.");
    }
  }

  const rows = screenings.data?.results ?? [];

  return (
    <section>
      <h2>Eligibility screening</h2>
      <p className="muted">
        ML screening is <strong>advisory</strong>. A clinician records the authoritative decision.
      </p>

      {!readOnly && (
        <form className="card" onSubmit={onCreate}>
          <h3>New screening</h3>
          {error && <div role="alert" className="error-box">{error}</div>}
          <div className="form-row">
            <label>Study
              <select value={study} onChange={(e) => setStudy(e.target.value)}>
                <option value="">Select open study…</option>
                {(studies.data?.results ?? []).map((s) => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </select>
            </label>
            <label>Subject
              <select value={subject} onChange={(e) => setSubject(e.target.value)}>
                <option value="">Select subject…</option>
                {(subjects.data?.results ?? []).map((s) => (
                  <option key={s.id} value={s.id}>{s.subject_code}</option>
                ))}
              </select>
            </label>
          </div>
          <label>Clinical note (EHR)
            <textarea rows={4} value={note} onChange={(e) => setNote(e.target.value)}
              placeholder="Paste de-identifiable clinical note…" />
          </label>
          <button type="submit" disabled={create.isPending}>Run screening</button>
        </form>
      )}

      <AsyncBoundary
        isLoading={screenings.isLoading}
        isError={screenings.isError}
        error={screenings.error}
        isEmpty={rows.length === 0}
        emptyMessage="No screenings yet."
        onRetry={() => screenings.refetch()}
      >
        <table className="data-table">
          <thead>
            <tr><th>Subject</th><th>Status</th><th>ML rec.</th><th>Score</th><th>Decision</th><th></th></tr>
          </thead>
          <tbody>
            {rows.map((s) => (
              <Fragment key={s.id}>
                <tr>
                  <td>{s.subject_code}</td>
                  <td><span className={`badge status-${s.status}`}>{s.status}</span></td>
                  <td>{s.ml_recommendation || "—"}</td>
                  <td>{s.ml_score != null ? s.ml_score.toFixed(2) : "—"}</td>
                  <td>{s.human_decision || "—"}</td>
                  <td>
                    {s.status === "AWAITING_REVIEW" && (
                      <button onClick={() => setExpanded(expanded === s.id ? null : s.id)}>
                        Review
                      </button>
                    )}
                  </td>
                </tr>
                {expanded === s.id && (
                  <tr>
                    <td colSpan={6}>
                      <div className="rationale">
                        <h4>ML rationale (model {s.model_version})</h4>
                        <ul>
                          {s.rationale.map((r, i) => (
                            <li key={i}>
                              <span className={`tag ${r.type}`}>{r.type}</span> {r.text} —{" "}
                              <strong>{r.satisfied === null ? "unknown" : r.satisfied ? "satisfied" : "not satisfied"}</strong>
                              {r.evidence && <em> · “{r.evidence}”</em>}
                            </li>
                          ))}
                        </ul>
                        {!readOnly && (
                          <div className="actions">
                            <button onClick={() => decide.mutate({ id: s.id, decision: "ELIGIBLE" })}>
                              Confirm eligible
                            </button>
                            <button className="link-danger"
                              onClick={() => decide.mutate({ id: s.id, decision: "INELIGIBLE" })}>
                              Mark ineligible
                            </button>
                          </div>
                        )}
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      </AsyncBoundary>
    </section>
  );
}
