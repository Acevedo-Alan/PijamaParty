import { useState } from 'react';

export type ListingView = 'grid' | 'list';

const KEY = 'pijamaparty.catalog.view';

function keptView(): ListingView {
  try {
    return sessionStorage.getItem(KEY) === 'list' ? 'list' : 'grid';
  } catch {
    return 'grid';
  }
}

/**
 * Grid or list (changes to UC-01, T-E7-06): kept for the visit, never in the URL. sessionStorage lives as long as the
 * tab, so the choice survives a trip to a product and back; a browser that refuses it starts every listing as a grid.
 */
export function useListingView(): [ListingView, (view: ListingView) => void] {
  const [view, setView] = useState(keptView);
  return [
    view,
    (next) => {
      setView(next);
      try {
        sessionStorage.setItem(KEY, next);
      } catch {
        // Storage blocked or full: the choice lasts until the page changes.
      }
    },
  ];
}
