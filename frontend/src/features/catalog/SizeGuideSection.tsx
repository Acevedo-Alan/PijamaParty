import { useId, useRef, useState } from 'react';
import { PanelDialog } from '../../components/motion/PanelDialog.tsx';
import { es } from '../../i18n/es.ts';
import type { SizeGuide } from './productDetailApi.ts';
import './sizeGuide.css';

/**
 * RNF-31.5: a size guide's table (size first, then the columns) and its note, on the product page and the size guide
 * page (T-E7-29). The table scrolls sideways inside its box when it does not fit, never the page; `label` names it.
 */
export function SizeGuideTable({ guide, label }: { guide: SizeGuide; label: string }) {
  return (
    <>
      <div className="size-guide-table" role="region" aria-label={label} tabIndex={0}>
        <table>
          <thead>
            <tr>
              <th scope="col">{es.product.size}</th>
              {guide.columns.map((column) => (
                <th key={column} scope="col">
                  {column}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {guide.rows.map((row) => (
              <tr key={row.size}>
                <th scope="row">{row.size}</th>
                {row.values.map((value, index) => (
                  <td key={guide.columns[index] ?? index}>{value === '' ? es.product.noMeasure : value}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {guide.note !== null && <p className="size-guide-note">{guide.note}</p>}
    </>
  );
}

/**
 * RNF-31.5 (T-E7-36): "Guía de talles" beside the size label, opening the guide of the product's category in a modal
 * dialog that rises from the bottom. The focus returns to the link once the dialog has closed.
 */
export function SizeGuideLink({ guide }: { guide: SizeGuide }) {
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
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <rect x="2.5" y="8" width="19" height="8" rx="1.5" />
          <path d="M6.5 8v3M10 8v4.5M13.5 8v3M17 8v4.5" />
        </svg>
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
        <SizeGuideTable guide={guide} label={es.product.sizeGuideTable} />
      </PanelDialog>
    </>
  );
}
