import { useQueries } from '@tanstack/react-query';
import { useEffect, useId, useState } from 'react';
import { es } from '../../i18n/es.ts';
import type { ListedProduct } from './catalogApi.ts';
import { ProductCard } from './ProductCard.tsx';
import { fetchProductDetail, type ProductDetail } from './productDetailApi.ts';
import { forgetViewed, readRecentlyViewed } from './recentlyViewed.ts';
import { productDetailKey } from './useProductDetail.ts';

/** How many viewed products show. */
const SHOWN = 4;

/** The card of a viewed product from its current detail: the server's name, photos, price and stock. */
function toListed(product: ProductDetail): ListedProduct {
  const inStock = (color: string) =>
    product.variants.some((variant) => variant.color === color && variant.available > 0);
  return {
    slug: product.slug,
    name: product.name,
    image: product.images[0] ?? null,
    secondImage: product.images[1] ?? null,
    fromPriceCents: product.fromPriceCents,
    soldOut: product.variants.every((variant) => variant.available === 0),
    colors: product.colors.filter(inStock),
    // The detail does not say it (T-E7-33).
    isNew: false,
  };
}

/**
 * "Vistos recientemente" (T-E7-33): the last products this browser opened, without the open one, as cards read from
 * their current detail (shared with the product page's cache). Nothing shows while they load or when none is left.
 */
export function RecentlyViewed({ exclude, className }: { exclude?: string; className?: string }) {
  const titleId = useId();
  const [slugs] = useState(() => readRecentlyViewed().filter((slug) => slug !== exclude).slice(0, SHOWN));
  const details = useQueries({
    queries: slugs.map((slug) => ({
      queryKey: productDetailKey(slug),
      queryFn: ({ signal }: { signal: AbortSignal }) => fetchProductDetail(slug, signal),
    })),
  });

  // The slugs no longer listed, as one string so the effect runs when they change, not on every render.
  const gone = slugs.filter((_, index) => details[index]?.data?.status === 'notFound').join(' ');
  useEffect(() => {
    gone.split(' ').filter(Boolean).forEach(forgetViewed);
  }, [gone]);

  if (details.some((detail) => detail.isPending)) {
    return null;
  }
  const viewed = details.flatMap((detail) => (detail.data?.status === 'found' ? [toListed(detail.data.product)] : []));
  if (viewed.length === 0) {
    return null;
  }

  return (
    <section className={`related${className === undefined ? '' : ` ${className}`}`} aria-labelledby={titleId}>
      <h2 id={titleId} className="related-title">
        {es.product.recentlyViewed}
      </h2>
      <ul className="grid">
        {viewed.map((product) => (
          <li key={product.slug}>
            <ProductCard product={product} />
          </li>
        ))}
      </ul>
    </section>
  );
}
