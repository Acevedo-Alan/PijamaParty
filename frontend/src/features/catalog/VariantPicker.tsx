import { useId, type ReactNode, type Ref } from 'react';
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

/** E7: the pick reads at a glance on any tone; CSS shows it on the checked swatch only. */
const SWATCH_CHECK = (
  <svg className="swatch-check" viewBox="0 0 20 20" aria-hidden="true">
    <circle cx="10" cy="10" r="10" />
    <path d="M5.5 10.5l3 3 6-7" />
  </svg>
);

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
          {SWATCH_CHECK}
          <span className="visually-hidden">{value}</span>
        </>
      )}
    </label>
  );
}

/** T-E7-36: what a group still without a pick says below it, once the add button was pressed. */
function MissingPick({ id, shown, text }: { id: string; shown: boolean; text: string }) {
  return shown ? (
    <p id={id} className="option-missing">
      {text}
    </p>
  ) : null;
}

interface VariantPickerProps {
  sizes: string[];
  colors: string[];
  variants: DetailVariant[];
  selection: Selection;
  /** The add button was pressed without a size or a color: the groups still without a pick say so (T-E7-36). */
  showMissing: boolean;
  /** Beside the size label: the size guide link, when the category has one (T-E7-36). */
  sizeGuide?: ReactNode;
  /** The picker, so the page can move the focus to a group still without a pick. */
  ref?: Ref<HTMLDivElement>;
  onPickSize: (size: string) => void;
  onPickColor: (color: string) => void;
}

/**
 * Color and size as two radio groups (UC-03): colors as swatches with the picked color's name, sizes as square
 * buttons (E7). A size without stock in the picked color is disabled. A color stays selectable to see its photos,
 * but shows as sold out when it has no stock in the picked size.
 */
export function VariantPicker({
  sizes,
  colors,
  variants,
  selection,
  showMissing,
  sizeGuide,
  ref,
  onPickSize,
  onPickColor,
}: VariantPickerProps) {
  const sizeLabelId = useId();
  const sizeMissingId = useId();
  const colorMissingId = useId();
  const colorMissing = showMissing && selection.color === null;
  // Not while no size can be picked for the chosen color: the stock note says why (T-E7-36).
  const sizeMissing =
    showMissing && selection.size === null && sizes.some((size) => isSizeAvailable(variants, size, selection.color));
  return (
    <div className="variant-picker" ref={ref}>
      <fieldset
        className="option-group"
        data-group="color"
        {...(colorMissing ? { 'aria-describedby': colorMissingId } : {})}
      >
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
        <MissingPick id={colorMissingId} shown={colorMissing} text={es.product.pickColor} />
      </fieldset>
      {/* Named by its label alone, so the size guide link beside it is not read as part of the group's name. */}
      <fieldset
        className="option-group"
        data-group="size"
        aria-labelledby={sizeLabelId}
        {...(sizeMissing ? { 'aria-describedby': sizeMissingId } : {})}
      >
        <legend className="option-legend-row">
          <span id={sizeLabelId}>{es.product.size}</span>
          {sizeGuide}
        </legend>
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
        <MissingPick id={sizeMissingId} shown={sizeMissing} text={es.product.pickSize} />
      </fieldset>
    </div>
  );
}
