import { useQuery } from '@tanstack/react-query';
import { useId } from 'react';
import { Link } from 'react-router';
import { BlurredImage } from '../../components/BlurredImage.tsx';
import { fetchHomeBanner, isStorePath, type HomeBanner } from './homeBannerApi.ts';

export const homeBannerKey = ['homeBanner'] as const;

interface ViewProps {
  banner: HomeBanner;
  /** The back office's preview: the link is shown, not followed. */
  preview?: boolean;
}

/**
 * The character banner as the home page shows it (UC-32): the text beside the photo from 700 px, the photo above it
 * below. The photo is decorative (the title carries the message), so its alternative text is empty. Its link is a
 * router link to a path of the store, never an address built from the text.
 */
export function CharacterBannerView({ banner, preview = false }: ViewProps) {
  const titleId = useId();
  const { title, body, linkText, linkPath, image } = banner;
  const arrow = <span aria-hidden="true">→</span>;

  return (
    <section className="character-banner" aria-labelledby={titleId}>
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
          {...(image.placeholder === undefined ? {} : { placeholder: image.placeholder })}
        />
      )}
    </section>
  );
}

/** The home page's section (UC-30): only a published banner shows; while it loads or if it fails, nothing does. */
export function CharacterBanner() {
  const { data } = useQuery({ queryKey: homeBannerKey, queryFn: ({ signal }) => fetchHomeBanner(signal) });
  return data ? <CharacterBannerView banner={data} /> : null;
}
