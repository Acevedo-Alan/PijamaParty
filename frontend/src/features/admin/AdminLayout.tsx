import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Link, Navigate, NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import { es } from '../../i18n/es.ts';
import { onSessionExpired } from './adminSession.ts';
import { signOut } from './authApi.ts';
import { useSession, type SignInState } from './useSession.ts';
import './admin.css';

/** Not for search engines: the back office has nothing public. */
export function NoIndex() {
  return <meta name="robots" content="noindex" />;
}

function AccessDenied() {
  return (
    <main className="wrap admin-main">
      <NoIndex />
      <h1>{es.admin.accessDenied.title}</h1>
      <p>{es.admin.accessDenied.body}</p>
      <Link to="/">{es.admin.viewStore}</Link>
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
        <p role="status">{es.admin.loading}</p>
      </main>
    );
  }
  if (session.isError) {
    return (
      <main className="wrap admin-main">
        <NoIndex />
        <div role="alert">
          <p>{es.admin.loadError}</p>
          <button type="button" className="btn" onClick={() => void session.refetch()}>
            {es.admin.retry}
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

  return (
    <>
      <NoIndex />
      <header className="admin-header">
        <div className="wrap admin-header-inner">
          <p className="admin-brand">{es.admin.title}</p>
          <nav aria-label={es.admin.nav} className="admin-nav">
            <NavLink to="/admin/pedidos">{es.admin.orders}</NavLink>
            <NavLink to="/admin/arrepentimientos">{es.admin.withdrawals}</NavLink>
            <NavLink to="/admin/productos">{es.admin.products}</NavLink>
            <NavLink to="/admin/categorias">{es.admin.categories}</NavLink>
            <NavLink to="/admin/cupones">{es.admin.coupons}</NavLink>
            <NavLink to="/admin/resenas">{es.admin.reviews}</NavLink>
            <NavLink to="/admin/envios">{es.admin.shipping}</NavLink>
            <NavLink to="/admin/portada">{es.admin.home}</NavLink>
            <Link to="/">{es.admin.viewStore}</Link>
            <button
              type="button"
              className="link-btn"
              disabled={leave.isPending}
              onClick={() => {
                leave.mutate();
              }}
            >
              {es.admin.signOut}
            </button>
          </nav>
        </div>
      </header>
      <main className="wrap admin-main">
        <Outlet />
      </main>
    </>
  );
}
