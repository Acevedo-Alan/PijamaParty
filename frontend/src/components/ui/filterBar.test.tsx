import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Button } from './Button.tsx';
import { Field, FieldInput, FieldLabel, FieldSelect } from './Field.tsx';
import { FilterBar, FilterBarActions } from './FilterBar.tsx';

describe('filter bar (T-E8-18, D-E8-12)', () => {
  it('given fields and a button, when the button is pressed, then the bar is a named search that submits once', async () => {
    const onSubmit = vi.fn((event: { preventDefault: () => void }) => {
      event.preventDefault();
    });
    render(
      <FilterBar aria-label="Buscar pedidos" onSubmit={onSubmit}>
        <Field>
          <FieldLabel>Buscar</FieldLabel>
          <FieldInput type="search" />
        </Field>
        <Field>
          <FieldLabel>Estado</FieldLabel>
          <FieldSelect defaultValue="">
            <option value="">Todos</option>
            <option value="PAID">Pagado</option>
          </FieldSelect>
        </Field>
        <FilterBarActions>
          <Button type="submit">Buscar</Button>
        </FilterBarActions>
      </FilterBar>,
    );

    expect(screen.getByRole('search', { name: 'Buscar pedidos' })).toBeInTheDocument();
    expect(screen.getByLabelText('Estado')).toHaveValue('');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));

    expect(onSubmit).toHaveBeenCalledTimes(1);
  });
});
