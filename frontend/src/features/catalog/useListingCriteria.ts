import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';
import { parseCriteria, toSearchParams, type ListingCriteria } from './listingCriteria.ts';

/**
 * The filters and sort live in the URL, so a shared link shows the same listing and the back button undoes the
 * last change (UC-02).
 */
export function useListingCriteria(): [ListingCriteria, (criteria: ListingCriteria) => void] {
  const [searchParams, setSearchParams] = useSearchParams();
  const query = searchParams.toString();
  const criteria = useMemo(() => parseCriteria(new URLSearchParams(query)), [query]);
  const setCriteria = useCallback(
    (next: ListingCriteria) => {
      setSearchParams(toSearchParams(next));
    },
    [setSearchParams],
  );
  return [criteria, setCriteria];
}
