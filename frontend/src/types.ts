export type Severity = 'CRITICAL' | 'WARNING' | 'INFO';

/** Mirrors the backend's LogEntryResponse. `timestamp` is an ISO local date-time in UTC. */
export interface LogEntity {
  id: number;
  serviceName: string;
  severity: Severity;
  logMessage: string;
  timestamp: string;
}

export interface LogSummary {
  total: number;
  critical: number;
  standard: number;
}

/** INFO matches every log routed to storage as a standard trace (INFO and WARNING). */
export type LogFilter = 'ALL' | 'CRITICAL' | 'INFO';

export interface User {
  id: number;
  email: string;
}

export interface AuthSession {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  user: User;
}
