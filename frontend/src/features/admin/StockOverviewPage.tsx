import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { memo, useId, useMemo, useRef, useState, type SubmitEvent } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router';
import { Button } from '../../components/ui/Button.tsx';
import { Field, FieldInput, FieldLabel, FieldSelect } from '../../components/ui/Field.tsx';
import { FilterBar, FilterBarActions } from '../../components/ui/FilterBar.tsx';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { parseQuantity } from './adminInput.ts';
import { newIdempotencyKey } from './adminSession.ts';
import { Feedback, type Notice } from './Feedback.tsx';
import { listReturn, STOCK_LIST, type ListReturn } from './listReturn.ts';
import { isUuid, MAX_PRODUCT_SEARCH, validProductSearch } from './productSearch.ts';
import {
  adjustStock,
  fetchStockOverview,
  STOCK_FILTERS,
  type AdjustAnswer,
  type StockListFilter,
  type StockRow,
} from './stockApi.ts';
import { useCategories } from './useCategories.ts';

const messages = esAdmin.stockOverview;
const FILTER_PARAM = 'filtro';
const SEARCH_PARAM = 'buscar';
const CATEGORY_PARAM = 'categoria';
/** Units as the back office writes them elsewhere, e.g. 1.000. */
const UNITS = new Intl.NumberFormat('es-AR');

/** The filters in the address, so they survive opening a variant's history and coming back. */
function filterOf(params: URLSearchParams): StockListFilter {
  const filter = params.get(FILTER_PARAM);
  const q = params.get(SEARCH_PARAM)?.trim() ?? '';
  const categoryId = params.get(CATEGORY_PARAM) ?? '';
  return {
    filter: STOCK_FILTERS.find((value) => value === filter) ?? 'ALL',
    q: q === '' || !validProductSearch(q) ? null : q,
    categoryId: isUuid(categoryId) ? categoryId : null,
  };
}

/** The answer of a refused adjustment, in the words of the variant's stock page (UC-07). */
function refusal(answer: Exclude<AdjustAnswer, { status: 'adjusted' }>): Notice {
  const stock = esAdmin.stock;
  switch (answer.status) {
    case 'outOfRange':
      return {
        kind: 'error',
        text:
          answer.problem === 'WOULD_GO_NEGATIVE'
            ? stock.wouldGoNegative(answer.onHand)
            : stock.wouldExceedMaximum(answer.onHand),
      };
    case 'belowReserved':
      return { kind: 'error', text: stock.wouldGoBelowReserved(answer.reserved, answer.onHand) };
    case 'invalid':
      return { kind: 'error', text: stock.problems[answer.problem] };
    case 'unknownVariant':
      return { kind: 'error', text: stock.variantNotFound };
  }
}

/** One attempt of a quick adjustment: its key goes again only with the same sign, quantity and note. */
interface Attempt {
  key: string;
  quantity: number;
  note: string;
}

/**
 * A row of the Stock screen with its quick adjustment (UC-07). After a saved adjustment the row shows the units of the
 * answer until the list is read again, and keeps its place.
 */
const StockRowItem = memo(function StockRowItem({ row, opened }: { row: StockRow; opened: ListReturn }) {
  const queryClient = useQueryClient();
  const quantityId = useId();
  const quantityErrorId = useId();
  const noteId = useId();
  const quantityField = useRef<HTMLInputElement>(null);
  const [quantity, setQuantity] = useState('1');
  const [quantityInvalid, setQuantityInvalid] = useState(false);
  const [note, setNote] = useState('');
  const [attempt, setAttempt] = useState<Attempt | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  // The button pressed while its adjustment is on its way.
  const [saving, setSaving] = useState<1 | -1 | null>(null);
  // The units saved from this row, for the row as the list read it; a new reading of the list replaces them.
  const [saved, setSaved] = useState<{ from: StockRow; onHand: number } | null>(null);
  const savedHere = saved !== null && saved.from === row ? saved : null;
  // The server's figures, but for a row saved here: its on hand from the answer, less the reserved units it shows.
  const onHand = savedHere === null ? row.onHand : savedHere.onHand;
  const available = savedHere === null ? row.available : savedHere.onHand - row.reserved;
  const variant = messages.variant(row.size, row.color);

  async function save(sign: 1 | -1) {
    const units = parseQuantity(quantity);
    if (units === null || units <= 0) {
      setQuantityInvalid(true);
      setNotice(null);
      quantityField.current?.focus();
      return;
    }
    setQuantityInvalid(false);
    const signed = sign * units;
    const trimmed = note.trim();
    const current =
      attempt !== null && attempt.quantity === signed && attempt.note === trimmed
        ? attempt
        : { key: newIdempotencyKey(), quantity: signed, note: trimmed };
    setAttempt(current);
    setSaving(sign);
    setNotice(null);
    try {
      const answer = await adjustStock(row.variantId, signed, trimmed, current.key);
      // Answered: the next press is another adjustment, with its own key.
      setAttempt(null);
      if (answer.status === 'adjusted') {
        setSaved({ from: row, onHand: answer.onHand });
        setNote('');
        setNotice({ kind: 'ok', text: esAdmin.stock.adjusted(answer.onHand) });
        void queryClient.invalidateQueries({ queryKey: ['admin', 'stock', row.variantId] });
      } else {
        setNotice(refusal(answer));
      }
    } catch {
      // The same key goes again with the next press of the same button.
      setNotice({ kind: 'error', text: messages.connectionLost });
    } finally {
      setSaving(null);
    }
  }

  return (
    <li className="admin-card stock-row">
      <div className="admin-card-head">
        <p className="admin-card-title">{row.productName}</p>
        <span className={row.published ? 'admin-badge published' : 'admin-badge'}>
          {row.published ? messages.published : messages.hidden}
        </span>
      </div>
      <p className="admin-card-meta">
        {variant} · {messages.sku} <span translate="no">{row.sku}</span>
      </p>
      <dl className="stock-figures">
        <div>
          <dt>{messages.onHand}</dt>
          <dd>{UNITS.format(onHand)}</dd>
        </div>
        <div>
          <dt>{messages.reserved}</dt>
          <dd>{UNITS.format(row.reserved)}</dd>
        </div>
        <div>
          <dt>{messages.available}</dt>
          <dd>{UNITS.format(available)}</dd>
        </div>
      </dl>
      <form
        className="stock-quick-adjust"
        aria-label={messages.adjust(row.productName, variant)}
        onSubmit={(event) => {
          event.preventDefault();
        }}
      >
        <div className="field">
          <label htmlFor={quantityId}>{messages.quantity}</label>
          <input
            ref={quantityField}
            id={quantityId}
            name="quantity"
            type="number"
            inputMode="numeric"
            autoComplete="off"
            min={1}
            step={1}
            value={quantity}
            aria-invalid={quantityInvalid || undefined}
            aria-describedby={quantityInvalid ? quantityErrorId : undefined}
            onChange={(event) => {
              setQuantity(event.target.value);
            }}
          />
          {quantityInvalid && (
            <p id={quantityErrorId} className="form-error">
              {messages.quantityInvalid}
            </p>
          )}
        </div>
        <div className="field">
          <label htmlFor={noteId}>{messages.note}</label>
          <input
            id={noteId}
            name="note"
            autoComplete="off"
            value={note}
            maxLength={200}
            onChange={(event) => {
              setNote(event.target.value);
            }}
          />
        </div>
        <div className="stock-quick-actions">
          <button
            type="button"
            className="btn btn-secondary"
            disabled={saving !== null}
            onClick={() => void save(-1)}
          >
            {saving === -1 ? esAdmin.stock.submitting : `− ${messages.subtract}`}
          </button>
          <button type="button" className="btn" disabled={saving !== null} onClick={() => void save(1)}>
            {saving === 1 ? esAdmin.stock.submitting : `+ ${messages.add}`}
          </button>
        </div>
        <Feedback notice={notice} />
      </form>
      <Link to={`/admin/productos/${row.productId}/variantes/${row.variantId}/stock`} state={opened}>
        {messages.history}
      </Link>
    </li>
  );
});

/**
 * UC-07 at /admin/stock (T-E8-09): every active variant of every product with its units on hand, reserved and
 * available, fewest available first, 50 at a time, filtered by stock level, search and category, all kept in the
 * address; each row adjusts its stock in place.
 */
export function StockOverviewPage() {
  const [params, setParams] = useSearchParams();
  // The variant's history returns here with the same filters.
  const { search: listSearch } = useLocation();
  const opened = useMemo(() => listReturn(listSearch, STOCK_LIST), [listSearch]);
  const filter = filterOf(params);
  const [search, setSearch] = useState(filter.q ?? '');
  const [searchInvalid, setSearchInvalid] = useState(false);
  const searchField = useRef<HTMLInputElement>(null);
  const filtered = filter.filter !== 'ALL' || filter.q !== null || filter.categoryId !== null;
  const categories = useCategories();
  const rows = useInfiniteQuery({
    queryKey: ['admin', 'stock-overview', filter.filter, filter.q, filter.categoryId],
    queryFn: ({ pageParam, signal }) => fetchStockOverview(filter, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last, _pages, page) => (last.hasMore ? page + 1 : undefined),
  });
  const items = rows.data?.pages.flatMap((page) => page.items) ?? [];
  const categoryNames = new Set(categories.data?.map((category) => category.id));
  const unknownCategory = filter.categoryId !== null && !categoryNames.has(filter.categoryId);

  function change(name: string, value: string) {
    const next = new URLSearchParams(params);
    if (value === '') {
      next.delete(name);
    } else {
      next.set(name, value);
    }
    setParams(next, { replace: true });
  }

  function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!validProductSearch(search)) {
      setSearchInvalid(true);
      searchField.current?.focus();
      return;
    }
    setSearchInvalid(false);
    change(SEARCH_PARAM, search.trim());
  }

  return (
    <>
      <title>{`${messages.title} | ${esAdmin.title}`}</title>
      <div className="admin-title-row">
        <h1>{messages.title}</h1>
      </div>
      <p className="admin-lead">{messages.lead}</p>
      <FilterBar aria-label={messages.filters} onSubmit={submit}>
        <Field
          className="min-[640px]:min-w-64 min-[640px]:flex-1"
          hint={messages.searchHint}
          error={searchInvalid ? messages.searchInvalid : undefined}
        >
          <FieldLabel>{messages.search}</FieldLabel>
          <FieldInput
            ref={searchField}
            type="search"
            value={search}
            maxLength={MAX_PRODUCT_SEARCH}
            autoComplete="off"
            spellCheck={false}
            onChange={(event) => {
              setSearch(event.target.value);
            }}
          />
        </Field>
        <Field>
          <FieldLabel>{messages.filter}</FieldLabel>
          <FieldSelect
            value={filter.filter === 'ALL' ? '' : filter.filter}
            onChange={(event) => {
              change(FILTER_PARAM, event.target.value);
            }}
          >
            <option value="">{messages.all}</option>
            {STOCK_FILTERS.map((value) => (
              <option key={value} value={value}>
                {messages.filterNames[value]}
              </option>
            ))}
          </FieldSelect>
        </Field>
        <Field>
          <FieldLabel>{messages.category}</FieldLabel>
          <FieldSelect
            value={filter.categoryId ?? ''}
            onChange={(event) => {
              change(CATEGORY_PARAM, event.target.value);
            }}
          >
            <option value="">{messages.allCategories}</option>
            {unknownCategory && filter.categoryId !== null && (
              <option value={filter.categoryId}>
                {categories.isPending ? esAdmin.loading : messages.unknownCategory}
              </option>
            )}
            {categories.data?.map((category) => (
              <option key={category.id} value={category.id}>
                {category.active ? category.name : esAdmin.productForm.inactiveCategory(category.name)}
              </option>
            ))}
          </FieldSelect>
        </Field>
        <FilterBarActions>
          <Button type="submit" variant="primary">
            {messages.apply}
          </Button>
        </FilterBarActions>
      </FilterBar>
      {rows.isPending && <p role="status">{esAdmin.loading}</p>}
      {rows.isError && items.length === 0 && (
        <div role="alert">
          <p>{esAdmin.loadError}</p>
          <button type="button" className="btn" onClick={() => void rows.refetch()}>
            {esAdmin.retry}
          </button>
        </div>
      )}
      {rows.isSuccess && items.length === 0 && <p>{filtered ? messages.emptyFiltered : messages.empty}</p>}
      {items.length > 0 && (
        <ul className="admin-list stock-rows">
          {items.map((row) => (
            <StockRowItem key={row.variantId} row={row} opened={opened} />
          ))}
        </ul>
      )}
      {/* A failed "Ver más" keeps the rows read so far and says why; the button tries again. */}
      {rows.isFetchNextPageError && <p role="alert">{esAdmin.loadError}</p>}
      {rows.hasNextPage && (
        <button
          type="button"
          className="btn btn-secondary show-more-btn"
          disabled={rows.isFetchingNextPage}
          onClick={() => void rows.fetchNextPage()}
        >
          {rows.isFetchingNextPage ? esAdmin.loading : esAdmin.showMore}
        </button>
      )}
    </>
  );
}
