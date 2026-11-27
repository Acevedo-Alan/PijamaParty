import { useQueries } from '@tanstack/react-query';
import { useEffect, useState, type RefObject } from 'react';
import { fetchProductPage } from './catalogApi.ts';
import { NO_FILTERS, type Audience } from './listingCriteria.ts';

/** How close to the screen the audience cards come before their counts are asked for. */
const NEAR_SCREEN = '300px 0px';

/**
 * True once the element has come within 300 px of the screen. Without IntersectionObserver it never is: the counts
 * are a nicety, not worth a request the visitor may never scroll to.
 */
function useNearScreen(target: RefObject<HTMLElement | null>): boolean {
  const [near, setNear] = useState(false);

  useEffect(() => {
    const element = target.current;
    if (near || element === null || typeof IntersectionObserver === 'undefined') {
      return undefined;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setNear(true);
          observer.disconnect();
        }
      },
      { rootMargin: NEAR_SCREEN },
    );
    observer.observe(element);
    return () => {
      observer.disconnect();
    };
  }, [near, target]);

  return near;
}

/**
 * How many products the listing shows for each audience (T-E7-85): the `total` of its first page (D-11), the same
 * "N productos" the listing shows once the card is chosen. Asked for only once the cards come near the screen, never
 * in the first load (RNF-11). Undefined while loading or when it cannot be loaded.
 */
export function useAudienceCounts(
  audiences: readonly Audience[],
  section: RefObject<HTMLElement | null>,
): (number | undefined)[] {
  const near = useNearScreen(section);
  const results = useQueries({
    queries: audiences.map((audience) => ({
      queryKey: ['catalog', 'audience-count', audience] as const,
      queryFn: async ({ signal }: { signal: AbortSignal }) => {
        const page = await fetchProductPage(
          undefined,
          { filters: { ...NO_FILTERS, audiences: [audience] }, sort: 'NEWEST' },
          0,
          signal,
        );
        return page.status === 'listed' ? page.total : null;
      },
      enabled: near,
      staleTime: 5 * 60 * 1000,
    })),
  });
  return results.map((result) => (typeof result.data === 'number' ? result.data : undefined));
}
