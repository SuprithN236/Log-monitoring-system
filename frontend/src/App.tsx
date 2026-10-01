import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { fetchLogs, fetchSummary, LEDGER_LIMIT } from './api/logClient.ts';
import { FilterBar } from './components/FilterBar.tsx';
import { LogLedger } from './components/LogLedger.tsx';
import { SummaryCards } from './components/SummaryCards.tsx';
import type { LogEntity, LogFilter, LogSummary } from './types.ts';

const AUTO_REFRESH_MS = 10_000;

function matchesFilter(log: LogEntity, filter: LogFilter): boolean {
  if (filter === 'ALL') return true;
  if (filter === 'CRITICAL') return log.severity === 'CRITICAL';
  return log.severity !== 'CRITICAL';
}

export default function App() {
  const [logs, setLogs] = useState<LogEntity[]>([]);
  const [summary, setSummary] = useState<LogSummary | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<LogFilter>('ALL');
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const inFlight = useRef<AbortController | null>(null);

  // `showSkeleton` is for user-visible loads; background polling refreshes the table in place.
  const loadLogs = useCallback(async (showSkeleton: boolean) => {
    inFlight.current?.abort();
    const controller = new AbortController();
    inFlight.current = controller;

    if (showSkeleton) setIsLoading(true);
    else setIsRefreshing(true);

    try {
      const [logData, summaryData] = await Promise.all([
        fetchLogs(controller.signal),
        fetchSummary(controller.signal),
      ]);
      setLogs(logData);
      setSummary(summaryData);
      setError(null);
      setLastUpdated(new Date());
    } catch (err) {
      if (controller.signal.aborted) return;
      setError(err instanceof Error ? err.message : 'Unable to reach the log service');
    } finally {
      if (inFlight.current === controller) {
        inFlight.current = null;
        setIsLoading(false);
        setIsRefreshing(false);
      }
    }
  }, []);

  useEffect(() => {
    loadLogs(true);
    return () => inFlight.current?.abort();
  }, [loadLogs]);

  useEffect(() => {
    if (!autoRefresh) return;
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible' && inFlight.current === null) {
        loadLogs(false);
      }
    }, AUTO_REFRESH_MS);
    return () => window.clearInterval(timer);
  }, [autoRefresh, loadLogs]);

  const filteredLogs = useMemo(() => logs.filter((log) => matchesFilter(log, filter)), [logs, filter]);

  const filterCounts = useMemo<Record<LogFilter, number>>(() => {
    const critical = logs.filter((log) => log.severity === 'CRITICAL').length;
    return { ALL: logs.length, CRITICAL: critical, INFO: logs.length - critical };
  }, [logs]);

  const busy = isLoading || isRefreshing;
  // Covers the first paint, before the initial fetch has started, so the empty state never flashes.
  const showSkeleton = isLoading || (lastUpdated === null && error === null);

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <header className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <div className="flex items-center gap-2 text-xs font-medium uppercase tracking-widest text-emerald-400">
            <span className="relative flex h-2 w-2">
              <span className={`absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75 ${autoRefresh ? 'animate-ping' : ''}`} />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" />
            </span>
            {autoRefresh ? 'Live' : 'Paused'}
          </div>
          <h1 className="mt-1 text-2xl font-semibold text-slate-50 sm:text-3xl">Log Monitoring Console</h1>
          <p className="mt-1 text-sm text-slate-400">
            Persisted runtime logs from the RabbitMQ ingestion pipeline
            {lastUpdated && <> · updated {lastUpdated.toLocaleTimeString()}</>}
          </p>
        </div>

        <div className="flex items-center gap-3">
          <label className="flex cursor-pointer select-none items-center gap-2 text-sm text-slate-300">
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={(event) => setAutoRefresh(event.target.checked)}
              className="h-4 w-4 rounded border-slate-600 bg-slate-800 accent-emerald-500"
            />
            Auto-refresh
          </label>
          <button
            type="button"
            onClick={() => loadLogs(true)}
            disabled={busy}
            className="inline-flex items-center gap-2 rounded-lg border border-slate-700 bg-slate-900 px-3 py-1.5 text-sm font-medium text-slate-200 transition-colors hover:border-slate-500 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <svg
              className={`h-4 w-4 ${busy ? 'animate-spin' : ''}`}
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              aria-hidden="true"
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h5M20 20v-5h-5M5.1 15a7 7 0 0 0 12.9 2M18.9 9A7 7 0 0 0 6 7" />
            </svg>
            Refresh
          </button>
        </div>
      </header>

      <SummaryCards summary={summary} isLoading={showSkeleton} />

      <section className="mt-8 overflow-hidden rounded-xl border border-slate-800 bg-slate-900/50 shadow-xl shadow-black/30">
        <div className="flex flex-col gap-3 border-b border-slate-800 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-base font-semibold text-slate-100">Log Streaming Ledger</h2>
            <p className="text-xs text-slate-500">
              Showing {filteredLogs.length} of the latest {logs.length} entries (max {LEDGER_LIMIT}) · click a row to expand
            </p>
          </div>
          <FilterBar active={filter} counts={filterCounts} onChange={setFilter} />
        </div>

        {error && (
          <div role="alert" className="flex items-center justify-between gap-4 border-b border-red-900/60 bg-red-950/40 px-4 py-3 text-sm text-red-300">
            <span>{error}</span>
            <button
              type="button"
              onClick={() => loadLogs(true)}
              className="shrink-0 rounded-md border border-red-700 px-2.5 py-1 text-xs font-medium hover:bg-red-900/40"
            >
              Retry
            </button>
          </div>
        )}

        <LogLedger logs={filteredLogs} isLoading={showSkeleton} />
      </section>
    </div>
  );
}
