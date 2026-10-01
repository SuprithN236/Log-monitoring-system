import type { LogSummary } from '../types.ts';

interface SummaryCardsProps {
  summary: LogSummary | null;
  isLoading: boolean;
}

const numberFormat = new Intl.NumberFormat();

export function SummaryCards({ summary, isLoading }: SummaryCardsProps) {
  const cards = [
    {
      label: 'Total Logs',
      value: summary?.total,
      valueClass: 'text-slate-100',
      accent: 'border-slate-700',
      caption: 'Persisted across all services',
    },
    {
      label: 'Critical Failures',
      value: summary?.critical,
      valueClass: 'font-bold text-red-500',
      accent: 'border-red-500/40',
      caption: 'Routed via *.critical and alerted',
    },
    {
      label: 'Standard Info Traces',
      value: summary?.standard,
      valueClass: 'text-blue-400',
      accent: 'border-blue-500/40',
      caption: 'INFO and WARNING via *.info',
    },
  ];

  return (
    <section aria-label="Log summary" className="grid grid-cols-1 gap-4 sm:grid-cols-3">
      {cards.map((card) => (
        <div
          key={card.label}
          className={`rounded-xl border ${card.accent} bg-slate-900/70 p-5 shadow-lg shadow-black/20`}
        >
          <p className="text-xs font-medium uppercase tracking-wider text-slate-400">{card.label}</p>
          {isLoading && summary === null ? (
            <div className="mt-3 h-9 w-24 animate-pulse rounded bg-slate-800" />
          ) : (
            <p className={`mt-2 text-4xl tabular-nums ${card.valueClass}`}>
              {card.value === undefined ? '—' : numberFormat.format(card.value)}
            </p>
          )}
          <p className="mt-2 text-xs text-slate-500">{card.caption}</p>
        </div>
      ))}
    </section>
  );
}
