import { useId, useRef, useState } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { AUDIENCE_CARDS } from './homeContent.ts';
import { NO_FILTERS, toSearchParams, type ProductSort } from './listingCriteria.ts';
import { useAudienceCounts } from './useAudienceCounts.ts';

/** 70 % of a phone's width (the row slides sideways), a third of the page from 700 px (T-E7-84). */
const CARD_SIZES = '(min-width: 1180px) 380px, (min-width: 700px) 33vw, 70vw';

const AUDIENCES = AUDIENCE_CARDS.map(({ audience }) => audience);

interface AudienceCardsProps {
  /** The listing's sort, kept when an audience is chosen (UC-30). */
  sort: ProductSort;
  /** The page takes the visitor to its listing once the audience is chosen. */
  onChoose: () => void;
}

/** The arrow of a card (T-E7-85): drawn, so its stroke and size fit the circle. */
function ArrowIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  );
}

/** "48 productos"; nothing with none, while it loads or when it cannot be loaded (T-E7-85). */
function countText(count: number | undefined): string {
  return count === undefined || count === 0 ? '' : es.catalog.productCount(count);
}

/**
 * The card in view in the phone's sideways row (T-E7-85): the scroll divided by one card and its gap, the last one
 * once the row reaches its end.
 */
function cardInView(row: HTMLElement): number {
  const items = row.children;
  const first = items[0] as HTMLElement | undefined;
  const second = items[1] as HTMLElement | undefined;
  if (first === undefined || second === undefined) {
    return 0;
  }
  if (row.scrollLeft + row.clientWidth >= row.scrollWidth - 2) {
    return items.length - 1;
  }
  return Math.round(row.scrollLeft / (second.offsetLeft - first.offsetLeft));
}

/**
 * "Elegí para quién" (UC-30): one card per audience opens the home listing filtered by it (the UC-02 key
 * `publico`), keeping the sort. The photos are decorative: the label names the audience, and how many products it
 * has describes the link (T-E7-85).
 */
export function AudienceCards({ sort, onChoose }: AudienceCardsProps) {
  const titleId = useId();
  const countId = useId();
  const section = useRef<HTMLElement>(null);
  const counts = useAudienceCounts(AUDIENCES, section);
  const [inView, setInView] = useState(0);

  return (
    <section className="audience" aria-labelledby={titleId} ref={section}>
      <h2 className="section-title" id={titleId}>
        {es.home.audienceTitle}
      </h2>
      <p className="section-sub">{es.home.audienceText}</p>
      <ul
        className="audience-grid"
        onScroll={(event) => {
          setInView(cardInView(event.currentTarget));
        }}
      >
        {AUDIENCE_CARDS.map(({ audience, tone, photo }, index) => {
          const text = countText(counts[index]);
          return (
            <li key={audience}>
              <Link
                className={`audience-card tone-${tone}`}
                to={{ pathname: '/', search: toSearchParams({ filters: { ...NO_FILTERS, audiences: [audience] }, sort }).toString() }}
                onClick={onChoose}
                aria-describedby={text === '' ? undefined : `${countId}-${audience}`}
              >
                {photo !== undefined && (
                  <img src={photo.src} srcSet={photo.srcSet} sizes={CARD_SIZES} alt="" loading="lazy" decoding="async" />
                )}
                <span className="audience-text">
                  <span className="audience-name">{es.home.audiences[audience]}</span>
                  {/* The link stays named by the audience; the count describes it (aria-describedby) */}
                  <span className="audience-more" id={`${countId}-${audience}`} aria-hidden="true">
                    {text}
                  </span>
                </span>
                <span className="audience-arrow" aria-hidden="true">
                  <ArrowIcon />
                </span>
              </Link>
            </li>
          );
        })}
      </ul>
      {/* Phones only: which card of the sideways row is in view. Decorative: the cards are reached by swiping or Tab */}
      <div className="audience-dots" aria-hidden="true">
        {AUDIENCE_CARDS.map(({ audience }, index) => (
          <span key={audience} className={index === inView ? 'active' : undefined} />
        ))}
      </div>
    </section>
  );
}
