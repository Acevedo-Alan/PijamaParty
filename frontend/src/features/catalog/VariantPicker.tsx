import { es } from '../../i18n/es.ts';
import type { DetailVariant } from './productDetailApi.ts';
import { isColorSoldOut, isSizeAvailable, type Selection } from './variantSelection.ts';

interface OptionProps {
  name: string;
  value: string;
  checked: boolean;
  disabled?: boolean;
  soldOut: boolean;
  onPick: () => void;
}

function Option({ name, value, checked, disabled = false, soldOut, onPick }: OptionProps) {
  return (
    <label className={soldOut ? 'chip sold-out' : 'chip'}>
      <input
        type="radio"
        name={name}
        value={value}
        checked={checked}
        disabled={disabled}
        onChange={onPick}
        {...(soldOut ? { 'aria-label': es.product.soldOutOption(value) } : {})}
      />
      {value}
    </label>
  );
}

interface VariantPickerProps {
  sizes: string[];
  colors: string[];
  variants: DetailVariant[];
  selection: Selection;
  onPickSize: (size: string) => void;
  onPickColor: (color: string) => void;
}

/**
 * Color and size as two radio groups (UC-03). A size without stock in the picked color is disabled. A color stays
 * selectable to see its photos, but shows as sold out when it has no stock in the picked size.
 */
export function VariantPicker({ sizes, colors, variants, selection, onPickSize, onPickColor }: VariantPickerProps) {
  return (
    <>
      <fieldset className="option-group">
        <legend>{es.product.color}</legend>
        <div className="chips">
          {colors.map((color) => (
            <Option
              key={color}
              name="color"
              value={color}
              checked={selection.color === color}
              soldOut={isColorSoldOut(variants, color, selection.size)}
              onPick={() => {
                onPickColor(color);
              }}
            />
          ))}
        </div>
      </fieldset>
      <fieldset className="option-group">
        <legend>{es.product.size}</legend>
        <div className="chips">
          {sizes.map((size) => {
            const available = isSizeAvailable(variants, size, selection.color);
            return (
              <Option
                key={size}
                name="size"
                value={size}
                checked={selection.size === size}
                disabled={!available}
                soldOut={!available}
                onPick={() => {
                  onPickSize(size);
                }}
              />
            );
          })}
        </div>
      </fieldset>
    </>
  );
}
