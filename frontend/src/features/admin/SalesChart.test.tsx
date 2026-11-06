import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { ListSkeleton } from '../../components/ui/Skeleton.tsx';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { SalesChart } from './SalesChart.tsx';

const messages = esAdmin.dashboard.chart;
const points = [
  { period: '2026-10-14', amountCents: 500_000, orders: 1 },
  { period: '2026-10-15', amountCents: 1_200_000, orders: 3 },
];

describe('the sales chart (UC-33, T-E8-26)', () => {
  it('given sales, when the chart shows, then its scale is the highest amount and a hint says how to read a bar', () => {
    const { container } = render(<SalesChart kind="DAYS" points={points} />);

    expect(container.textContent).toContain(messages.highest(formatMoney(1_200_000)));
    expect(screen.getByText(messages.readingHint)).toBeInTheDocument();
  });

  it('given a bar hovered, when the pointer is on it, then its day, amount and orders show', async () => {
    const { container } = render(<SalesChart kind="DAYS" points={points} />);

    const bars = container.querySelectorAll('rect');
    await userEvent.hover(bars[1] as Element);

    const reading = screen.getByText(/15 oct/, { selector: 'p' });
    expect(reading).toHaveTextContent('3 pedidos');
    expect(reading.textContent).toContain(formatMoney(1_200_000));
    expect(bars[1]?.querySelector('title')?.textContent).toContain(formatMoney(1_200_000));
  });

  it('given no sale, when the chart shows, then there is no scale, only the empty state', () => {
    render(<SalesChart kind="DAYS" points={points.map((point) => ({ ...point, amountCents: 0 }))} />);

    expect(screen.getByText(messages.empty)).toBeInTheDocument();
    expect(screen.queryByText(/Máximo/)).not.toBeInTheDocument();
  });
});

describe('the list skeleton (T-E8-26)', () => {
  it('given a list loading, when it shows, then screen readers get the loading status and the blocks are hidden', () => {
    const { container } = render(<ListSkeleton rows={3} />);

    expect(screen.getByRole('status')).toHaveTextContent(esAdmin.loading);
    expect(container.querySelectorAll('li')).toHaveLength(3);
    expect(container.querySelector('ul')).toHaveAttribute('aria-hidden', 'true');
  });
});
