import { useState } from 'react';
import type { LogEntity } from '../types.ts';
import { SeverityBadge } from './SeverityBadge.tsx';

interface LogLedgerProps {
  logs: LogEntity[];
  isLoading: boolean;
}

const timestampFormat = new Intl.DateTimeFormat(undefined, {
  year: 'numeric',
  month: 'short',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
});

// The backend stores UTC without an offset, so mark it as UTC before converting to local time.
function formatTimestamp(value: string): string {
  const date = new Date(/[zZ]|[+-]\d{2}:?\d{2}$/.test(value) ? value : `${value}Z`);
  return Number.isNaN(date.getTime()) ? value : timestampFormat.format(date);
}

const SKELETON_ROWS = 8;

function SkeletonRows() {
  return (
    <>
      {Array.from({ length: SKELETON_ROWS }, (_, i) => (
        <tr key={i} className="animate-pulse">
          <td className="px-4 py-3"><div className="h-4 w-36 rounded bg-slate-800" /></td>
          <td className="px-4 py-3"><div className="h-4 w-28 rounded bg-slate-800" /></td>
          <td className="px-4 py-3"><div className="h-5 w-16 rounded bg-slate-800" /></td>
          <td className="px-4 py-3"><div className="h-4 rounded bg-slate-800" style={{ width: `${55 + ((i * 17) % 40)}%` }} /></td>
        </tr>
      ))}
    </>
  );
}

export function LogLedger({ logs, isLoading }: LogLedgerProps) {
  const [expandedId, setExpandedId] = useState<number | null>(null);

  return (
    <div className="max-h-[60vh] overflow-auto" aria-busy={isLoading}>
      <table className="w-full min-w-[720px] border-collapse text-left text-sm">
        <thead className="sticky top-0 z-10 bg-slate-900/95 backdrop-blur">
          <tr className="border-b border-slate-800 text-xs uppercase tracking-wider text-slate-400">
            <th scope="col" className="w-48 px-4 py-3 font-medium">Timestamp</th>
            <th scope="col" className="w-44 px-4 py-3 font-medium">Service Name</th>
            <th scope="col" className="w-28 px-4 py-3 font-medium">Severity</th>
            <th scope="col" className="px-4 py-3 font-medium">Log Message</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800/70">
          {isLoading ? (
            <SkeletonRows />
          ) : logs.length === 0 ? (
            <tr>
              <td colSpan={4} className="px-4 py-16 text-center text-slate-500">
                No logs match this view yet.
              </td>
            </tr>
          ) : (
            logs.map((log) => {
              const isExpanded = expandedId === log.id;
              return (
                <tr
                  key={log.id}
                  onClick={() => setExpandedId(isExpanded ? null : log.id)}
                  className={`cursor-pointer align-top transition-colors hover:bg-slate-800/40 ${
                    log.severity === 'CRITICAL' ? 'bg-red-950/20' : ''
                  }`}
                >
                  <td className="whitespace-nowrap px-4 py-3 font-mono text-xs text-slate-400 tabular-nums">
                    {formatTimestamp(log.timestamp)}
                  </td>
                  <td className="px-4 py-3 font-medium text-slate-200">{log.serviceName}</td>
                  <td className="px-4 py-3"><SeverityBadge severity={log.severity} /></td>
                  <td className="px-4 py-3">
                    <p
                      title={isExpanded ? undefined : 'Click to expand'}
                      className={`whitespace-pre-wrap break-words font-mono text-xs text-slate-300 ${
                        isExpanded ? '' : 'line-clamp-2'
                      }`}
                    >
                      {log.logMessage}
                    </p>
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>
    </div>
  );
}
