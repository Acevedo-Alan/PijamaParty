import { Link, useNavigate } from 'react-router';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { ProductForm } from './ProductForm.tsx';
import { createProduct, type ProductDetails } from './productsApi.ts';
import { useStoreProduct } from './useAdminProduct.ts';
import { useCategories } from './useCategories.ts';

const messages = esAdmin.productForm;

/** UC-06 step 1 at /admin/productos/nuevo: a new product is hidden and has no variants; it then opens to edit. */
export function NewProductPage() {
  const categories = useCategories();
  const store = useStoreProduct();
  const navigate = useNavigate();

  async function create(details: ProductDetails) {
    const answer = await createProduct(details);
    if (answer.status === 'saved') {
      store(answer.product);
      void navigate(`/admin/productos/${answer.product.id}`, { replace: true });
    }
    return answer;
  }

  return (
    <>
      <title>{`${messages.newTitle} | ${esAdmin.title}`}</title>
      <Link to="/admin/productos" className="back-link">
        {esAdmin.productEdit.backToList}
      </Link>
      <h1>{messages.newTitle}</h1>
      {categories.isPending && <p role="status">{esAdmin.loading}</p>}
      {categories.isError && (
        <div role="alert">
          <p>{esAdmin.loadError}</p>
          <button type="button" className="btn" onClick={() => void categories.refetch()}>
            {esAdmin.retry}
          </button>
        </div>
      )}
      {categories.data?.length === 0 && (
        <p>
          {messages.needsCategory} <Link to="/admin/categorias">{esAdmin.categories}</Link>
        </p>
      )}
      {categories.data !== undefined && categories.data.length > 0 && (
        <ProductForm categories={categories.data} initial={null} submitLabel={messages.create} onSubmit={create} />
      )}
    </>
  );
}
