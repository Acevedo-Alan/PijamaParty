import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Field, FieldInput, FieldLabel } from './Field.tsx';

describe('field (T-E8-18, D-E8-12)', () => {
  it('given a label and a hint, when rendered, then the control is named by the label and described by the hint', () => {
    render(
      <Field hint="Con código de país y de área.">
        <FieldLabel>WhatsApp</FieldLabel>
        <FieldInput type="tel" />
      </Field>,
    );

    const input = screen.getByLabelText('WhatsApp');
    expect(input).toHaveAccessibleDescription('Con código de país y de área.');
    expect(input).toHaveAttribute('aria-invalid', 'false');
    expect(input).toHaveAttribute('type', 'tel');
  });

  it('given an error, when rendered, then the control is invalid and described by the hint and then the error', () => {
    render(
      <Field hint="Hasta 80 caracteres." error="En una sola línea.">
        <FieldLabel>Anuncio</FieldLabel>
        <FieldInput />
      </Field>,
    );

    const input = screen.getByLabelText('Anuncio');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('Hasta 80 caracteres. En una sola línea.');
  });

  it('given two fields, when rendered, then each label names its own control', () => {
    render(
      <>
        <Field>
          <FieldLabel>Email</FieldLabel>
          <FieldInput />
        </Field>
        <Field>
          <FieldLabel>Dirección</FieldLabel>
          <FieldInput />
        </Field>
      </>,
    );

    expect(screen.getByLabelText('Email')).not.toBe(screen.getByLabelText('Dirección'));
    expect(screen.getByLabelText('Email')).not.toHaveAttribute('aria-describedby');
  });

  it('given a label outside a field, when rendered, then it fails at once', () => {
    expect(() => render(<FieldLabel>Suelta</FieldLabel>)).toThrow('FieldLabel must be inside a Field');
  });
});
