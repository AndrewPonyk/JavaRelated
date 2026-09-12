export type CompileOptions = {
  optimize: boolean;
  sourceMaps: boolean;
  target: "es2020" | "es2021" | "es2022" | "esnext";
};

export type Diagnostic = {
  code: string;
  severity: "info" | "warning" | "error";
  message: string;
  span?: {
    start: number;
    end: number;
  } | null;
};

export type CompileOutput = {
  javascript: string;
  sourceMap?: unknown;
  diagnostics: Diagnostic[];
  cacheHit: boolean;
};

export type CompileJob = {
  id: string;
  source: string;
  options: CompileOptions;
  output?: CompileOutput | null;
  status: "pending" | "succeeded" | "failed";
  createdAt: string;
  updatedAt: string;
};

export type CompileArtifact = {
  id: string;
  compileJobId: string;
  artifactType: "javascript" | "sourceMap" | "diagnostics";
  content: unknown;
  createdAt: string;
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export async function listCompileJobs(): Promise<CompileJob[]> {
  return request<CompileJob[]>("/api/compile-jobs?limit=50");
}

export async function createCompileJob(
  source: string,
  options: CompileOptions
): Promise<CompileJob> {
  return request<CompileJob>("/api/compile-jobs", {
    method: "POST",
    body: JSON.stringify({ source, options })
  });
}

export async function updateCompileJob(
  id: string,
  source: string,
  options: CompileOptions
): Promise<CompileJob> {
  return request<CompileJob>(`/api/compile-jobs/${id}`, {
    method: "PUT",
    body: JSON.stringify({ source, options })
  });
}

export async function deleteCompileJob(id: string): Promise<void> {
  await request<void>(`/api/compile-jobs/${id}`, {
    method: "DELETE",
    expectJson: false
  });
}

export async function listCompileArtifacts(id: string): Promise<CompileArtifact[]> {
  return request<CompileArtifact[]>(`/api/compile-jobs/${id}/artifacts`);
}

async function request<T>(
  path: string,
  init: RequestInit & { expectJson?: boolean } = {}
): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers: {
      "content-type": "application/json",
      ...init.headers
    }
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    const message =
      body && typeof body.error === "string"
        ? body.error
        : `request failed with status ${response.status}`;
    throw new Error(message);
  }

  if (init.expectJson === false || response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}
