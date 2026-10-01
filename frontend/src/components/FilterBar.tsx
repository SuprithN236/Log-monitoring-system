import type { LogFilter } from '../types.ts';

interface FilterBarProps {
  active: LogFilter;
  counts: Record<LogFilter, number>;
  onChange: (filter: LogFilter) => void;
}

const FILTERS: { value: LogFilter; label: string; activeClass: string }[] = [
  { value: 'ALL', label: 'All Logs', activeClass: 'bg-slate-100 text-slate-900 border-slate-100' },
  { value: 'CRITICAL', label: 'Critical Only', activeClass: 'bg-red-600 text-white border-red-600' },
  { value: 'INFO', label: 'Info Only', activeClass: 'bg-blue-600 text-white border-blue-600' },
];

export function FilterBar({ active, counts, onChange }: FilterBarProps) {
  return (
    <div role="group" aria-label="Filter logs by severity" className="flex flex-wrap gap-2">
      {FILTERS.map((filter) => {
        const isActive = filter.value === active;
        return (
          <button
            key={filter.value}
            type="button"
            aria-pressed={isActive}
            onClick={() => onChange(filter.value)}
            className={`inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-400 ${
              isActive
                ? filter.activeClass
                : 'border-slate-700 bg-slate-900 text-slate-300 hover:border-slate-500 hover:text-slate-100'
            }`}
          >
            {filter.label}
            <span
              className={`rounded-full px-1.5 text-xs tabular-nums ${
                isActive ? 'bg-black/20' : 'bg-slate-800 text-slate-400'
              }`}
            >
              {counts[filter.value]}
            </span>
          </button>
        );
      })}
    </div>
  );
}
