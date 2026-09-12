// Enrollment workflow: create an enrollment and drive the state machine
// (screening → consent → randomize → activate), each step calling the API.
import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/api/client";
import { AsyncBoundary } from "@/components/AsyncBoundary";
import { isReadOnly, useAuth } from "@/auth/AuthContext";
import type { Enrollment, Paginated, Study, Subject } from "@/types";

const NEXT_ACTION: Record<string, { label: string; path: string } | null> = {
  SCREENING: { label: "Record consent", path: "consent" },
  CONSENTED: { label: "Randomize", path: "randomize" },
  RANDOMIZED: { label: "Activate", path: "activate" },
  ENROLLED: null,
  WITHDRAWN: null,
  SCREEN_FAILED: null,
};

export function EnrollmentPage() {
  const { user } = useAuth();
  const readOnly = isReadOnly(user);
  const qc = useQueryClient();

  const enrollments = useQuery({
    queryKey: ["enrollments"],
    queryFn: () => api.get<Paginated<Enrollment>>("/enrollments/"),
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
  const [error, setError] = useState<string | null>(null);

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["enrollments"] });
    qc.invalidateQueries({ queryKey: ["subjects"] });
  };

  const create = useMutation({
    mutationFn: (body: { study: string; subject: string }) =>
      api.post<Enrollment>("/enrollments/", body),
    onSuccess: refresh,
  });

  const advance = useMutation({
    mutationFn: ({ id, path }: { id: string; path: string }) =>
      api.post<Enrollment>(`/enrollments/${id}/${path}/`, {}),
    onSuccess: refresh,
  });

  const withdraw = useMutation({
    mutationFn: (id: string) =>
      api.post<Enrollment>(`/enrollments/${id}/withdraw/`, { reason: "withdrawn by user" }),
    onSuccess: refresh,
  });

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!study || !subject) return setError("Select both a study and a subject.");
    try {
      await create.mutateAsync({ study, subject });
      setSubject("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create enrollment.");
    }
  }

  async function onAdvance(enr: Enrollment) {
    const next = NEXT_ACTION[enr.status];
    if (!next) return;
    try {
      await advance.mutateAsync({ id: enr.id, path: next.path });
    } catch (err) {
      alert(err instanceof ApiError ? err.message : "Action failed.");
    }
  }

  const rows = enrollments.data?.results ?? [];

  return (
    <section>
      <h2>Enrollment</h2>

      {!readOnly && (
        <form className="card" onSubmit={onCreate}>
          <h3>Enroll a subject</h3>
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
                {(subjects.data?.results ?? []).filter((s) => !s.enrolled).map((s) => (
                  <option key={s.id} value={s.id}>{s.subject_code}</option>
                ))}
              </select>
            </label>
          </div>
          <button type="submit" disabled={create.isPending}>Start screening</button>
        </form>
      )}

      <AsyncBoundary
        isLoading={enrollments.isLoading}
        isError={enrollments.isError}
        error={enrollments.error}
        isEmpty={rows.length === 0}
        emptyMessage="No enrollments yet."
        onRetry={() => enrollments.refetch()}
      >
        <table className="data-table">
          <thead>
            <tr><th>Subject</th><th>Status</th><th>Arm</th>{!readOnly && <th>Next step</th>}</tr>
          </thead>
          <tbody>
            {rows.map((enr) => {
              const next = NEXT_ACTION[enr.status];
              const terminal = ["WITHDRAWN", "SCREEN_FAILED"].includes(enr.status);
              return (
                <tr key={enr.id}>
                  <td>{enr.subject_code}</td>
                  <td><span className={`badge status-${enr.status}`}>{enr.status}</span></td>
                  <td>{enr.arm_name ?? "—"}</td>
                  {!readOnly && (
                    <td className="actions">
                      {next && (
                        <button onClick={() => onAdvance(enr)} disabled={advance.isPending}>
                          {next.label}
                        </button>
                      )}
                      {!terminal && (
                        <button className="link-danger" onClick={() => withdraw.mutate(enr.id)}>
                          Withdraw
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </AsyncBoundary>
    </section>
  );
}
