import { useCallback, useEffect, useRef } from 'react';
import { es } from '../../i18n/es.ts';
import { WithdrawalHomeBlock } from '../withdrawal/WithdrawalLink.tsx';
import { AudienceCards } from './AudienceCards.tsx';
import { CatalogListing } from './CatalogListing.tsx';
import { CharacterBanner, useHomeBanner } from './CharacterBanner.tsx';
import { HeroCarousel } from './HeroCarousel.tsx';
import { toSearchParams, withoutText } from './listingCriteria.ts';
import { useListingCriteria } from './useListingCriteria.ts';
import './catalog.css';
import './home.css';

/**
 * The home page at / (UC-30): the welcome carousel, the character banner (UC-32), "Elegí para quién", the whole
 * shop's listing (UC-01, UC-02) and the withdrawal block (UC-29). Opened with filters or a sort in the URL, it
 * starts at the listing.
 */
export function HomePage() {
  const [urlCriteria] = useListingCriteria();
  const criteria = withoutText(urlCriteria);
  const listingHeading = useRef<HTMLHeadingElement>(null);
  const opensAtListing = useRef(toSearchParams(criteria).toString() !== '');
  const bannerSettled = useHomeBanner().isFetched;

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

  return (
    <main className="home">
      <title>{`${es.catalog.allTitle} | ${es.store.name}`}</title>
      <HeroCarousel onDiscover={showListing} />
      <div className="wrap home-sections">
        <CharacterBanner />
        <AudienceCards sort={criteria.sort} onChoose={showListing} />
      </div>
      <section className="wrap catalog" aria-labelledby="todos-los-productos">
        <CatalogListing
          photosBelowFold
          heading={
            <h2 className="section-title listing-heading" id="todos-los-productos" ref={listingHeading} tabIndex={-1}>
              {es.catalog.allTitle}
            </h2>
          }
        />
        <WithdrawalHomeBlock />
      </section>
    </main>
  );
}
