import type { Audience } from './listingCriteria.ts';

/**
 * A photo built into the frontend (D-03): WebP files at 640, 1280 and 1920 px in `src/assets/home/`, imported so
 * the build fingerprints them, with its alternative text from es.ts.
 */
export interface HomePhoto {
  src: string;
  srcSet: string;
  alt: string;
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

/** Up to 3 slides (D-14). The business has not sent the photos yet (D-15): each slide shows its gradient. */
export const HERO_SLIDES: readonly HeroSlide[] = [{ tone: 'hero' }, { tone: 'rose' }, { tone: 'peach' }];

/** "Elegí para quién" (UC-30), in the prototype's order; the photos are pending as the carousel's are (D-15). */
export const AUDIENCE_CARDS: readonly AudienceCard[] = [
  { audience: 'WOMEN', tone: 'rose' },
  { audience: 'MEN', tone: 'cream' },
  { audience: 'KIDS', tone: 'peach' },
];
