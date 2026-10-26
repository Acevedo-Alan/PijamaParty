import { Collapsible } from '../../components/Collapsible.tsx';
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

/** RNF-31.5: the size guide of the product's category, the first of the collapsed sections of the page (E7). */
export function SizeGuideSection({ guide }: { guide: SizeGuide }) {
  return (
    <Collapsible title={es.product.sizeGuide} className="pdp-section size-guide">
      <SizeGuideTable guide={guide} label={es.product.sizeGuideTable} />
    </Collapsible>
  );
}
