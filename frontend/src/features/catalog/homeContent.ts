import kids640 from '../../assets/home/audience-kids-640.webp';
import kids1280 from '../../assets/home/audience-kids-1280.webp';
import men640 from '../../assets/home/audience-men-640.webp';
import men1280 from '../../assets/home/audience-men-1280.webp';
import women640 from '../../assets/home/audience-women-640.webp';
import women1280 from '../../assets/home/audience-women-1280.webp';
import family640 from '../../assets/home/hero-family-640.webp';
import family896 from '../../assets/home/hero-family-896.webp';
import familyWide1280 from '../../assets/home/hero-family-wide-1280.webp';
import familyWide1920 from '../../assets/home/hero-family-wide-1920.webp';
import fort640 from '../../assets/home/hero-fort-640.webp';
import fort1280 from '../../assets/home/hero-fort-1280.webp';
import fort1792 from '../../assets/home/hero-fort-1792.webp';
import fortWide1280 from '../../assets/home/hero-fort-wide-1280.webp';
import fortWide1920 from '../../assets/home/hero-fort-wide-1920.webp';
import popcorn640 from '../../assets/home/hero-popcorn-640.webp';
import popcorn1280 from '../../assets/home/hero-popcorn-1280.webp';
import popcorn1792 from '../../assets/home/hero-popcorn-1792.webp';
import popcornWide1280 from '../../assets/home/hero-popcorn-wide-1280.webp';
import popcornWide1920 from '../../assets/home/hero-popcorn-wide-1920.webp';
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
  /** The same scene composed for wide screens, shown instead from 700 px (T-E7-82). */
  wide?: { src: string; srcSet: string };
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
  wide: { src: familyWide1280, srcSet: `${familyWide1280} 1280w, ${familyWide1920} 1920w` },
};

/** The second slide: a mother and daughter in short summer pajamas and patterned knee socks, sharing popcorn. */
const POPCORN_PHOTO: HomePhoto = {
  src: popcorn640,
  srcSet: `${popcorn640} 640w, ${popcorn1280} 1280w, ${popcorn1792} 1792w`,
  alt: es.home.carousel.photos.popcorn,
  wide: { src: popcornWide1280, srcSet: `${popcornWide1280} 1280w, ${popcornWide1920} 1920w` },
};

/** The third slide: three children in printed pajamas and a wearable blanket inside a blanket fort. */
const FORT_PHOTO: HomePhoto = {
  src: fort640,
  srcSet: `${fort640} 640w, ${fort1280} 1280w, ${fort1792} 1792w`,
  alt: es.home.carousel.photos.fort,
  wide: { src: fortWide1280, srcSet: `${fortWide1280} 1280w, ${fortWide1920} 1920w` },
};

/** Up to 3 slides (D-14), each with its photo (D-15). */
export const HERO_SLIDES: readonly HeroSlide[] = [
  { tone: 'hero', photo: FAMILY_PHOTO },
  { tone: 'rose', photo: POPCORN_PHOTO },
  { tone: 'peach', photo: FORT_PHOTO },
];

/** An audience card's photo (T-E7-86), 4:5: decorative, the card's name says who it is for. */
function audiencePhoto(small: string, large: string): HomePhoto {
  return { src: small, srcSet: `${small} 640w, ${large} 1280w`, alt: '' };
}

/** "Elegí para quién" (UC-30), in the prototype's order, each with its photo (D-15): one series, the same bedroom. */
export const AUDIENCE_CARDS: readonly AudienceCard[] = [
  { audience: 'WOMEN', tone: 'rose', photo: audiencePhoto(women640, women1280) },
  { audience: 'MEN', tone: 'cream', photo: audiencePhoto(men640, men1280) },
  { audience: 'KIDS', tone: 'peach', photo: audiencePhoto(kids640, kids1280) },
];
