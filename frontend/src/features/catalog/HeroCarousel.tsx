import { m } from 'motion/react';
import { useEffect, useState } from 'react';
import { preload } from 'react-dom';
import { useActionMotion } from '../../components/motion/useActionMotion.ts';
import { usePrefersReducedMotion } from '../../components/motion/usePrefersReducedMotion.ts';
import { es } from '../../i18n/es.ts';
import { duration, ease } from '../../styles/motion.ts';
import { HERO_SLIDES, type HeroSlide } from './homeContent.ts';

/** D-14: a slide stays 4 seconds. */
export const SLIDE_MS = 4000;

const HERO_SIZES = '100vw';
/** The welcome text enters once, its lines 80 ms apart (E7 motion). */
const STAGGER_S = 0.08;

function useEntrance(order: number) {
  return useActionMotion({
    hidden: { y: 12, opacity: 0 },
    shown: { y: 0, opacity: 1 },
    transition: { duration: duration.entrance, ease: ease.out, delay: order * STAGGER_S },
  });
}

function PauseIcon({ paused }: { paused: boolean }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      {paused ? <path d="M8 5l11 7-11 7z" /> : <path d="M8 5v14M16 5v14" />}
    </svg>
  );
}

interface HeroCarouselProps {
  /** "Descubrí la colección": the page takes the visitor to its listing. */
  onDiscover: () => void;
  slides?: readonly HeroSlide[];
}

/**
 * The welcome carousel of the home page (UC-30, D-14): the slides crossfade every 4 seconds under the fixed welcome
 * text, with a pause button first in tab order. Under reduced motion they never advance by themselves. The first
 * photo is the page's largest paint: it is preloaded, loads first and is never animated (RNF-11).
 */
export function HeroCarousel({ onDiscover, slides = HERO_SLIDES }: HeroCarouselProps) {
  const [current, setCurrent] = useState(0);
  const [paused, setPaused] = useState(false);
  const reduced = usePrefersReducedMotion();
  const total = slides.length;
  const rotates = total > 1 && !reduced;
  const rotating = rotates && !paused;
  const first = slides[0]?.photo;
  const entrance = [useEntrance(0), useEntrance(1), useEntrance(2)] as const;

  if (first !== undefined) {
    preload(first.src, { as: 'image', imageSrcSet: first.srcSet, imageSizes: HERO_SIZES, fetchPriority: 'high' });
  }

  useEffect(() => {
    if (!rotating) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setCurrent((current + 1) % total);
    }, SLIDE_MS);
    return () => {
      window.clearTimeout(timer);
    };
  }, [rotating, current, total]);

  return (
    <section
      className="hero"
      aria-roledescription={es.home.carousel.roleDescription}
      aria-label={es.home.carousel.label}
    >
      {rotates && (
        <button
          type="button"
          className="hero-pause"
          aria-label={paused ? es.home.carousel.resume : es.home.carousel.pause}
          onClick={() => {
            setPaused(!paused);
          }}
        >
          <PauseIcon paused={paused} />
        </button>
      )}
      {/* Announces the slide the visitor picks, never the ones that pass by themselves. */}
      <div className="hero-slides" aria-live={rotating ? 'off' : 'polite'}>
        {slides.map(({ tone, photo }, index) => (
          <div
            key={index}
            className={`hero-slide tone-${tone}${index === current ? ' active' : ''}`}
            role="group"
            aria-roledescription={es.home.carousel.slideRoleDescription}
            aria-label={es.home.carousel.slide(index + 1, total)}
            aria-hidden={index !== current}
          >
            {photo !== undefined && (
              <img
                src={photo.src}
                srcSet={photo.srcSet}
                sizes={HERO_SIZES}
                alt={photo.alt}
                loading={index === 0 ? 'eager' : 'lazy'}
                fetchPriority={index === 0 ? 'high' : 'auto'}
                decoding="async"
              />
            )}
          </div>
        ))}
      </div>
      {total > 1 && (
        <div className="hero-dots">
          {slides.map((_, index) => (
            <button
              key={index}
              type="button"
              aria-label={es.home.carousel.showSlide(index + 1, total)}
              aria-current={index === current ? 'true' : undefined}
              onClick={() => {
                setCurrent(index);
              }}
            />
          ))}
        </div>
      )}
      <div className="hero-copy">
        <m.div {...entrance[0]}>
          <p className="hero-eyebrow">{es.home.eyebrow}</p>
          <h1>{es.home.title}</h1>
        </m.div>
        <m.p {...entrance[1]}>{es.home.text}</m.p>
        <m.a
          href="#todos-los-productos"
          className="hero-cta"
          {...entrance[2]}
          onClick={(event) => {
            event.preventDefault();
            onDiscover();
          }}
        >
          {es.home.discover} <span aria-hidden="true">→</span>
        </m.a>
      </div>
    </section>
  );
}
