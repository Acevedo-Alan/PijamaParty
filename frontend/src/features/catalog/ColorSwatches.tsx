import { es } from '../../i18n/es.ts';
import { colorTone, PRINT_TONE } from './colorTones.ts';

/** D-04 on the card: at most 3 colors, then "+N", leaving the row's right end to the sizes. */
const CARD_SWATCHES = 3;

/**
 * The colors of a product as small circles (D-04). Screen readers hear one sentence with every color instead of the
 * circles. T-E7-71: a name the map does not know is a design ("Snoopy en su casita"), too long for the card's one
 * line: when any color is one, the product shows a single print circle and, with more than one color, "N diseños".
 */
export function ColorSwatches({ colors }: { colors: readonly string[] }) {
  if (colors.length === 0) {
    return null;
  }
  const tones = colors.map(colorTone);
  const designs = tones.some((tone) => tone === undefined);
  const shown = designs ? [] : colors.slice(0, CARD_SWATCHES);
  const more = colors.length - shown.length;

  return (
    <p className="swatches-mini">
      <span className="visually-hidden">{es.catalog.colors(colors)}</span>
      {designs ? (
        <>
          <span
            className="dot outlined"
            style={{ background: PRINT_TONE.background }}
            aria-hidden="true"
            data-testid="swatch"
          />
          {colors.length > 1 && (
            <span className="swatch-count" aria-hidden="true">
              {es.catalog.designs(colors.length)}
            </span>
          )}
        </>
      ) : (
        shown.map((color, index) => {
          const tone = tones[index];
          return (
            tone !== undefined && (
              <span
                key={color}
                className={tone.outlined ? 'dot outlined' : 'dot'}
                style={{ background: tone.background }}
                aria-hidden="true"
                data-testid="swatch"
                title={color}
              />
            )
          );
        })
      )}
      {!designs && more > 0 && (
        <span className="dot-more" aria-hidden="true">
          +{more}
        </span>
      )}
    </p>
  );
}
