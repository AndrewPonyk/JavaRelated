const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8000";

export interface AddressRecord {
  id?: number | null;
  formatted_address: string;
  latitude: number;
  longitude: number;
  confidence: number;
  source: string;
}

export interface AddressLookupResponse {
  query: string;
  normalized_query: string;
  best_match: AddressRecord;
  candidates: AddressRecord[];
}

export async function lookupAddress(query: string): Promise<AddressLookupResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/geocode/lookup`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ query })
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json() as Promise<AddressLookupResponse>;
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { detail?: string };
    return body.detail ?? `Request failed with status ${response.status}`;
  } catch {
    return `Request failed with status ${response.status}`;
  }
}
