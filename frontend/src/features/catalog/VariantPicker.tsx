import { es } from '../../i18n/es.ts';
import { colorTone } from './colorTones.ts';
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

/** A size as a square button (E7); the real radio stays in the page for keyboards and screen readers. */
function SizeOption({ name, value, checked, disabled = false, soldOut, onPick }: OptionProps) {
  return (
    <label className={soldOut ? 'chip size-option sold-out' : 'chip size-option'}>
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

/** A color as a swatch (D-04), or by its name when the map has no tone for it. */
function ColorOption({ name, value, checked, soldOut, onPick }: OptionProps) {
  const tone = colorTone(value);
  const classes = ['swatch-option', soldOut ? 'sold-out' : '', tone === undefined ? 'named' : ''].filter(Boolean);
  return (
    <label className={classes.join(' ')} title={value}>
      <input
        type="radio"
        name={name}
        value={value}
        checked={checked}
        onChange={onPick}
        {...(soldOut ? { 'aria-label': es.product.soldOutOption(value) } : {})}
      />
      {tone === undefined ? (
        value
      ) : (
        <>
          <span
            className={tone.outlined ? 'swatch outlined' : 'swatch'}
            style={{ background: tone.background }}
            aria-hidden="true"
          />
          <span className="visually-hidden">{value}</span>
        </>
      )}
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
 * Color and size as two radio groups (UC-03): colors as swatches with the picked color's name, sizes as square
 * buttons (E7). A size without stock in the picked color is disabled. A color stays selectable to see its photos,
 * but shows as sold out when it has no stock in the picked size.
 */
export function VariantPicker({ sizes, colors, variants, selection, onPickSize, onPickColor }: VariantPickerProps) {
  return (
    <>
      <fieldset className="option-group">
        <legend>
          {es.product.color}
          {/* The checked radio already tells screen readers; this is for the eyes, as swatches carry no text. */}
          {selection.color !== null && (
            <span className="option-picked" aria-hidden="true">
              : {selection.color}
            </span>
          )}
        </legend>
        <div className="swatches">
          {colors.map((color) => (
            <ColorOption
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
              <SizeOption
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
