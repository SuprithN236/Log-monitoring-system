import type { Severity } from '../types.ts';

const STYLES: Record<Severity, string> = {
  CRITICAL: 'bg-red-500/15 text-red-400 ring-red-500/40',
  WARNING: 'bg-amber-500/15 text-amber-300 ring-amber-500/40',
  INFO: 'bg-blue-500/15 text-blue-300 ring-blue-500/40',
};

export function SeverityBadge({ severity }: { severity: Severity }) {
  return (
    <span
      className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-semibold tracking-wide ring-1 ring-inset ${
        STYLES[severity] ?? 'bg-slate-700/40 text-slate-300 ring-slate-600'
      }`}
    >
      {severity}
    </span>
  );
}
