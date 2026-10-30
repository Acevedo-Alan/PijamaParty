import { useId, useState } from 'react';
import { Link, useParams } from 'react-router';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { Feedback, type Notice } from './Feedback.tsx';
import { PhotosSection } from './PhotosSection.tsx';
import { ProductForm } from './ProductForm.tsx';
import { setPublished, updateProduct, type AdminProduct, type ProductDetails } from './productsApi.ts';
import { useAdminProduct, useStoreProduct } from './useAdminProduct.ts';
import { useCategories } from './useCategories.ts';
import { VariantsSection } from './VariantsSection.tsx';

const messages = esAdmin.productEdit;

function DetailsSection({ product }: { product: AdminProduct }) {
  const categories = useCategories();
  const store = useStoreProduct();
  const titleId = useId();

  async function save(details: ProductDetails) {
    const answer = await updateProduct(product.id, details);
    if (answer.status === 'saved') {
      store(answer.product);
    }
    return answer;
  }

  const { categoryId, name, description, material, season, audience } = product;
  return (
    <section className="admin-section" aria-labelledby={titleId}>
      <h2 id={titleId}>{messages.details}</h2>
      {categories.isError && <p role="alert">{esAdmin.loadError}</p>}
      {categories.data !== undefined && (
        <ProductForm
          categories={categories.data}
          initial={{ categoryId, name, description, material, season, audience }}
          submitLabel={esAdmin.save}
          onSubmit={save}
        />
      )}
    </section>
  );
}

/** Publishing needs an active priced variant; hiding takes the product out of the shop (UC-06 step 5). */
function StateSection({ product }: { product: AdminProduct }) {
  const store = useStoreProduct();
  const titleId = useId();
  const [notice, setNotice] = useState<Notice | null>(null);
  const [saving, setSaving] = useState(false);

  async function toggle() {
    setSaving(true);
    setNotice(null);
    try {
      const answer = await setPublished(product.id, !product.published);
      switch (answer.status) {
        case 'saved':
          store(answer.product);
          break;
        case 'needsPricedVariant':
          setNotice({ kind: 'error', text: messages.needsPricedVariant });
          break;
        case 'notFound':
          setNotice({ kind: 'error', text: messages.notFound });
          break;
      }
    } catch {
      setNotice({ kind: 'error', text: esAdmin.actionError });
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="admin-section" aria-labelledby={titleId}>
      <h2 id={titleId}>{messages.state}</h2>
      <p>{product.published ? messages.published : messages.hidden}</p>
      <div className="admin-actions">
        <button type="button" className="btn" disabled={saving} onClick={() => void toggle()}>
          {product.published ? messages.hide : messages.publish}
        </button>
        {product.published && <Link to={`/producto/${product.slug}`}>{messages.viewInStore}</Link>}
      </div>
      <Feedback notice={notice} />
    </section>
  );
}

/** UC-06 at /admin/productos/:productId: details, state, variants and photos of one product. */
export function ProductEditPage() {
  const { productId = '' } = useParams();
  const product = useAdminProduct(productId);

  return (
    <>
      <Link to="/admin/productos" className="back-link">
        {messages.backToList}
      </Link>
      {product.isPending && <p role="status">{esAdmin.loading}</p>}
      {product.isError && (
        <div role="alert">
          <p>{esAdmin.loadError}</p>
          <button type="button" className="btn" onClick={() => void product.refetch()}>
            {esAdmin.retry}
          </button>
        </div>
      )}
      {product.data?.status === 'notFound' && <h1>{messages.notFound}</h1>}
      {product.data?.status === 'found' && (
        <>
          <title>{`${product.data.product.name} | ${esAdmin.title}`}</title>
          <h1>{product.data.product.name}</h1>
          <DetailsSection key={product.data.product.id} product={product.data.product} />
          <StateSection product={product.data.product} />
          <VariantsSection product={product.data.product} />
          <PhotosSection product={product.data.product} />
        </>
      )}
    </>
  );
}
