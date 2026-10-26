import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { FAQ_PATH } from '../help/FaqLink.tsx';
import { PageMeta } from '../seo/PageMeta.tsx';
import { SizeGuideTable } from './SizeGuideSection.tsx';
import { fetchSizeGuides } from './sizeGuidesApi.ts';
import '../help/help.css';

const messages = es.sizeGuides;

/** The guides on the page, or why there are none (T-E7-29). */
function SizeGuides() {
  const guides = useQuery({ queryKey: ['catalog', 'size-guides'], queryFn: ({ signal }) => fetchSizeGuides(signal) });
  if (guides.isPending) {
    return <p role="status">{messages.loading}</p>;
  }
  if (guides.isError) {
    return (
      <div role="alert" className="faq-error">
        <p>{messages.loadError}</p>
        <button type="button" className="btn btn-secondary" onClick={() => void guides.refetch()}>
          {messages.retry}
        </button>
      </div>
    );
  }
  if (guides.data.length === 0) {
    return (
      <p>
        {messages.none} <Link to={FAQ_PATH}>{messages.faq}</Link>.
      </p>
    );
  }
  return guides.data.map(({ category, guide }) => (
    <section key={category.slug} aria-labelledby={`size-guide-${category.slug}`}>
      <h2 id={`size-guide-${category.slug}`}>{category.name}</h2>
      <SizeGuideTable guide={guide} label={messages.tableOf(category.name)} />
    </section>
  ));
}

/**
 * UC-31 (T-E7-29, RNF-31.5): the size guide of every active category that has one, the same the administrator keeps
 * in the back office and the product page shows.
 */
export function SizeGuidesPage() {
  return (
    <main className="wrap help-page">
      <title>{`${messages.title} | ${es.store.name}`}</title>
      <PageMeta description={es.seo.sizeGuidesDescription} />
      <h1>{messages.title}</h1>
      <p className="help-intro">{messages.intro}</p>
      <SizeGuides />
    </main>
  );
}
