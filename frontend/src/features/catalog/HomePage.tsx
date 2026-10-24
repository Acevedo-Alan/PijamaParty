import { useCallback, useEffect, useRef } from 'react';
import { useLocation } from 'react-router';
import { es } from '../../i18n/es.ts';
import { ContactSection } from '../help/ContactSection.tsx';
import { PageMeta } from '../seo/PageMeta.tsx';
import { WithdrawalHomeBlock } from '../withdrawal/WithdrawalLink.tsx';
import { AudienceCards } from './AudienceCards.tsx';
import { CatalogListing } from './CatalogListing.tsx';
import { CharacterBanner, useHomeBanner } from './CharacterBanner.tsx';
import { HeroCarousel } from './HeroCarousel.tsx';
import { toSearchParams, withoutText } from './listingCriteria.ts';
import { useListingCriteria } from './useListingCriteria.ts';
import './catalog.css';
import './home.css';

/** The navigation state of a link that opens the listing with a filter (UC-31): the home page goes to its listing. */
export const SHOW_LISTING = { showListing: true } as const;

function showsListing(state: unknown): boolean {
  return typeof state === 'object' && state !== null && 'showListing' in state && state.showListing === true;
}

/**
 * The home page at / (UC-30): the welcome carousel, the character banner (UC-32), "Elegí para quién", the whole
 * shop's listing (UC-01, UC-02), the withdrawal block (UC-29) and "Contactanos" (D-09). Opened with filters or a sort in the URL, or from
 * a navigation link of the header (UC-31), it starts at the listing.
 */
export function HomePage() {
  const [urlCriteria] = useListingCriteria();
  const criteria = withoutText(urlCriteria);
  const listingHeading = useRef<HTMLHeadingElement>(null);
  const opensAtListing = useRef(toSearchParams(criteria).toString() !== '');
  const bannerSettled = useHomeBanner().isFetched;
  const location = useLocation();

  const showListing = useCallback(() => {
    const heading = listingHeading.current;
    heading?.scrollIntoView({ block: 'start' });
    heading?.focus({ preventScroll: true });
  }, []);

  // The banner arrives above the listing after the first paint: the page scrolls again once it has its place.
  useEffect(() => {
    if (!opensAtListing.current) {
      return;
    }
    listingHeading.current?.scrollIntoView({ block: 'start', behavior: 'instant' });
    if (bannerSettled) {
      opensAtListing.current = false;
    }
  }, [bannerSettled]);

  // A navigation link keeps this page mounted when it is already open: each one goes to the listing again.
  useEffect(() => {
    if (showsListing(location.state)) {
      showListing();
    }
  }, [location, showListing]);

  return (
    <main className="home">
      <title>{`${es.catalog.allTitle} | ${es.store.name}`}</title>
      <PageMeta description={es.seo.homeDescription} />
      <HeroCarousel onDiscover={showListing} />
      <CharacterBanner />
      <div className="wrap home-sections">
        <AudienceCards sort={criteria.sort} onChoose={showListing} />
      </div>
      <section className="wrap catalog" aria-labelledby="todos-los-productos">
        <CatalogListing
          photosBelowFold
          heading={
            <>
              <h2 className="section-title listing-heading" id="todos-los-productos" ref={listingHeading} tabIndex={-1}>
                {es.catalog.allTitle}
              </h2>
              <p className="section-sub">{es.home.listingText}</p>
            </>
          }
        />
        <WithdrawalHomeBlock />
      </section>
      <ContactSection />
    </main>
  );
}
