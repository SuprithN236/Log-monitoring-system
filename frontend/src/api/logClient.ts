import type { AuthSession, LogEntity, LogSummary, User } from '../types.ts';

const LOGS_BASE = '/api/v1/logs';
const AUTH_BASE = '/api/v1/auth';
const LEDGER_LIMIT = 500;

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST';
  body?: unknown;
  token?: string;
  signal?: AbortSignal;
}

async function request<T>(path: string, { method = 'GET', body, token, signal }: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
  });
  if (!response.ok) {
    let detail = response.statusText;
    try {
      const problem = await response.json();
      const fieldErrors = problem.errors ? Object.entries(problem.errors).map(([field, msg]) => `${field} ${msg}`) : [];
      detail = fieldErrors.length > 0 ? fieldErrors.join('; ') : (problem.detail ?? problem.error ?? detail);
    } catch {
      // Non-JSON error bodies (e.g. a proxy failure page) fall back to the status text.
    }
    throw new ApiError(response.status, detail || `Request failed (${response.status})`);
  }
  return response.json() as Promise<T>;
}

export function signUp(email: string, password: string): Promise<AuthSession> {
  return request<AuthSession>(`${AUTH_BASE}/signup`, { method: 'POST', body: { email, password } });
}

export function signIn(email: string, password: string): Promise<AuthSession> {
  return request<AuthSession>(`${AUTH_BASE}/login`, { method: 'POST', body: { email, password } });
}

export function fetchCurrentUser(token: string, signal?: AbortSignal): Promise<User> {
  return request<User>(`${AUTH_BASE}/me`, { token, signal });
}

export function fetchLogs(token: string, signal?: AbortSignal): Promise<LogEntity[]> {
  return request<LogEntity[]>(`${LOGS_BASE}?limit=${LEDGER_LIMIT}`, { token, signal });
}

export function fetchSummary(token: string, signal?: AbortSignal): Promise<LogSummary> {
  return request<LogSummary>(`${LOGS_BASE}/summary`, { token, signal });
}

export { LEDGER_LIMIT };
