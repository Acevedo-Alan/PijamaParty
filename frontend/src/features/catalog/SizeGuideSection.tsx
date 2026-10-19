import { Collapsible } from '../../components/Collapsible.tsx';
import { es } from '../../i18n/es.ts';
import type { SizeGuide } from './productDetailApi.ts';

/**
 * RNF-31.5: the size guide of the product's category, collapsed right below the size picker. The table scrolls
 * sideways inside its box when it does not fit, never the page.
 */
export function SizeGuideSection({ guide }: { guide: SizeGuide }) {
  return (
    <Collapsible title={es.product.sizeGuide} className="size-guide">
      <div className="size-guide-table" role="region" aria-label={es.product.sizeGuideTable} tabIndex={0}>
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
    </Collapsible>
  );
}
