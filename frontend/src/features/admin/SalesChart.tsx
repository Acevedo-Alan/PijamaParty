import { ChartNoAxesColumn } from 'lucide-react';
import { useState } from 'react';
import { EmptyState } from '../../components/ui/EmptyState.tsx';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatDayMonth, formatMonthYear } from '../../i18n/formatDate.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { cn } from '../../components/ui/cn.ts';
import type { Chart, ChartPoint } from './dashboardApi.ts';
import { formatNumber } from '../../i18n/formatNumber.ts';

const messages = esAdmin.dashboard.chart;
const ordersText = esAdmin.dashboard.sales.orders;

const WIDTH = 600;
const HEIGHT = 160;
const GAP = 4;

function periodLabel(kind: Chart, period: string): string {
  return kind === 'DAYS' ? formatDayMonth(period) : formatMonthYear(period);
}

/**
 * UC-33: the sales per day or per month as bars, a hand-written SVG (no chart library in the admin chunk). A period
 * without sales is a 0 bar, a thin line at the base. The SVG is decorative; screen readers get the same values from a
 * visually hidden table. With no sale at all it shows an empty state instead (D-E8-12). The bars grow from their base
 * when they first show, as when the view switches between days and months (new keys), not on a refresh.
 */
export function SalesChart({ kind, points }: { kind: Chart; points: ChartPoint[] }) {
  // The bar a pointer is on (hover, or a tap on a phone): its amount shows under the chart. A keyboard or a screen
  // reader reads the same values from the table below (T-E8-26).
  const [chosen, setChosen] = useState<string | null>(null);
  const highest = Math.max(1, ...points.map((point) => point.amountCents));
  const barWidth = points.length === 0 ? 0 : (WIDTH - GAP * (points.length - 1)) / points.length;
  const first = points[0];
  const last = points.at(-1);
  const reading = points.find((point) => point.period === chosen);
  const empty = points.every((point) => point.amountCents === 0);
  return (
    <>
      {empty ? (
        <EmptyState icon={<ChartNoAxesColumn />} title={messages.empty}>
          {messages.emptyHint}
        </EmptyState>
      ) : (
        <p className="m-0 text-xs font-semibold tabular-nums text-muted-foreground" aria-hidden="true">
          {messages.highest(formatMoney(highest))}
        </p>
      )}
      {!empty && (
        <svg
          className="block h-40 w-full"
          viewBox={`0 0 ${String(WIDTH)} ${String(HEIGHT)}`}
          preserveAspectRatio="none"
          aria-hidden="true"
        >
          {points.map((point, index) => {
            const height = Math.max(2, (point.amountCents / highest) * (HEIGHT - 4));
            return (
              <rect
                key={point.period}
                onPointerEnter={() => {
                  setChosen(point.period);
                }}
                onPointerDown={() => {
                  setChosen(point.period);
                }}
                className={cn(
                  point.amountCents === 0 ? 'fill-input' : 'fill-primary',
                  'origin-bottom [transform-box:fill-box] motion-safe:animate-grow',
                )}
                style={{ animationDelay: `${String(index * 12)}ms` }}
                x={index * (barWidth + GAP)}
                y={HEIGHT - height}
                width={barWidth}
                height={height}
                rx={Math.min(3, barWidth / 2)}
              >
                <title>{`${periodLabel(kind, point.period)}: ${formatMoney(point.amountCents)}`}</title>
              </rect>
            );
          })}
        </svg>
      )}
      {!empty && (
        <p className="m-0 min-h-5 text-[13px] font-semibold tabular-nums text-foreground" aria-hidden="true">
          {reading === undefined
            ? messages.readingHint
            : messages.reading(
                periodLabel(kind, reading.period),
                formatMoney(reading.amountCents),
                ordersText(reading.orders),
              )}
        </p>
      )}
      {!empty && first !== undefined && last !== undefined && (
        <p className="m-0 flex justify-between text-xs text-muted-foreground" aria-hidden="true">
          <span>{periodLabel(kind, first.period)}</span>
          <span>{periodLabel(kind, last.period)}</span>
        </p>
      )}
      <table className="visually-hidden">
        <caption>{messages.tableCaption(kind)}</caption>
        <thead>
          <tr>
            <th scope="col">{messages.period}</th>
            <th scope="col">{messages.amount}</th>
            <th scope="col">{messages.orders}</th>
          </tr>
        </thead>
        <tbody>
          {points.map((point) => (
            <tr key={point.period}>
              <th scope="row">{periodLabel(kind, point.period)}</th>
              <td>{formatMoney(point.amountCents)}</td>
              <td>{formatNumber(point.orders)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
