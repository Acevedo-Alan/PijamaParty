import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatDayMonth, formatMonthYear } from '../../i18n/formatDate.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import type { Chart, ChartPoint } from './dashboardApi.ts';

const messages = esAdmin.dashboard.chart;

const WIDTH = 600;
const HEIGHT = 160;
const GAP = 4;

function periodLabel(kind: Chart, period: string): string {
  return kind === 'DAYS' ? formatDayMonth(period) : formatMonthYear(period);
}

/**
 * UC-33: the sales per day or per month as bars, a hand-written SVG (no chart library in the admin chunk). A period
 * without sales is a 0 bar, a thin line at the base. The SVG is decorative; screen readers get the same values from a
 * visually hidden table.
 */
export function SalesChart({ kind, points }: { kind: Chart; points: ChartPoint[] }) {
  const highest = Math.max(1, ...points.map((point) => point.amountCents));
  const barWidth = points.length === 0 ? 0 : (WIDTH - GAP * (points.length - 1)) / points.length;
  const first = points[0];
  const last = points.at(-1);
  return (
    <>
      <svg className="sales-chart" viewBox={`0 0 ${String(WIDTH)} ${String(HEIGHT)}`} preserveAspectRatio="none"
        aria-hidden="true">
        {points.map((point, index) => {
          const height = Math.max(2, (point.amountCents / highest) * (HEIGHT - 4));
          return (
            <rect
              key={point.period}
              className={point.amountCents === 0 ? 'empty' : undefined}
              x={index * (barWidth + GAP)}
              y={HEIGHT - height}
              width={barWidth}
              height={height}
              rx={Math.min(3, barWidth / 2)}
            />
          );
        })}
      </svg>
      {first !== undefined && last !== undefined && (
        <p className="sales-chart-axis" aria-hidden="true">
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
              <td>{point.orders.toLocaleString('es-AR')}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
