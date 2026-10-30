import { useQuery } from '@tanstack/react-query';
import { useId } from 'react';
import { Link } from 'react-router';
import { BlurredImage } from '../../components/BlurredImage.tsx';
import { fetchHomeBanner, isStorePath, type HomeBanner } from './homeBannerApi.ts';
import { HERO_SLIDES } from './homeContent.ts';

export const homeBannerKey = ['homeBanner'] as const;

interface ViewProps {
  banner: HomeBanner;
  /** The back office's preview: the link is shown, not followed. */
  preview?: boolean;
  /** The photo is the first one on the screen: loaded at once and first, not lazily (RNF-11). */
  firstPhoto?: boolean;
}

/**
 * The character banner as the home page shows it (UC-32): a band across the page with its content at the page width
 * (T-E7-11), the text beside the photo from 700 px, the photo above it below. The photo is decorative (the title
 * carries the message), so its alternative text is empty. Its link is a router link to a path of the store, never an
 * address built from the text.
 */
export function CharacterBannerView({ banner, preview = false, firstPhoto = false }: ViewProps) {
  const titleId = useId();
  const { title, body, linkText, linkPath, image } = banner;
  const arrow = <span aria-hidden="true">→</span>;

  return (
    <section className="character-banner" aria-labelledby={titleId}>
      <div className="character-banner-inner">
        <div className="character-banner-copy">
          <h2 id={titleId}>{title}</h2>
          {body !== '' && <p>{body}</p>}
          {preview || !isStorePath(linkPath) ? (
            <span className="character-banner-link">
              {linkText} {arrow}
            </span>
          ) : (
            <Link className="character-banner-link" to={linkPath}>
              {linkText} {arrow}
            </Link>
          )}
        </div>
        {image === null ? (
          <div className="ph character-banner-art" />
        ) : (
          <BlurredImage
            className="character-banner-art"
            src={image.src}
            srcSet={image.srcSet}
            sizes="(min-width: 700px) 560px, 100vw"
            alt=""
            loading={firstPhoto ? 'eager' : 'lazy'}
            fetchPriority={firstPhoto ? 'high' : 'auto'}
            {...(image.placeholder === undefined ? {} : { placeholder: image.placeholder })}
          />
        )}
      </div>
    </section>
  );
}

/** The published banner, shared by its section and by the page that waits for it before scrolling past it. */
export function useHomeBanner() {
  return useQuery({ queryKey: homeBannerKey, queryFn: ({ signal }) => fetchHomeBanner(signal) });
}

/** While no slide of the carousel has a photo (D-15), the banner's is the first photo on the screen (UC-30). */
const BANNER_PHOTO_FIRST = HERO_SLIDES[0]?.photo === undefined;

/**
 * The home page's section (UC-30): only a published banner shows. While it loads, a gradient of its size holds the
 * place, so the sections below do not move when it arrives (T-E7-11); unpublished or failed, the place closes.
 */
export function CharacterBanner() {
  const { data, isPending } = useHomeBanner();
  if (isPending) {
    return (
      <div className="character-banner character-banner-pending" aria-hidden="true">
        <div className="character-banner-inner">
          <div className="character-banner-copy" />
          <div className="ph character-banner-art" />
        </div>
      </div>
    );
  }
  return data ? <CharacterBannerView banner={data} firstPhoto={BANNER_PHOTO_FIRST} /> : null;
}
