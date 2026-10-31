import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Link, Navigate, Outlet, useLocation, useNavigate } from 'react-router';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { NoIndex } from '../seo/NoIndex.tsx';
import { AdminMenuPanel } from './AdminMenuPanel.tsx';
import { AdminNav } from './AdminNav.tsx';
import { onSessionExpired } from './adminSession.ts';
import { signOut } from './authApi.ts';
import { useSession, type SignInState } from './useSession.ts';
import './admin.css';

/** The page's content, where "Ir al contenido" goes. */
const MAIN_ID = 'admin-content';

function AccessDenied() {
  return (
    <main className="wrap admin-main">
      <NoIndex />
      <h1>{esAdmin.accessDenied.title}</h1>
      <p>{esAdmin.accessDenied.body}</p>
      <Link to="/">{esAdmin.viewStore}</Link>
    </main>
  );
}

/**
 * Every back-office page but the sign-in page (UC-04). Without a session it opens the sign-in page, which returns
 * here once signed in; a customer is denied. An expired session on any later call opens the sign-in page again.
 */
export function AdminLayout() {
  const session = useSession();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const from = location.pathname + location.search;

  useEffect(
    () =>
      onSessionExpired(() => {
        queryClient.removeQueries({ queryKey: ['admin'] });
        const state: SignInState = { from, expired: true };
        void navigate('/admin/ingresar', { replace: true, state });
      }),
    [queryClient, navigate, from],
  );

  const leave = useMutation({
    mutationFn: signOut,
    onSettled: () => {
      queryClient.removeQueries({ queryKey: ['admin'] });
      void navigate('/admin/ingresar', { replace: true });
    },
  });

  if (session.isPending) {
    return (
      <main className="wrap admin-main">
        <NoIndex />
        <p role="status">{esAdmin.loading}</p>
      </main>
    );
  }
  if (session.isError) {
    return (
      <main className="wrap admin-main">
        <NoIndex />
        <div role="alert">
          <p>{esAdmin.loadError}</p>
          <button type="button" className="btn" onClick={() => void session.refetch()}>
            {esAdmin.retry}
          </button>
        </div>
      </main>
    );
  }
  if (session.data.status === 'signedOut') {
    const state: SignInState = { from };
    return <Navigate to="/admin/ingresar" replace state={state} />;
  }
  if (session.data.status === 'forbidden') {
    return <AccessDenied />;
  }

  const signOutProps = {
    signingOut: leave.isPending,
    onSignOut: () => {
      leave.mutate();
    },
  };

  // E8a navigation: the grouped menu at the side from 860 px; under it, behind "Menú" in a panel (CSS picks one).
  return (
    <>
      <NoIndex />
      {/* Past the menu's entries to the page, for the keyboard (the main element takes the focus). */}
      <a className="admin-skip-link" href={`#${MAIN_ID}`}>
        {esAdmin.skipToContent}
      </a>
      <header className="admin-header">
        <div className="admin-header-inner">
          <AdminMenuPanel {...signOutProps} />
          <p className="admin-brand">{esAdmin.title}</p>
        </div>
      </header>
      <div className="admin-shell">
        <div className="admin-side">
          <AdminNav {...signOutProps} />
        </div>
        <main id={MAIN_ID} className="admin-main" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </>
  );
}
