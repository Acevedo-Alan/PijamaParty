import { Outlet, useNavigation } from 'react-router';
import { NightLoader } from '../../components/NightLoader.tsx';
import { es } from '../../i18n/es.ts';
import './routeStates.css';

/** Root of every route: a progress bar while the next page's chunk loads (T-E8-21). */
export function RootLayout() {
  const navigating = useNavigation().state !== 'idle';
  return (
    <>
      <div className="route-progress" data-active={navigating} aria-hidden="true" />
      <div role="status" className="visually-hidden">
        {navigating ? es.routeError.loading : ''}
      </div>
      <Outlet />
    </>
  );
}

/** First visit to a lazy page: nothing is rendered yet, so say so instead of showing a blank screen. */
export function RouteLoadingFallback() {
  return (
    <main className="wrap route-loading" aria-busy="true">
      <NightLoader>{es.routeError.loading}</NightLoader>
    </main>
  );
}
