/** jsdom has no matchMedia: answers the reduced motion query as a visitor with this setting would. */
export function preferReducedMotion(reduce: boolean) {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: (query: string): MediaQueryList =>
      ({
        matches: reduce && query.includes('prefers-reduced-motion') && !query.includes('no-preference'),
        media: query,
        onchange: null,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        addListener: () => undefined,
        removeListener: () => undefined,
        dispatchEvent: () => false,
      }) satisfies MediaQueryList,
  });
}

export function forgetMotionPreference() {
  Reflect.deleteProperty(window, 'matchMedia');
}
