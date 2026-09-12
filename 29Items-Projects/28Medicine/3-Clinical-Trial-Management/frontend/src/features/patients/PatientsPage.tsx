// Subjects: list (PHI redacted by the API for non-clinical roles) + create.
import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/api/client";
import { AsyncBoundary } from "@/components/AsyncBoundary";
import { isReadOnly, useAuth } from "@/auth/AuthContext";
import type { Paginated, Subject } from "@/types";

function useSubjects() {
  return useQuery({
    queryKey: ["subjects"],
    queryFn: () => api.get<Paginated<Subject>>("/subjects/"),
  });
}

export function PatientsPage() {
  const { user } = useAuth();
  const readOnly = isReadOnly(user);
  const qc = useQueryClient();
  const subjects = useSubjects();

  const [form, setForm] = useState({ first_name: "", last_name: "", date_of_birth: "", sex_at_birth: "F" });
  const [error, setError] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: (body: typeof form) => api.post<Subject>("/subjects/", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["subjects"] });
      setForm({ first_name: "", last_name: "", date_of_birth: "", sex_at_birth: "F" });
    },
  });

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (form.date_of_birth && !/^\d{4}-\d{2}-\d{2}$/.test(form.date_of_birth)) {
      return setError("Date of birth must be YYYY-MM-DD.");
    }
    try {
      await create.mutateAsync(form);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create subject.");
    }
  }

  const rows = subjects.data?.results ?? [];

  return (
    <section>
      <h2>Subjects</h2>

      {!readOnly && (
        <form className="card" onSubmit={onSubmit}>
          <h3>Register subject</h3>
          {error && <div role="alert" className="error-box">{error}</div>}
          <div className="form-row">
            <label>First name<input value={form.first_name}
              onChange={(e) => setForm({ ...form, first_name: e.target.value })} /></label>
            <label>Last name<input value={form.last_name}
              onChange={(e) => setForm({ ...form, last_name: e.target.value })} /></label>
            <label>DOB<input placeholder="YYYY-MM-DD" value={form.date_of_birth}
              onChange={(e) => setForm({ ...form, date_of_birth: e.target.value })} /></label>
            <label>Sex
              <select value={form.sex_at_birth}
                onChange={(e) => setForm({ ...form, sex_at_birth: e.target.value })}>
                <option value="F">F</option>
                <option value="M">M</option>
                <option value="">Unknown</option>
              </select>
            </label>
          </div>
          <button type="submit" disabled={create.isPending}>
            {create.isPending ? "Saving…" : "Register"}
          </button>
        </form>
      )}

      <AsyncBoundary
        isLoading={subjects.isLoading}
        isError={subjects.isError}
        error={subjects.error}
        isEmpty={rows.length === 0}
        emptyMessage="No subjects registered."
        onRetry={() => subjects.refetch()}
      >
        <table className="data-table">
          <thead>
            <tr><th>Code</th><th>Name</th><th>Age</th><th>Sex</th><th>Enrolled</th></tr>
          </thead>
          <tbody>
            {rows.map((s) => (
              <tr key={s.id}>
                <td>{s.subject_code}</td>
                <td>{s.first_name} {s.last_name}</td>
                <td>{s.age ?? "—"}</td>
                <td>{s.sex_at_birth || "—"}</td>
                <td>{s.enrolled ? "Yes" : "No"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </AsyncBoundary>
    </section>
  );
}
