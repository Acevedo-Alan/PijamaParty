import { useInfiniteQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { fetchProductSummaries } from './productsApi.ts';
import { useCategories } from './useCategories.ts';

const messages = es.admin.productList;

/** UC-06 at /admin/productos: every product, published or not, newest first, 50 at a time. */
export function ProductsPage() {
  const categories = useCategories();
  const products = useInfiniteQuery({
    queryKey: ['admin', 'products'],
    queryFn: ({ pageParam, signal }) => fetchProductSummaries(pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last, _pages, page) => (last.hasMore ? page + 1 : undefined),
  });
  const categoryNames = new Map(categories.data?.map((category) => [category.id, category.name]));
  const items = products.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <>
      <title>{`${messages.title} | ${es.admin.title}`}</title>
      <div className="admin-title-row">
        <h1>{messages.title}</h1>
        <Link to="/admin/productos/nuevo" className="btn">
          {messages.create}
        </Link>
      </div>
      {products.isPending && <p role="status">{es.admin.loading}</p>}
      {products.isError && items.length === 0 && (
        <div role="alert">
          <p>{es.admin.loadError}</p>
          <button type="button" className="btn" onClick={() => void products.refetch()}>
            {es.admin.retry}
          </button>
        </div>
      )}
      {products.isSuccess && items.length === 0 && <p>{messages.empty}</p>}
      {items.length > 0 && (
        <ul className="admin-list">
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
          {products.isFetchingNextPage ? es.admin.loading : es.admin.showMore}
        </button>
      )}
    </>
  );
}
