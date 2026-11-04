import { useLocation } from 'react-router';

/** T-E8-09: the lists a page can be opened from other than its own, which its back link then returns to. */
export const STOCK_LIST = '/admin/stock';
type OtherList = typeof STOCK_LIST;

/**
 * UC-06, UC-07 and UC-18 (decided on 2026-11-04): a back-office list hands its filters (its query string) to the
 * pages it opens, in the navigation state, so their "back to the list" link returns to the same filtered list.
 * `listPath` names the list when it is not the page's own (the Stock screen opening a variant's history).
 */
export interface ListReturn {
  listSearch: string;
  listPath?: OtherList;
}

/** The navigation state of a link a list opens: `search` is the list's own query string, `?…` or empty. */
export function listReturn(search: string, listPath?: OtherList): ListReturn {
  return listPath === undefined ? { listSearch: search } : { listSearch: search, listPath };
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

/** The other list a navigation state names, if it is one of those a page can come from. */
export function listPathOf(state: unknown): OtherList | null {
  if (typeof state === 'object' && state !== null && 'listPath' in state && state.listPath === STOCK_LIST) {
    return STOCK_LIST;
  }
  return null;
}

/**
 * Where the "back to the list" link of a detail page goes: `listPath` with the filters the page was opened from, or
 * the unfiltered list when it was opened another way. Not history.back(), which would leave the back office when the
 * page was the first one opened. `state` carries the filters on to the pages this one opens.
 */
export function useBackToList(listPath: string): { to: string; state: ListReturn } {
  const listSearch = listSearchOf(useLocation().state);
  return { to: `${listPath}${listSearch}`, state: listReturn(listSearch) };
}

/** A customer's profile: the only page other than a list an order can be opened from. */
const CUSTOMER_PROFILE = /^\/admin\/clientes\/[0-9a-fA-F-]{36}$/;

/**
 * UC-18, UC-34 (decided on 2026-11-04): an order opened from a customer's profile returns to that profile, which in
 * turn still returns to "Clientes" with the filters it was opened from (`profileState`).
 */
export interface ProfileReturn {
  profilePath: string;
  profileState: ListReturn;
}

/** The navigation state of an order link on a customer's profile. */
export function profileReturn(profilePath: string, profileState: ListReturn): ProfileReturn {
  return { profilePath, profileState };
}

/** The profile a navigation state names, if it is a customer's profile; anything else is none. */
export function profileReturnOf(state: unknown): ProfileReturn | null {
  if (typeof state === 'object' && state !== null && 'profilePath' in state && 'profileState' in state) {
    const { profilePath, profileState } = state;
    if (typeof profilePath === 'string' && CUSTOMER_PROFILE.test(profilePath)) {
      return profileReturn(profilePath, listReturn(listSearchOf(profileState)));
    }
  }
  return null;
}

/**
 * Where the back link of an order (and of its packing slip, through the order) goes: the customer's profile it was
 * opened from, or the orders list with its filters. `state` goes with the back link; `passOn` with the links to the
 * order's own pages, so coming back from them still knows the way.
 */
export function useOrderBack(orderList: string): {
  from: 'list' | 'customer';
  to: string;
  state: ListReturn;
  passOn: ListReturn | ProfileReturn;
} {
  const state: unknown = useLocation().state;
  const list = useBackToList(orderList);
  const profile = profileReturnOf(state);
  return profile === null
    ? { from: 'list', to: list.to, state: list.state, passOn: list.state }
    : { from: 'customer', to: profile.profilePath, state: profile.profileState, passOn: profile };
}
