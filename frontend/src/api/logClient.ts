import type { LogEntity, LogSummary } from '../types.ts';

const API_BASE = '/api/v1/logs';
const LEDGER_LIMIT = 500;

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(path, { headers: { Accept: 'application/json' }, signal });
  if (!response.ok) {
    let detail = response.statusText;
    try {
      const body = await response.json();
      detail = body.detail ?? body.error ?? detail;
    } catch {
      // Non-JSON error bodies (e.g. a proxy failure page) fall back to the status text.
    }
    throw new ApiError(response.status, `Request failed (${response.status}): ${detail}`);
  }
  return response.json() as Promise<T>;
}

export function fetchLogs(signal?: AbortSignal): Promise<LogEntity[]> {
  return getJson<LogEntity[]>(`${API_BASE}?limit=${LEDGER_LIMIT}`, signal);
}

export function fetchSummary(signal?: AbortSignal): Promise<LogSummary> {
  return getJson<LogSummary>(`${API_BASE}/summary`, signal);
}

export { LEDGER_LIMIT };
