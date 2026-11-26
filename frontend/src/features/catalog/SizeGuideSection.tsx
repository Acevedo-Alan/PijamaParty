import { useId, useRef, useState } from 'react';
import { PanelDialog } from '../../components/motion/PanelDialog.tsx';
import { es } from '../../i18n/es.ts';
import type { SizeGuide } from './productDetailApi.ts';
import './sizeGuide.css';

const IN_CENTIMETERS = / \(cm\)$/i;

/** The ruler of the "Guía de talles" link and of the note's callout. */
function Ruler() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="2.5" y="8" width="19" height="8" rx="1.5" />
      <path d="M6.5 8v3M10 8v4.5M13.5 8v3M17 8v4.5" />
    </svg>
  );
}

/**
 * T-E7-80: the guide as it reads best. With `sizes` (the product page), only the rows of those sizes, unless none of
 * them is in the guide; a column with no value in any shown row is left out; " (cm)" leaves the names for one line.
 */
export function readableGuide(guide: SizeGuide, sizes?: readonly string[]) {
  const wanted = new Set(sizes?.map((size) => size.toLowerCase()));
  const matching = guide.rows.filter((row) => wanted.has(row.size.toLowerCase()));
  const rows = matching.length > 0 ? matching : guide.rows;
  const kept = guide.columns.flatMap((column, index) =>
    rows.some((row) => (row.values[index] ?? '') !== '') ? [{ column, index }] : [],
  );
  return {
    columns: kept.map(({ column }) => column.replace(IN_CENTIMETERS, '')),
    rows: rows.map((row) => ({ size: row.size, values: kept.map(({ index }) => row.values[index] ?? '') })),
    inCentimeters: kept.some(({ column }) => IN_CENTIMETERS.test(column)),
  };
}

/**
 * RNF-31.5: a size guide's table (size first, then the columns) and its note, on the product page and the size guide
 * page (T-E7-29). The table scrolls sideways inside its box when it does not fit, never the page; `label` names it.
 * T-E7-80: `sizes` keeps the product's rows; the unit is one line above the table and the note a callout.
 */
export function SizeGuideTable({ guide, label, sizes }: { guide: SizeGuide; label: string; sizes?: readonly string[] }) {
  const { columns, rows, inCentimeters } = readableGuide(guide, sizes);
  return (
    <>
      {inCentimeters && <p className="size-guide-unit">{es.product.allInCentimeters}</p>}
      <div className="size-guide-table" role="region" aria-label={label} tabIndex={0}>
        <table>
          <thead>
            <tr>
              <th scope="col">{es.product.size}</th>
              {columns.map((column) => (
                <th key={column} scope="col">
                  {column}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.size}>
                <th scope="row">{row.size}</th>
                {row.values.map((value, index) => (
                  <td key={columns[index] ?? index}>{value === '' ? es.product.noMeasure : value}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {guide.note !== null && (
        <p className="size-guide-note">
          <Ruler />
          <span>{guide.note}</span>
        </p>
      )}
    </>
  );
}

/**
 * RNF-31.5 (T-E7-36): "Guía de talles" beside the size label, opening the guide of the product's category in a modal
 * dialog that rises from the bottom. The focus returns to the link once the dialog has closed.
 */
export function SizeGuideLink({ guide, sizes }: { guide: SizeGuide; sizes: readonly string[] }) {
  const [open, setOpen] = useState(false);
  const titleId = useId();
  const link = useRef<HTMLButtonElement>(null);
  const close = () => {
    setOpen(false);
  };
  return (
    <>
      <button
        ref={link}
        type="button"
        className="size-guide-link"
        aria-haspopup="dialog"
        onClick={() => {
          setOpen(true);
        }}
      >
        <Ruler />
        {es.product.sizeGuide}
      </button>
      <PanelDialog
        open={open}
        from="bottom"
        className="size-guide-dialog"
        labelledBy={titleId}
        onDismiss={close}
        onClosed={() => {
          link.current?.focus();
        }}
      >
        <div className="size-guide-dialog-head">
          <h2 id={titleId}>{es.product.sizeGuide}</h2>
          <button type="button" className="icon-btn" aria-label={es.product.closeSizeGuide} onClick={close}>
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M6 6l12 12M18 6 6 18" />
            </svg>
          </button>
        </div>
        <SizeGuideTable guide={guide} label={es.product.sizeGuideTable} sizes={sizes} />
      </PanelDialog>
    </>
  );
}
