import { useInfiniteQuery } from '@tanstack/react-query';
import { useRef, useState, type SubmitEvent } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router';
import { Button } from '../../components/ui/Button.tsx';
import { Field, FieldInput, FieldLabel } from '../../components/ui/Field.tsx';
import { FilterBar, FilterBarActions } from '../../components/ui/FilterBar.tsx';
import { ListSkeleton } from '../../components/ui/Skeleton.tsx';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatDate } from '../../i18n/formatDate.ts';
import { fetchCustomers, type CustomerListFilter } from './customersApi.ts';
import { listReturn } from './listReturn.ts';

const messages = esAdmin.customerList;
const SEARCH_PARAM = 'buscar';
const FROM_PARAM = 'desde';
const TO_PARAM = 'hasta';
const DATE = /^\d{4}-\d{2}-\d{2}$/;
const MAX_SEARCH = 100;

/** UC-34: 2 to 100 characters of the name or email; blank is no search. */
function validSearch(typed: string): boolean {
  // Code points, as the server counts them.
  const length = Array.from(typed.trim()).length;
  return length === 0 || (length >= 2 && length <= MAX_SEARCH);
}

/** The filters in the address, so they survive leaving and coming back. A value the server would refuse is left out. */
function filterOf(params: URLSearchParams): CustomerListFilter {
  const date = (name: string) => {
    const value = params.get(name);
    return value !== null && DATE.test(value) ? value : null;
  };
  const q = params.get(SEARCH_PARAM)?.trim() ?? '';
  return { q: q === '' || !validSearch(q) ? null : q, from: date(FROM_PARAM), to: date(TO_PARAM) };
}

/**
 * UC-34 at /admin/clientes (T-E8-10): the registered customers, newest first, 50 at a time, each with its email,
 * registration date and number of orders, filtered by a search (name or email) and by registration dates, both kept
 * in the address. Each name opens the customer's profile (T-E8-11), whose back link returns to the same filters.
 */
export function CustomersPage() {
  const [params, setParams] = useSearchParams();
  const opened = listReturn(useLocation().search);
  const filter = filterOf(params);
  const [search, setSearch] = useState(filter.q ?? '');
  const [searchInvalid, setSearchInvalid] = useState(false);
  const searchField = useRef<HTMLInputElement>(null);
  const invalidPeriod = filter.from !== null && filter.to !== null && filter.from > filter.to;
  const filtered = filter.q !== null || filter.from !== null || filter.to !== null;
  const customers = useInfiniteQuery({
    queryKey: ['admin', 'customers', 'list', filter.q, filter.from, filter.to],
    queryFn: ({ pageParam, signal }) => fetchCustomers(filter, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last, _pages, page) => (last.hasMore ? page + 1 : undefined),
    enabled: !invalidPeriod,
  });
  const items = customers.data?.pages.flatMap((page) => page.items) ?? [];

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
      {customers.isPending && !invalidPeriod && <ListSkeleton />}
      {customers.isError && items.length === 0 && (
        <div role="alert">
          <p>{esAdmin.loadError}</p>
          <button type="button" className="btn" onClick={() => void customers.refetch()}>
            {esAdmin.retry}
          </button>
        </div>
      )}
      {customers.isSuccess && items.length === 0 && <p>{filtered ? messages.emptyFiltered : messages.empty}</p>}
      {items.length > 0 && (
        <ul className="admin-list customer-rows">
          {items.map((customer) => (
            <li key={customer.id} className="admin-card">
              <div className="admin-card-head">
                <Link to={`/admin/clientes/${customer.id}`} state={opened} className="admin-card-title">
                  {customer.name}
                </Link>
                <p className="admin-card-meta">
                  {customer.email} · {messages.registered(formatDate(customer.registeredAt))}
                </p>
                <span className="admin-badge">{messages.orders(customer.orders)}</span>
              </div>
            </li>
          ))}
        </ul>
      )}
      {customers.isFetchNextPageError && <p role="alert">{esAdmin.loadError}</p>}
      {customers.hasNextPage && (
        <button
          type="button"
          className="btn btn-secondary show-more-btn"
          disabled={customers.isFetchingNextPage}
          onClick={() => void customers.fetchNextPage()}
        >
          {customers.isFetchingNextPage ? esAdmin.loading : esAdmin.showMore}
        </button>
      )}
    </>
  );
}
