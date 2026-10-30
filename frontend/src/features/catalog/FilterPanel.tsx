import { useEffect, useId, useRef, useState, type ReactNode, type SubmitEvent } from 'react';
import { PanelDialog } from '../../components/motion/PanelDialog.tsx';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { ActiveFilters, priceLabel } from './ActiveFilters.tsx';
import type { FilterOptions } from './catalogApi.ts';
import { chosen, FILTER_GROUPS, FilterBar, type FilterGroup } from './FilterBar.tsx';
import { groupSizes } from './sizeGroups.ts';
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

/** Which groups show their values; the others show only their header. */
interface Disclosure {
  expanded: ReadonlySet<FilterGroup>;
  onToggle: (group: FilterGroup) => void;
}

interface FilterSectionProps extends Disclosure {
  group: FilterGroup;
  legend: string;
  /** What is chosen in the group, beside its name: a collapsed group still says it. */
  summary: string | null;
  children: ReactNode;
}

/**
 * A group that opens and closes from its header (changes to UC-02), so a long catalog does not turn the panel into
 * one long list. The legend keeps naming the group's fields; the summary describes the header's button.
 */
function FilterSection({ group, legend, summary, expanded, onToggle, children }: FilterSectionProps) {
  const id = useId();
  const isExpanded = expanded.has(group);
  return (
    <fieldset className="filter-group" data-group={group}>
      <legend>
        <button
          type="button"
          className="filter-toggle"
          aria-expanded={isExpanded}
          aria-controls={`${id}-values`}
          aria-describedby={summary === null ? undefined : `${id}-summary`}
          onClick={() => {
            onToggle(group);
          }}
        >
          <span>{legend}</span>
          {summary !== null && (
            <span id={`${id}-summary`} className="filter-summary" aria-hidden="true">
              {summary}
            </span>
          )}
        </button>
      </legend>
      <div id={`${id}-values`} className="filter-values" hidden={!isExpanded}>
        {children}
      </div>
    </fieldset>
  );
}

interface ChipsProps<T extends string> {
  values: readonly T[];
  selected: readonly T[];
  label: (value: T) => string;
  onChange: (selected: T[]) => void;
}

function Chips<T extends string>({ values, selected, label, onChange }: ChipsProps<T>) {
  return (
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
          <span>{label(value)}</span>
        </label>
      ))}
    </div>
  );
}

/** The options, then the chosen values no longer offered: a value from a shared link stays visible to uncheck it. */
function withChosen<T extends string>(options: readonly T[], selected: readonly T[]): T[] {
  return [...options, ...selected.filter((value) => !options.includes(value))];
}

interface CheckboxGroupProps<T extends string> extends Disclosure {
  group: FilterGroup;
  legend: string;
  options: readonly T[];
  selected: readonly T[];
  label?: (value: T) => string;
  onChange: (selected: T[]) => void;
}

/** Several values of one filter combine with OR. */
function CheckboxGroup<T extends string>({
  group,
  legend,
  options,
  selected,
  label = (value) => value,
  onChange,
  ...disclosure
}: CheckboxGroupProps<T>) {
  const values = withChosen(options, selected);
  if (values.length === 0) {
    return null;
  }
  return (
    <FilterSection
      group={group}
      legend={legend}
      summary={selected.length === 0 ? null : es.filters.chosenSummary(selected.map(label))}
      {...disclosure}
    >
      <Chips values={values} selected={selected} label={label} onChange={onChange} />
    </FilterSection>
  );
}

interface SizeGroupProps extends Disclosure {
  options: readonly string[];
  selected: readonly string[];
  onChange: (selected: string[]) => void;
}

/**
 * The sizes apart by kind, each in natural order, with a heading per kind only when there are several. Each kind is a
 * group named by its heading, so a screen reader says "Números" before "2, checkbox".
 */
function SizeGroup({ options, selected, onChange, ...disclosure }: SizeGroupProps) {
  const id = useId();
  const groups = groupSizes(withChosen(options, selected));
  if (groups.length === 0) {
    return null;
  }
  const label = (size: string) => size;
  return (
    <FilterSection
      group="sizes"
      legend={es.filters.size}
      summary={selected.length === 0 ? null : es.filters.chosenSummary(selected)}
      {...disclosure}
    >
      {groups.length === 1 ? (
        <Chips values={groups[0]?.sizes ?? []} selected={selected} label={label} onChange={onChange} />
      ) : (
        groups.map(({ kind, sizes }) => (
          <div key={kind} className="size-kind" role="group" aria-labelledby={`${id}-${kind}`}>
            <p id={`${id}-${kind}`} className="size-kind-name">
              {es.filters.sizeKinds[kind]}
            </p>
            <Chips values={sizes} selected={selected} label={label} onChange={onChange} />
          </div>
        ))
      )}
    </FilterSection>
  );
}

interface PriceGroupProps extends Disclosure {
  range: FilterOptions['priceRange'];
  draft: Draft;
  problem: PriceProblem;
  onChange: (prices: Pick<Draft, 'minPesos' | 'maxPesos'>) => void;
}

function PriceGroup({ range, draft, problem, onChange, ...disclosure }: PriceGroupProps) {
  const id = useId();
  if (range === null && draft.minPesos === '' && draft.maxPesos === '') {
    return null;
  }
  const errorId = `${id}-error`;
  const invalid = problem !== null;
  const typed = fromDraft(draft);
  return (
    <FilterSection
      group="price"
      legend={es.filters.price}
      summary={typeof typed === 'string' ? null : priceLabel(typed)}
      {...disclosure}
    >
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
    </FilterSection>
  );
}

interface FilterPanelProps {
  category: string | undefined;
  filters: ProductFilters;
  onApply: (filters: ProductFilters) => void;
  /** Shown at the end of the bar's row: the number of matches (D-11). */
  count?: ReactNode;
}

/**
 * The bar of filter buttons and the sliding panel (UC-02). Each button opens the panel at its group. A modal dialog
 * keeps the focus inside, closes with Escape and hides the page from screen readers. The choices apply with "Show
 * results"; closing discards them.
 */
export function FilterPanel({ category, filters, onApply, count }: FilterPanelProps) {
  const [isOpen, setOpen] = useState(false);
  const titleId = useId();
  const dialog = useRef<HTMLDialogElement>(null);
  /** The group to focus once the panel shows it: at once, or when the options arrive. */
  const focusGroup = useRef<FilterGroup | null>(null);
  const options = useFilterOptions(category);
  const [draft, setDraft] = useState(() => toDraft(filters));
  const [problem, setProblem] = useState<PriceProblem>(null);
  const [expanded, setExpanded] = useState<ReadonlySet<FilterGroup>>(() => new Set());

  // After the panel's showModal, which focuses its first button: the focus moves to the group's first field.
  useEffect(() => {
    if (!isOpen || focusGroup.current === null) {
      return;
    }
    const field = dialog.current?.querySelector<HTMLElement>(`[data-group="${focusGroup.current}"] input`);
    if (field !== null && field !== undefined) {
      field.focus();
      focusGroup.current = null;
    }
  });

  // An unknown or inactive category has nothing to filter; the grid shows its message.
  if (options.data?.status === 'unknownCategory') {
    return null;
  }

  /** The group of the pressed button opens, and so does every group with an applied value. */
  function open(group: FilterGroup) {
    setDraft(toDraft(filters));
    setProblem(null);
    setExpanded(new Set([group, ...FILTER_GROUPS.filter((other) => chosen(other, filters) > 0)]));
    focusGroup.current = group;
    setOpen(true);
  }

  function toggle(group: FilterGroup) {
    setExpanded((previous) => {
      const next = new Set(previous);
      if (!next.delete(group)) {
        next.add(group);
      }
      return next;
    });
  }

  function close() {
    focusGroup.current = null;
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
      <FilterBar
        options={options.data?.status === 'found' ? options.data.options : undefined}
        filters={filters}
        onOpen={open}
        trailing={count}
      />
      <ActiveFilters filters={filters} onApply={onApply} />
      <PanelDialog
        open={isOpen}
        ref={dialog}
        from="left"
        className="filters-panel"
        labelledBy={titleId}
        onDismiss={close}
      >
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
              <FilterGroups
                options={options.data.options}
                draft={draft}
                problem={problem}
                onChange={change}
                expanded={expanded}
                onToggle={toggle}
              />
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

interface FilterGroupsProps extends Disclosure {
  options: FilterOptions;
  draft: Draft;
  problem: PriceProblem;
  onChange: (next: Partial<Draft>) => void;
}

/** The groups in the bar's order (changes to UC-02); a group without options is left out. */
function FilterGroups({ options, draft, problem, onChange, ...disclosure }: FilterGroupsProps) {
  const lists = [options.sizes, options.colors, options.materials, options.seasons, options.audiences];
  if (options.priceRange === null && lists.every((values) => values.length === 0) && isEmpty(draft)) {
    return <p>{es.filters.none}</p>;
  }
  return (
    <>
      <CheckboxGroup
        group="audiences"
        legend={es.filters.audience}
        options={options.audiences}
        selected={draft.audiences}
        label={(audience) => es.filters.audiences[audience]}
        onChange={(audiences) => {
          onChange({ audiences });
        }}
        {...disclosure}
      />
      <SizeGroup
        options={options.sizes}
        selected={draft.sizes}
        onChange={(sizes) => {
          onChange({ sizes });
        }}
        {...disclosure}
      />
      <CheckboxGroup
        group="colors"
        legend={es.filters.color}
        options={options.colors}
        selected={draft.colors}
        onChange={(colors) => {
          onChange({ colors });
        }}
        {...disclosure}
      />
      <CheckboxGroup
        group="materials"
        legend={es.filters.material}
        options={options.materials}
        selected={draft.materials}
        onChange={(materials) => {
          onChange({ materials });
        }}
        {...disclosure}
      />
      <CheckboxGroup
        group="seasons"
        legend={es.filters.season}
        options={options.seasons}
        selected={draft.seasons}
        label={(season) => es.filters.seasons[season]}
        onChange={(seasons) => {
          onChange({ seasons });
        }}
        {...disclosure}
      />
      <PriceGroup range={options.priceRange} draft={draft} problem={problem} onChange={onChange} {...disclosure} />
    </>
  );
}

function isEmpty({ minPesos, maxPesos, ...values }: Draft): boolean {
  return minPesos === '' && maxPesos === '' && countFilters(values) === 0;
}
