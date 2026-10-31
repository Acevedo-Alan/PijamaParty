import { es } from '../../i18n/es.ts';
import { colorTone } from './colorTones.ts';

/** D-04 on the card: at most 3 colors, then "+N", leaving the row's right end to the sizes. */
const CARD_SWATCHES = 3;

/**
 * The colors of a product as small circles (D-04); a color the map does not know shows its name. Screen readers
 * hear one sentence with every color instead of the circles.
 */
export function ColorSwatches({ colors }: { colors: readonly string[] }) {
  if (colors.length === 0) {
    return null;
  }
  const shown = colors.slice(0, CARD_SWATCHES);
  const more = colors.length - shown.length;

  return (
    <p className="swatches-mini">
      <span className="visually-hidden">{es.catalog.colors(colors)}</span>
      {shown.map((color) => {
        const tone = colorTone(color);
        return tone === undefined ? (
          <span key={color} className="swatch-name" aria-hidden="true">
            {color}
          </span>
        ) : (
          <span
            key={color}
            className={tone.outlined ? 'dot outlined' : 'dot'}
            style={{ background: tone.background }}
            aria-hidden="true"
            data-testid="swatch"
            title={color}
          />
        );
      })}
      {more > 0 && (
        <span className="dot-more" aria-hidden="true">
          +{more}
        </span>
      )}
    </p>
  );
}
