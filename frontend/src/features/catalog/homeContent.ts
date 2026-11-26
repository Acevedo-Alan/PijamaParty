import family640 from '../../assets/home/hero-family-640.webp';
import family896 from '../../assets/home/hero-family-896.webp';
import { es } from '../../i18n/es.ts';
import type { Audience } from './listingCriteria.ts';

/**
 * A photo built into the frontend (D-03): WebP files at 640, 1280 and 1920 px in `src/assets/home/`, imported so
 * the build fingerprints them, with its alternative text from es.ts.
 */
export interface HomePhoto {
  src: string;
  srcSet: string;
  alt: string;
  /**
   * The height of the photo kept on wide screens, where the carousel shows only a band of a portrait photo, as a CSS
   * percentage from its top ("20%" keeps a band near the top). Without it, the band is near the middle.
   */
  wideFocus?: string;
}

/** The prototype's gradient backgrounds, shown where a photo is missing (D-15). */
export type HomeTone = 'hero' | 'rose' | 'peach' | 'cream';

export interface HeroSlide {
  tone: HomeTone;
  photo?: HomePhoto;
}

export interface AudienceCard {
  audience: Audience;
  tone: HomeTone;
  photo?: HomePhoto;
}

/**
 * The first slide's photo, made at 896 px: no larger file is built from it, an upscaled copy would only weigh more.
 */
const FAMILY_PHOTO: HomePhoto = {
  src: family640,
  srcSet: `${family640} 640w, ${family896} 896w`,
  alt: es.home.carousel.photos.family,
  // The faces are in the photo's upper half.
  wideFocus: '18%',
};

/** Up to 3 slides (D-14). The second and third photos are still pending (D-15): those slides show their gradient. */
export const HERO_SLIDES: readonly HeroSlide[] = [
  { tone: 'hero', photo: FAMILY_PHOTO },
  { tone: 'rose' },
  { tone: 'peach' },
];

/** "Elegí para quién" (UC-30), in the prototype's order; the photos are pending as the carousel's are (D-15). */
export const AUDIENCE_CARDS: readonly AudienceCard[] = [
  { audience: 'WOMEN', tone: 'rose' },
  { audience: 'MEN', tone: 'cream' },
  { audience: 'KIDS', tone: 'peach' },
];
