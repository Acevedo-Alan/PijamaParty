import { useId } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { AUDIENCE_CARDS } from './homeContent.ts';
import { NO_FILTERS, toSearchParams, type ProductSort } from './listingCriteria.ts';

const CARD_SIZES = '(min-width: 1180px) 380px, 33vw';

interface AudienceCardsProps {
  /** The listing's sort, kept when an audience is chosen (UC-30). */
  sort: ProductSort;
  /** The page takes the visitor to its listing once the audience is chosen. */
  onChoose: () => void;
}

/**
 * "Elegí para quién" (UC-30): one card per audience opens the home listing filtered by it (the UC-02 key
 * `publico`), keeping the sort. The photos are decorative: the label names the audience.
 */
export function AudienceCards({ sort, onChoose }: AudienceCardsProps) {
  const titleId = useId();

  return (
    <section className="audience" aria-labelledby={titleId}>
      <h2 className="section-title" id={titleId}>
        {es.home.audienceTitle}
      </h2>
      <p className="section-sub">{es.home.audienceText}</p>
      <ul className="audience-grid">
        {AUDIENCE_CARDS.map(({ audience, tone, photo }) => (
          <li key={audience}>
            <Link
              className={`audience-card tone-${tone}`}
              to={{ pathname: '/', search: toSearchParams({ filters: { ...NO_FILTERS, audiences: [audience] }, sort }).toString() }}
              onClick={onChoose}
            >
              {photo !== undefined && (
                <img src={photo.src} srcSet={photo.srcSet} sizes={CARD_SIZES} alt="" loading="lazy" decoding="async" />
              )}
              <span>{es.home.audiences[audience]}</span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
