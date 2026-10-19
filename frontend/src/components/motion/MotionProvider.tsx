import { LazyMotion, MotionConfig } from 'motion/react';
import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';

const MotionReady = createContext(false);

/**
 * The Motion library for the whole app (E7, D-16): the `m` components are in the main bundle, their animation
 * features load after the first paint. Under reduced motion Motion turns transforms off and keeps opacity.
 */
export function MotionProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const features = useCallback(
    () =>
      import('./motionFeatures.ts').then((module) => {
        setReady(true);
        return module.default;
      }),
    [],
  );
  return (
    <LazyMotion features={features} strict>
      <MotionConfig reducedMotion="user">
        <MotionReady value={ready}>{children}</MotionReady>
      </MotionConfig>
    </LazyMotion>
  );
}

/** Whether the animation features are loaded; until then, and without the provider, everything shows in place. */
export function useMotionReady(): boolean {
  return useContext(MotionReady);
}
