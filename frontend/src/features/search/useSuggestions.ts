import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { searchText } from '../catalog/listingCriteria.ts';
import { fetchSuggestions } from './searchApi.ts';

/** Suggestions wait this long after the last keystroke (E1 plan, UC-26). */
export const SUGGESTION_DELAY_MS = 250;
/** Shorter texts are not searched (UC-26). */
export const MIN_TEXT_LENGTH = 2;

/** The text once it has stopped changing for {@link SUGGESTION_DELAY_MS}. */
function useSettled(text: string | undefined): string | undefined {
  const [settled, setSettled] = useState(text);
  useEffect(() => {
    const timer = setTimeout(() => {
      setSettled(text);
    }, SUGGESTION_DELAY_MS);
    return () => {
      clearTimeout(timer);
    };
  }, [text]);
  return settled;
}

/** True when the text is long enough to search: at least 2 characters once trimmed. */
export function isSearchable(text: string | undefined): text is string {
  return text !== undefined && Array.from(text).length >= MIN_TEXT_LENGTH;
}

/**
 * The suggestions for what the visitor is typing. Nothing is asked under 2 characters, and a failed request shows no
 * suggestions instead of an error: the field keeps working.
 */
export function useSuggestions(typed: string) {
  const settled = useSettled(searchText(typed));
  const query = useQuery({
    queryKey: ['catalog', 'suggestions', settled ?? null],
    queryFn: ({ signal }) => fetchSuggestions(settled ?? '', signal),
    enabled: isSearchable(settled),
    retry: false,
    staleTime: 60_000,
  });
  return isSearchable(settled) && query.isSuccess ? query.data : [];
}
