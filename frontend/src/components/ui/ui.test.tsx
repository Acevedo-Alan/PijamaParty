import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { Badge } from './Badge.tsx';
import { Button } from './Button.tsx';
import { Card, CardContent, CardHeader, CardTitle } from './Card.tsx';
import { EmptyState } from './EmptyState.tsx';
import { SegmentedControl } from './SegmentedControl.tsx';
import { Switch } from './Switch.tsx';

const PERIODS = [
  { value: 'today', label: 'Hoy' },
  { value: 'week', label: '7 días' },
  { value: 'month', label: 'Este mes' },
] as const;

function Periods({ onChange }: { onChange?: (value: string) => void }) {
  const [value, setValue] = useState<(typeof PERIODS)[number]['value']>('month');
  return (
    <SegmentedControl
      label="Período"
      options={PERIODS}
      value={value}
      onValueChange={(next) => {
        setValue(next);
        onChange?.(next);
      }}
    />
  );
}

describe('back-office UI components (T-E8-18, D-E8-12)', () => {
  it('given a button, when nothing says its type, then it is a plain button that does not submit a form', () => {
    render(<Button>Actualizar</Button>);

    expect(screen.getByRole('button', { name: 'Actualizar' })).toHaveAttribute('type', 'button');
  });

  it('given a button as a link, when rendered, then the link takes its look and stays a link', () => {
    render(
      <Button asChild variant="primary">
        <a href="/admin/productos/nuevo">Producto</a>
      </Button>,
    );

    const link = screen.getByRole('link', { name: 'Producto' });
    expect(link).toHaveAttribute('href', '/admin/productos/nuevo');
    expect(link).not.toHaveAttribute('type');
    expect(link.className).toContain('bg-primary');
  });

  it('given a segmented control, when an option is chosen, then it is pressed alone and the others are not', async () => {
    const onChange = vi.fn();
    render(<Periods onChange={onChange} />);
    const group = screen.getByRole('radiogroup', { name: 'Período' });
    expect(screen.getByRole('radio', { name: 'Este mes' })).toBeChecked();

    await userEvent.click(screen.getByRole('radio', { name: 'Hoy' }));

    expect(screen.getByRole('radio', { name: 'Hoy' })).toBeChecked();
    expect(screen.getByRole('radio', { name: 'Este mes' })).not.toBeChecked();
    expect(onChange).toHaveBeenCalledWith('today');
    expect(group).toHaveStyle({ '--segment': '0' });
  });

  it('given the chosen option of a segmented control, when it is clicked again, then it stays chosen', async () => {
    render(<Periods />);

    await userEvent.click(screen.getByRole('radio', { name: 'Este mes' }));

    expect(screen.getByRole('radio', { name: 'Este mes' })).toBeChecked();
  });

  it('given a switch with a label, when pressed, then it turns on and screen readers hear its state', async () => {
    render(
      <label>
        <Switch />
        Actualizar cada 5 minutos
      </label>,
    );
    const toggle = screen.getByRole('switch', { name: 'Actualizar cada 5 minutos' });
    expect(toggle).not.toBeChecked();

    await userEvent.click(toggle);

    expect(toggle).toBeChecked();
  });

  it('given a card, a badge and an empty state, when rendered, then the card is a region named by its title', () => {
    render(
      <Card aria-labelledby="title">
        <CardHeader>
          <CardTitle id="title">Últimos pedidos</CardTitle>
          <Badge tone="success">Entregado</Badge>
        </CardHeader>
        <CardContent>
          <EmptyState title="Todavía no hay pedidos.">Cuando alguien compre, lo vas a ver acá.</EmptyState>
        </CardContent>
      </Card>,
    );

    expect(screen.getByRole('region', { name: 'Últimos pedidos' })).toBeInTheDocument();
    expect(screen.getByText('Entregado').className).toContain('text-success');
    expect(screen.getByText('Todavía no hay pedidos.')).toBeInTheDocument();
  });
});
