import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Link, Navigate, NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { NoIndex } from '../seo/NoIndex.tsx';
import { onSessionExpired } from './adminSession.ts';
import { signOut } from './authApi.ts';
import { useSession, type SignInState } from './useSession.ts';
import './admin.css';

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

  return (
    <>
      <NoIndex />
      <header className="admin-header">
        <div className="wrap admin-header-inner">
          <p className="admin-brand">{esAdmin.title}</p>
          <nav aria-label={esAdmin.nav} className="admin-nav">
            <NavLink to="/admin/pedidos">{esAdmin.orders}</NavLink>
            <NavLink to="/admin/arrepentimientos">{esAdmin.withdrawals}</NavLink>
            <NavLink to="/admin/productos">{esAdmin.products}</NavLink>
            <NavLink to="/admin/categorias">{esAdmin.categories}</NavLink>
            <NavLink to="/admin/cupones">{esAdmin.coupons}</NavLink>
            <NavLink to="/admin/resenas">{esAdmin.reviews}</NavLink>
            <NavLink to="/admin/envios">{esAdmin.shipping}</NavLink>
            <NavLink to="/admin/portada">{esAdmin.home}</NavLink>
            <NavLink to="/admin/actividad">{esAdmin.activity}</NavLink>
            <Link to="/">{esAdmin.viewStore}</Link>
            <button
              type="button"
              className="link-btn"
              disabled={leave.isPending}
              onClick={() => {
                leave.mutate();
              }}
            >
              {esAdmin.signOut}
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
