import { useId, useState, type SubmitEvent } from 'react';
import { PanelDialog } from '../../components/motion/PanelDialog.tsx';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import type { FilterOptions } from './catalogApi.ts';
import { CENTS_PER_PESO, countFilters, NO_FILTERS, WHOLE_PESOS, type ProductFilters } from './listingCriteria.ts';
import { useFilterOptions } from './useFilterOptions.ts';

/** The panel's choices before "Show results": prices stay as typed, in whole pesos. */
interface Draft extends Omit<ProductFilters, 'minPriceCents' | 'maxPriceCents'> {
  minPesos: string;
  maxPesos: string;
}

type PriceProblem = 'invalid' | 'range' | null;

function toDraft({ minPriceCents, maxPriceCents, ...values }: ProductFilters): Draft {
  return {
    ...values,
    minPesos: minPriceCents === undefined ? '' : String(Math.floor(minPriceCents / CENTS_PER_PESO)),
    maxPesos: maxPriceCents === undefined ? '' : String(Math.floor(maxPriceCents / CENTS_PER_PESO)),
  };
}

function toCents(pesos: string): number | undefined | 'invalid' {
  if (pesos === '') {
    return undefined;
  }
  return WHOLE_PESOS.test(pesos) ? Number(pesos) * CENTS_PER_PESO : 'invalid';
}

/** The filters a draft applies, or why its price range cannot be sent (UC-02: a minimum above the maximum). */
function fromDraft({ minPesos, maxPesos, ...values }: Draft): ProductFilters | NonNullable<PriceProblem> {
  const min = toCents(minPesos);
  const max = toCents(maxPesos);
  if (min === 'invalid' || max === 'invalid') {
    return 'invalid';
  }
  if (min !== undefined && max !== undefined && min > max) {
    return 'range';
  }
  return {
    ...values,
    ...(min === undefined ? {} : { minPriceCents: min }),
    ...(max === undefined ? {} : { maxPriceCents: max }),
  };
}

interface CheckboxGroupProps<T extends string> {
  legend: string;
  options: readonly T[];
  selected: readonly T[];
  label?: (value: T) => string;
  onChange: (selected: T[]) => void;
}

/** Several values of one filter combine with OR. A value applied from a shared link stays visible to uncheck it. */
function CheckboxGroup<T extends string>({ legend, options, selected, label, onChange }: CheckboxGroupProps<T>) {
  const values = [...options, ...selected.filter((value) => !options.includes(value))];
  if (values.length === 0) {
    return null;
  }
  return (
    <fieldset className="filter-group">
      <legend>{legend}</legend>
      <div className="chips">
        {values.map((value) => (
          <label key={value} className="chip">
            <input
              type="checkbox"
              checked={selected.includes(value)}
              onChange={(event) => {
                onChange(event.target.checked ? [...selected, value] : selected.filter((other) => other !== value));
              }}
            />
            <span>{label === undefined ? value : label(value)}</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}

interface PriceGroupProps {
  range: FilterOptions['priceRange'];
  draft: Draft;
  problem: PriceProblem;
  onChange: (prices: Pick<Draft, 'minPesos' | 'maxPesos'>) => void;
}

function PriceGroup({ range, draft, problem, onChange }: PriceGroupProps) {
  const id = useId();
  if (range === null && draft.minPesos === '' && draft.maxPesos === '') {
    return null;
  }
  const errorId = `${id}-error`;
  const invalid = problem !== null;
  return (
    <fieldset className="filter-group">
      <legend>{es.filters.price}</legend>
      {range !== null && (
        <p className="filter-hint">{es.filters.priceHint(formatMoney(range.minCents), formatMoney(range.maxCents))}</p>
      )}
      <div className="price-fields">
        <label>
          <span>{es.filters.minPrice}</span>
          <input
            type="number"
            inputMode="numeric"
            min={0}
            step={1}
            value={draft.minPesos}
            aria-invalid={invalid}
            aria-describedby={invalid ? errorId : undefined}
            onChange={(event) => {
              onChange({ minPesos: event.target.value, maxPesos: draft.maxPesos });
            }}
          />
        </label>
        <label>
          <span>{es.filters.maxPrice}</span>
          <input
            type="number"
            inputMode="numeric"
            min={0}
            step={1}
            value={draft.maxPesos}
            aria-invalid={invalid}
            aria-describedby={invalid ? errorId : undefined}
            onChange={(event) => {
              onChange({ minPesos: draft.minPesos, maxPesos: event.target.value });
            }}
          />
        </label>
      </div>
      {invalid && (
        <p id={errorId} className="filter-error" role="alert">
          {problem === 'range' ? es.filters.priceRangeError : es.filters.priceInvalid}
        </p>
      )}
    </fieldset>
  );
}

interface FilterPanelProps {
  category: string | undefined;
  filters: ProductFilters;
  onApply: (filters: ProductFilters) => void;
}

/**
 * The "Filter" button and its sliding panel (UC-02). A modal dialog keeps the focus inside, closes with Escape and
 * hides the page from screen readers. The choices apply with "Show results"; closing discards them.
 */
export function FilterPanel({ category, filters, onApply }: FilterPanelProps) {
  const [isOpen, setOpen] = useState(false);
  const titleId = useId();
  const options = useFilterOptions(category);
  const [draft, setDraft] = useState(() => toDraft(filters));
  const [problem, setProblem] = useState<PriceProblem>(null);

  // An unknown or inactive category has nothing to filter; the grid shows its message.
  if (options.data?.status === 'unknownCategory') {
    return null;
  }

  const count = countFilters(filters);

  function open() {
    setDraft(toDraft(filters));
    setProblem(null);
    setOpen(true);
  }

  function close() {
    setOpen(false);
  }

  function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const applied = fromDraft(draft);
    if (typeof applied === 'string') {
      setProblem(applied);
      return;
    }
    onApply(applied);
    close();
  }

  function change(next: Partial<Draft>) {
    setDraft({ ...draft, ...next });
    setProblem(null);
  }

  return (
    <>
      <button type="button" className="tool-btn" aria-haspopup="dialog" onClick={open}>
        {count === 0 ? es.filters.open : es.filters.openWithCount(count)}
      </button>
      <PanelDialog open={isOpen} from="left" className="filters-panel" labelledBy={titleId} onDismiss={close}>
        <form className="filters-form" noValidate onSubmit={submit}>
          <header className="filters-head">
            <h2 id={titleId}>{es.filters.title}</h2>
            <button type="button" className="icon-btn" aria-label={es.filters.close} onClick={close}>
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M6 6l12 12M18 6L6 18" />
              </svg>
            </button>
          </header>
          <div className="filters-body">
            {options.isPending && <p role="status">{es.filters.loading}</p>}
            {options.isError && (
              <div role="alert">
                <p>{es.filters.loadError}</p>
                <button type="button" className="link-btn" onClick={() => void options.refetch()}>
                  {es.filters.retry}
                </button>
              </div>
            )}
            {options.data?.status === 'found' && (
              <FilterGroups options={options.data.options} draft={draft} problem={problem} onChange={change} />
            )}
          </div>
          <footer className="filters-foot">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => {
                change(toDraft(NO_FILTERS));
              }}
            >
              {es.filters.clear}
            </button>
            <button type="submit" className="btn">
              {es.filters.apply}
            </button>
          </footer>
        </form>
      </PanelDialog>
    </>
  );
}

interface FilterGroupsProps {
  options: FilterOptions;
  draft: Draft;
  problem: PriceProblem;
  onChange: (next: Partial<Draft>) => void;
}

/** The groups in the order of UC-02; a group without options is left out. */
function FilterGroups({ options, draft, problem, onChange }: FilterGroupsProps) {
  const lists = [options.sizes, options.colors, options.materials, options.seasons, options.audiences];
  if (options.priceRange === null && lists.every((values) => values.length === 0) && isEmpty(draft)) {
    return <p>{es.filters.none}</p>;
  }
  return (
    <>
      <PriceGroup range={options.priceRange} draft={draft} problem={problem} onChange={onChange} />
      <CheckboxGroup
        legend={es.filters.size}
        options={options.sizes}
        selected={draft.sizes}
        onChange={(sizes) => {
          onChange({ sizes });
        }}
      />
      <CheckboxGroup
        legend={es.filters.color}
        options={options.colors}
        selected={draft.colors}
        onChange={(colors) => {
          onChange({ colors });
        }}
      />
      <CheckboxGroup
        legend={es.filters.material}
        options={options.materials}
        selected={draft.materials}
        onChange={(materials) => {
          onChange({ materials });
        }}
      />
      <CheckboxGroup
        legend={es.filters.season}
        options={options.seasons}
        selected={draft.seasons}
        label={(season) => es.filters.seasons[season]}
        onChange={(seasons) => {
          onChange({ seasons });
        }}
      />
      <CheckboxGroup
        legend={es.filters.audience}
        options={options.audiences}
        selected={draft.audiences}
        label={(audience) => es.filters.audiences[audience]}
        onChange={(audiences) => {
          onChange({ audiences });
        }}
      />
    </>
  );
}

function isEmpty({ minPesos, maxPesos, ...values }: Draft): boolean {
  return minPesos === '' && maxPesos === '' && countFilters(values) === 0;
}
