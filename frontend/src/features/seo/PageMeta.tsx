import { useLocation } from 'react-router';

/**
 * The description and canonical URL of an indexable page (T-E6-23, RNF-31.1). The canonical URL drops the query
 * string, so filters, sort and pages point to the bare page.
 */
export function PageMeta({ description }: { description: string }) {
  const { pathname } = useLocation();
  return (
    <>
      <meta name="description" content={description} />
      <link rel="canonical" href={canonicalUrl(pathname)} />
    </>
  );
}

export function canonicalUrl(pathname: string): string {
  return new URL(pathname, window.location.origin).href;
}
