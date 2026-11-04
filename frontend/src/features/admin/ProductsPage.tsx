import { useInfiniteQuery } from '@tanstack/react-query';
import { useRef, useState, type SubmitEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { Button } from '../../components/ui/Button.tsx';
import { Field, FieldInput, FieldLabel, FieldSelect } from '../../components/ui/Field.tsx';
import { FilterBar, FilterBarActions } from '../../components/ui/FilterBar.tsx';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { fetchProductSummaries, PRODUCT_STATES, PRODUCT_STOCKS, type ProductListFilter } from './productsApi.ts';
import { useCategories } from './useCategories.ts';

const messages = esAdmin.productList;
const SEARCH_PARAM = 'buscar';
const CATEGORY_PARAM = 'categoria';
const STATE_PARAM = 'estado';
const STOCK_PARAM = 'stock';
const MAX_SEARCH = 100;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * The filters in the address, so they survive opening a product and coming back. A search or category the server would
 * refuse is left out, as an unknown state or stock is.
 */
function filterOf(params: URLSearchParams): ProductListFilter {
  const state = params.get(STATE_PARAM);
  const stock = params.get(STOCK_PARAM);
  const q = params.get(SEARCH_PARAM)?.trim() ?? '';
  const categoryId = params.get(CATEGORY_PARAM) ?? '';
  return {
    q: q === '' || !validSearch(q) ? null : q,
    categoryId: UUID.test(categoryId) ? categoryId : null,
    state: PRODUCT_STATES.find((value) => value === state) ?? null,
    stock: PRODUCT_STOCKS.find((value) => value === stock) ?? null,
  };
}

/** UC-06: 2 to 100 characters of the name, or a SKU; blank is no search. */
function validSearch(typed: string): boolean {
  // Code points, as the server counts them.
  const length = Array.from(typed.trim()).length;
  return length === 0 || (length >= 2 && length <= MAX_SEARCH);
}

/**
 * UC-06 at /admin/productos: every product, published or not, newest first, 50 at a time, filtered by a search (name or
 * SKU), a category, a state and a stock level (T-E8-08), all kept in the address.
 */
export function ProductsPage() {
  const [params, setParams] = useSearchParams();
  const filter = filterOf(params);
  const [search, setSearch] = useState(filter.q ?? '');
  const [searchInvalid, setSearchInvalid] = useState(false);
  const searchField = useRef<HTMLInputElement>(null);
  const filtered = filter.q !== null || filter.categoryId !== null || filter.state !== null || filter.stock !== null;
  const categories = useCategories();
  const products = useInfiniteQuery({
    queryKey: ['admin', 'products', 'list', filter.q, filter.categoryId, filter.state, filter.stock],
    queryFn: ({ pageParam, signal }) => fetchProductSummaries(filter, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last, _pages, page) => (last.hasMore ? page + 1 : undefined),
  });
  const categoryNames = new Map(categories.data?.map((category) => [category.id, category.name]));
  // Until the categories arrive, or for one that no longer exists, the select shows its own placeholder option
  // instead of "Todas", so it never claims the list is unfiltered.
  const unknownCategory = filter.categoryId !== null && !categoryNames.has(filter.categoryId);
  const items = products.data?.pages.flatMap((page) => page.items) ?? [];

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
        <Link to="/admin/productos/nuevo" className="btn">
          {messages.create}
        </Link>
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
        <Field>
          <FieldLabel>{messages.state}</FieldLabel>
          <FieldSelect
            value={filter.state ?? ''}
            onChange={(event) => {
              change(STATE_PARAM, event.target.value);
            }}
          >
            <option value="">{messages.allStates}</option>
            {PRODUCT_STATES.map((value) => (
              <option key={value} value={value}>
                {messages.states[value]}
              </option>
            ))}
          </FieldSelect>
        </Field>
        <Field>
          <FieldLabel>{messages.stock}</FieldLabel>
          <FieldSelect
            value={filter.stock ?? ''}
            onChange={(event) => {
              change(STOCK_PARAM, event.target.value);
            }}
          >
            <option value="">{messages.allStock}</option>
            {PRODUCT_STOCKS.map((value) => (
              <option key={value} value={value}>
                {messages.stocks[value]}
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
      {products.isPending && <p role="status">{esAdmin.loading}</p>}
      {products.isError && items.length === 0 && (
        <div role="alert">
          <p>{esAdmin.loadError}</p>
          <button type="button" className="btn" onClick={() => void products.refetch()}>
            {esAdmin.retry}
          </button>
        </div>
      )}
      {products.isSuccess && items.length === 0 && <p>{filtered ? messages.emptyFiltered : messages.empty}</p>}
      {items.length > 0 && (
        <ul className="admin-list product-rows">
          {items.map((product) => (
            <li key={product.id} className="admin-card">
              <div className="admin-card-head">
                <Link to={`/admin/productos/${product.id}`} className="admin-card-title">
                  {product.name}
                </Link>
                <p className="admin-card-meta">
                  {categoryNames.get(product.categoryId) ?? messages.noCategory} ·{' '}
                  {messages.variants(product.variantCount)}
                </p>
                <span className={product.published ? 'admin-badge published' : 'admin-badge'}>
                  {product.published ? messages.published : messages.hidden}
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
      {products.hasNextPage && (
        <button
          type="button"
          className="btn btn-secondary show-more-btn"
          disabled={products.isFetchingNextPage}
          onClick={() => void products.fetchNextPage()}
        >
          {products.isFetchingNextPage ? esAdmin.loading : esAdmin.showMore}
        </button>
      )}
    </>
  );
}
