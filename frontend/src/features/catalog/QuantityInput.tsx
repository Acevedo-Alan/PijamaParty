import { useState } from 'react';
import { es } from '../../i18n/es.ts';

interface QuantityInputProps {
  value: number;
  /** The available stock of the picked variant; 0 or less leaves the control disabled at 1. */
  max: number;
  onChange: (quantity: number) => void;
}

/**
 * The units to add (E7), from 1 to the available stock of the picked variant. A typed value is kept while it is
 * being typed and settles inside the range when the field is left.
 */
export function QuantityInput({ value, max, onChange }: QuantityInputProps) {
  const [typed, setTyped] = useState<string | null>(null);
  const disabled = max < 1;
  const settle = (quantity: number) => Math.min(Math.max(1, quantity), Math.max(1, max));

  return (
    <div className="quantity-control" role="group" aria-label={es.product.quantity}>
      <button
        type="button"
        aria-label={es.product.lessUnits}
        disabled={disabled || value <= 1}
        onClick={() => {
          onChange(settle(value - 1));
        }}
      >
        −
      </button>
      <input
        type="number"
        inputMode="numeric"
        min={1}
        max={Math.max(1, max)}
        aria-label={es.product.quantity}
        disabled={disabled}
        value={typed ?? String(value)}
        onChange={(event) => {
          setTyped(event.target.value);
          const quantity = Number.parseInt(event.target.value, 10);
          if (!Number.isNaN(quantity)) {
            onChange(settle(quantity));
          }
        }}
        onBlur={() => {
          setTyped(null);
        }}
      />
      <button
        type="button"
        aria-label={es.product.moreUnits}
        disabled={disabled || value >= max}
        onClick={() => {
          onChange(settle(value + 1));
        }}
      >
        +
      </button>
    </div>
  );
}
