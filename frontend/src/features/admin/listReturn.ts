import { useLocation } from 'react-router';

/**
 * UC-06 and UC-18 (decided on 2026-11-04): a back-office list hands its filters (its query string) to the pages it
 * opens, in the navigation state, so their "back to the list" link returns to the same filtered list.
 */
export interface ListReturn {
  listSearch: string;
}

/** The navigation state of a link a list opens: `search` is the list's own query string, `?…` or empty. */
export function listReturn(search: string): ListReturn {
  return { listSearch: search };
}

/** The list's filters from a navigation state, if it carries them; anything else is no filters. */
export function listSearchOf(state: unknown): string {
  if (typeof state === 'object' && state !== null && 'listSearch' in state) {
    const { listSearch } = state;
    if (typeof listSearch === 'string' && (listSearch === '' || listSearch.startsWith('?'))) {
      return listSearch;
    }
  }
  return '';
}

/**
 * Where the "back to the list" link of a detail page goes: `listPath` with the filters the page was opened from, or
 * the unfiltered list when it was opened another way. Not history.back(), which would leave the back office when the
 * page was the first one opened.
 */
export function useBackToList(listPath: string): { to: string; state: ListReturn } {
  const listSearch = listSearchOf(useLocation().state);
  return { to: `${listPath}${listSearch}`, state: listReturn(listSearch) };
}
