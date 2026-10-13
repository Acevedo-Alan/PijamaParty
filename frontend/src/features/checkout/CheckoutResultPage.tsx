import { Link, Navigate, useSearchParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import type { TrackingState } from '../orders/OrderTrackingPage.tsx';
import { keptTrackingToken } from './trackingTokenStore.ts';
import './checkout.css';

/**
 * The return from MercadoPago (`back_urls` of T-E3-06, T-E3-11). The provider never gets the tracking token, so the
 * order is the one placed last in this tab; its tracking page shows the real status, which only the webhook sets.
 */
export function CheckoutResultPage() {
  const [params] = useSearchParams();
  const token = keptTrackingToken();
  if (token !== null) {
    const approved = (params.get('collection_status') ?? params.get('status')) === 'approved';
    const state: TrackingState = { paymentApproved: approved };
    return <Navigate to={`/pedido/${token}`} replace state={state} />;
  }
  return (
    <main className="wrap checkout checkout-result">
      <title>{`${es.checkoutResult.title} | ${es.store.name}`}</title>
      <h1>{es.checkoutResult.title}</h1>
      <p>{es.checkoutResult.noOrder}</p>
      <Link to="/" className="btn">
        {es.checkoutResult.backToStore}
      </Link>
    </main>
  );
}
