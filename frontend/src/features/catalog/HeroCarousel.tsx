import { m } from 'motion/react';
import { useEffect, useRef, useState, type RefObject } from 'react';
import { preload } from 'react-dom';
import { useActionMotion } from '../../components/motion/useActionMotion.ts';
import { usePrefersReducedMotion } from '../../components/motion/usePrefersReducedMotion.ts';
import { es } from '../../i18n/es.ts';
import { duration, ease } from '../../styles/motion.ts';
import { HERO_SLIDES, type HeroSlide } from './homeContent.ts';
import { useSwipe } from './useSwipe.ts';

/** D-14: a slide stays 4 seconds. */
export const SLIDE_MS = 4000;

const HERO_SIZES = '100vw';
/** A focus this soon after a press on the carousel is the press's: a phone focuses the tapped dot after the lift. */
const PRESS_FOCUS_MS = 1000;
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

/** At least a third of it on screen, in a visible tab: what nobody sees does not advance. */
function useSeen(target: RefObject<HTMLElement | null>): boolean {
  const [onScreen, setOnScreen] = useState(true);
  const [tabShown, setTabShown] = useState(() => document.visibilityState !== 'hidden');

  useEffect(() => {
    const element = target.current;
    if (element === null || typeof IntersectionObserver === 'undefined') {
      return undefined;
    }
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry !== undefined) {
          setOnScreen(entry.isIntersecting);
        }
      },
      { threshold: 1 / 3 },
    );
    observer.observe(element);
    return () => {
      observer.disconnect();
    };
  }, [target]);

  useEffect(() => {
    function change() {
      setTabShown(document.visibilityState !== 'hidden');
    }
    document.addEventListener('visibilitychange', change);
    return () => {
      document.removeEventListener('visibilitychange', change);
    };
  }, []);

  return onScreen && tabShown;
}

interface HeroCarouselProps {
  /** "Descubrí la colección": the page takes the visitor to its listing. */
  onDiscover: () => void;
  slides?: readonly HeroSlide[];
}

/**
 * The welcome carousel of the home page (UC-30, D-14): the slides crossfade every 4 seconds under the fixed welcome
 * text, with a pause button first in tab order, and a swipe moves between them. It waits while it is used or not
 * seen, and the current pill fills over the slide's time. Under reduced motion they never advance by themselves. The
 * first photo is the page's largest paint: it is preloaded, loads first and is never animated (RNF-11).
 */
export function HeroCarousel({ onDiscover, slides = HERO_SLIDES }: HeroCarouselProps) {
  const [current, setCurrent] = useState(0);
  const [paused, setPaused] = useState(false);
  // HIG and WCAG 2.2.2: it waits while it is used, pressed or not seen (UC-30).
  const [focused, setFocused] = useState(false);
  const [held, setHeld] = useState(false);
  /**
   * When a pointer was last pressed on the carousel: a focus that follows soon after is that press's, not the
   * keyboard's. On a phone the tap focuses only after the finger lifts, so "while pressed" would not do.
   */
  const lastPress = useRef(Number.NEGATIVE_INFINITY);
  const section = useRef<HTMLElement>(null);
  const pauseButton = useRef<HTMLButtonElement>(null);
  const seen = useSeen(section);
  const reduced = usePrefersReducedMotion();
  const total = slides.length;
  const rotates = total > 1 && !reduced;
  const rotating = rotates && !paused && !focused && !held && seen;
  /** The time a slide has left; another slide starts full, and one that waited resumes with it, as its pill does. */
  const remaining = useRef({ slide: current, ms: SLIDE_MS });
  // The fade cannot follow the finger, so the swipe does not re-render on every move.
  const swipe = useSwipe(current, total, setCurrent, true, false);
  const first = slides[0]?.photo;
  const entrance = [useEntrance(0), useEntrance(1), useEntrance(2)] as const;

  if (first !== undefined) {
    preload(first.src, { as: 'image', imageSrcSet: first.srcSet, imageSizes: HERO_SIZES, fetchPriority: 'high' });
  }

  useEffect(() => {
    if (!rotating) {
      return undefined;
    }
    const left = remaining.current.slide === current ? remaining.current.ms : SLIDE_MS;
    const started = Date.now();
    const timer = window.setTimeout(() => {
      setCurrent((shown) => (shown + 1) % total);
    }, left);
    return () => {
      window.clearTimeout(timer);
      remaining.current = { slide: current, ms: Math.max(0, left - (Date.now() - started)) };
    };
  }, [rotating, current, total]);

  // The finger can lift outside the carousel, where the section's own pointerup would never see it.
  useEffect(() => {
    if (!held) {
      return undefined;
    }
    function release() {
      setHeld(false);
    }
    window.addEventListener('pointerup', release);
    window.addEventListener('pointercancel', release);
    return () => {
      window.removeEventListener('pointerup', release);
      window.removeEventListener('pointercancel', release);
    };
  }, [held]);

  return (
    <section
      ref={section}
      className="hero"
      aria-roledescription={es.home.carousel.roleDescription}
      aria-label={es.home.carousel.label}
      {...swipe.handlers}
      onPointerDown={(event) => {
        setHeld(true);
        lastPress.current = performance.now();
        swipe.handlers.onPointerDown(event);
      }}
      // The keyboard's focus only: a dot tapped or clicked keeps the focus, and would stop it until a tap away. The
      // pause button controls the carousel: waiting while it has the focus would undo "Reanudar".
      onFocus={(event) => {
        const pressed = performance.now() - lastPress.current < PRESS_FOCUS_MS;
        setFocused(event.target !== pauseButton.current && !pressed);
      }}
      onBlur={(event) => {
        if (!(event.relatedTarget instanceof Node && event.currentTarget.contains(event.relatedTarget))) {
          setFocused(false);
        }
      }}
    >
      {rotates && (
        <button
          ref={pauseButton}
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
        <div className={rotating ? 'hero-dots' : 'hero-dots waiting'}>
          {slides.map((_, index) => (
            <button
              key={index}
              type="button"
              aria-label={es.home.carousel.showSlide(index + 1, total)}
              aria-current={index === current ? 'true' : undefined}
              className={rotates && index === current ? 'timed' : undefined}
              onClick={() => {
                setCurrent(index);
              }}
            >
              {/* The slide's time filling the current pill, restarted per slide (keyed by it). */}
              {rotates && index === current && (
                <span key={current} className="hero-dot-progress" style={{ animationDuration: `${String(SLIDE_MS)}ms` }} />
              )}
            </button>
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
