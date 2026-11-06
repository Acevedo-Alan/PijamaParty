import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type SubmitEvent } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router';
import { Button } from '../../components/ui/Button.tsx';
import { Field, FieldInput, FieldLabel, FieldSelect } from '../../components/ui/Field.tsx';
import { FilterBar, FilterBarActions } from '../../components/ui/FilterBar.tsx';
import { es } from '../../i18n/es.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatDateTime } from '../../i18n/formatDate.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { WORKLOAD_COUNTS_KEY } from './dashboardApi.ts';
import { listReturn } from './listReturn.ts';
import { fetchOrders, ORDER_STATUSES, type OrderListFilter } from './ordersApi.ts';

const messages = esAdmin.orderList;
const STATUS_PARAM = 'estado';
const SEARCH_PARAM = 'buscar';
const FROM_PARAM = 'desde';
const TO_PARAM = 'hasta';
const DATE = /^\d{4}-\d{2}-\d{2}$/;
/** The order number of UC-29, as the server reads it: 1001 or #1001. */
const ORDER_NUMBER = /^#?\d{1,18}$/;
const MAX_SEARCH = 100;

/** The filters in the address, so they survive opening an order and coming back. */
function filterOf(params: URLSearchParams): OrderListFilter {
  const status = params.get(STATUS_PARAM);
  const date = (name: string) => {
    const value = params.get(name);
    return value !== null && DATE.test(value) ? value : null;
  };
  const q = params.get(SEARCH_PARAM)?.trim() ?? '';
  return {
    status: ORDER_STATUSES.find((value) => value === status) ?? null,
    q: q === '' ? null : q,
    from: date(FROM_PARAM),
    to: date(TO_PARAM),
  };
}

/** UC-18: an order number, or 2 to 100 characters of the buyer's name or email; blank is no search. */
function validSearch(typed: string): boolean {
  const text = typed.trim();
  // Code points, as the server counts them.
  const length = Array.from(text).length;
  return text === '' || ORDER_NUMBER.test(text) || (length >= 2 && length <= MAX_SEARCH);
}

/**
 * UC-18 at /admin/pedidos: the orders, newest first, 50 at a time, filtered by status, by a search (an order number or
 * the buyer's name or email) and by store dates (T-E8-06), all kept in the address.
 */
export function OrdersPage() {
  const [params, setParams] = useSearchParams();
  // The pages this list opens return to it with the same filters.
  const opened = listReturn(useLocation().search);
  const filter = filterOf(params);
  const [search, setSearch] = useState(filter.q ?? '');
  const [searchInvalid, setSearchInvalid] = useState(false);
  const searchField = useRef<HTMLInputElement>(null);
  const invalidPeriod = filter.from !== null && filter.to !== null && filter.from > filter.to;
  const filtered = filter.q !== null || filter.from !== null || filter.to !== null;
  const queryClient = useQueryClient();
  const orders = useInfiniteQuery({
    queryKey: ['admin', 'orders', 'list', filter.status, filter.q, filter.from, filter.to],
    queryFn: ({ pageParam, signal }) => fetchOrders(filter, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last, _pages, page) => (last.hasMore ? page + 1 : undefined),
    enabled: !invalidPeriod,
  });
  const items = orders.data?.pages.flatMap((page) => page.items) ?? [];

  // T-E8-05: the menu's count of paid orders is read again whenever the list loads from its start, alongside it
  // (T-E8-23: here and not inside the query function, which also runs on every retry). A count already on its way is
  // kept, not sent again. "Show more" adds a page and does not touch the count.
  const loadedFromStart = orders.data?.pages.length === 1;
  const loadedAt = orders.dataUpdatedAt;
  useEffect(() => {
    if (loadedFromStart) {
      void queryClient.invalidateQueries({ queryKey: WORKLOAD_COUNTS_KEY }, { cancelRefetch: false });
    }
  }, [queryClient, loadedFromStart, loadedAt]);

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
    if (!validSearch(search)) {
      setSearchInvalid(true);
      // The error is read where the focus lands, as in "Clientes" and "Stock".
      searchField.current?.focus();
      return;
    }
    setSearchInvalid(false);
    change(SEARCH_PARAM, search.trim());
  }

  const empty = filtered ? messages.emptyFiltered : filter.status === null ? messages.empty : messages.emptyInStatus;

  return (
    <>
      <title>{`${messages.title} | ${esAdmin.title}`}</title>
      <div className="admin-title-row">
        <h1>{messages.title}</h1>
      </div>
      <FilterBar aria-label={messages.filters} onSubmit={submit}>
        <Field
          className="min-[640px]:min-w-64 min-[640px]:flex-1"
          hint={messages.searchHint}
          error={searchInvalid ? messages.searchInvalid : undefined}
        >
          <FieldLabel>{messages.search}</FieldLabel>
          <FieldInput
            ref={searchField}
            name={SEARCH_PARAM}
            type="search"
            value={search}
            maxLength={MAX_SEARCH}
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
            name={STATUS_PARAM}
            value={filter.status ?? ''}
            onChange={(event) => {
              change(STATUS_PARAM, event.target.value);
            }}
          >
            <option value="">{messages.all}</option>
            {ORDER_STATUSES.map((value) => (
              <option key={value} value={value}>
                {es.tracking.statuses[value]}
              </option>
            ))}
          </FieldSelect>
        </Field>
        <Field error={invalidPeriod ? messages.invalidPeriod : undefined}>
          <FieldLabel>{messages.from}</FieldLabel>
          <FieldInput
            name={FROM_PARAM}
            type="date"
            autoComplete="off"
            value={filter.from ?? ''}
            onChange={(event) => {
              change(FROM_PARAM, event.target.value);
            }}
          />
        </Field>
        <Field>
          <FieldLabel>{messages.to}</FieldLabel>
          <FieldInput
            name={TO_PARAM}
            type="date"
            autoComplete="off"
            value={filter.to ?? ''}
            onChange={(event) => {
              change(TO_PARAM, event.target.value);
            }}
          />
        </Field>
        <FilterBarActions>
          <Button type="submit" variant="primary">
            {messages.apply}
          </Button>
        </FilterBarActions>
      </FilterBar>
      {orders.isPending && !invalidPeriod && <p role="status">{esAdmin.loading}</p>}
      {orders.isError && items.length === 0 && (
        <div role="alert">
          <p>{esAdmin.loadError}</p>
          <button type="button" className="btn" onClick={() => void orders.refetch()}>
            {esAdmin.retry}
          </button>
        </div>
      )}
      {orders.isSuccess && items.length === 0 && <p>{empty}</p>}
      {items.length > 0 && (
        <ul className="admin-list">
          {items.map((order) => (
            <li key={order.id} className="admin-card">
              <div className="admin-card-head">
                <Link to={`/admin/pedidos/${order.id}`} state={opened} className="admin-card-title">
                  {messages.number(order.number)}
                </Link>
                <span className="admin-badge">{es.tracking.statuses[order.status]}</span>
                <span className="order-total">{formatMoney(order.totalCents)}</span>
              </div>
              <p className="admin-card-meta">
                {messages.buyer(order.buyerName, order.city, es.provinces[order.province])} ·{' '}
                {formatDateTime(order.createdAt)}
              </p>
            </li>
          ))}
        </ul>
      )}
      {orders.isFetchNextPageError && <p role="alert">{esAdmin.loadError}</p>}
      {orders.hasNextPage && (
        <button
          type="button"
          className="btn btn-secondary show-more-btn"
          disabled={orders.isFetchingNextPage}
          onClick={() => void orders.fetchNextPage()}
        >
          {orders.isFetchingNextPage ? esAdmin.loading : esAdmin.showMore}
        </button>
      )}
    </>
  );
}
