import { CardPayment, initMercadoPago } from '@mercadopago/sdk-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { CardDetails } from './cardPaymentApi.ts';

/** The key MercadoPago's SDK was started with; it is started once per key, never on each render. */
let startedWith: string | null = null;

function start(publicKey: string) {
  if (startedWith !== publicKey) {
    initMercadoPago(publicKey, { locale: 'es-AR' });
    startedWith = publicKey;
  }
}

interface CardPaymentFormProps {
  publicKey: string;
  /** The order total, the server's: the form shows it and offers the installments for it. */
  amountCents: number;
  /** Sends the card to the store; the form waits on it, so it shows its own progress meanwhile. */
  onSubmit: (card: CardDetails) => Promise<void>;
  onReady: () => void;
  /** The form could not start or broke; its passing errors (a mistyped field) it shows itself. */
  onFailure: () => void;
}

/**
 * UC-14 (T-E3-29): MercadoPago's card form (Card Payment Brick) inside the store's page. The card number, expiry and
 * security code are typed in MercadoPago's own secure fields (its iframes), which turn them into a single-use token:
 * the store's page and server never see the card. Loaded only when the buyer is about to pay, in its own chunk.
 */
export default function CardPaymentForm({ publicKey, amountCents, onSubmit, onReady, onFailure }: CardPaymentFormProps) {
  useState(() => {
    start(publicKey);
  });
  // The form is rebuilt whenever its settings or callbacks change identity, which would wipe what the buyer typed:
  // the callbacks are stable and read the page's latest ones, and the amount only rebuilds it when it changes. The
  // installments offered are those MercadoPago and the bank allow for it, any interest paid by the buyer (UC-14).
  const latest = useRef({ onSubmit, onReady, onFailure });
  useEffect(() => {
    latest.current = { onSubmit, onReady, onFailure };
  });
  const initialization = useMemo(() => ({ amount: amountCents / 100 }), [amountCents]);
  const ready = useCallback(() => {
    latest.current.onReady();
  }, []);
  const failed = useCallback((error: { type: 'non_critical' | 'critical' }) => {
    if (error.type === 'critical') {
      latest.current.onFailure();
    }
  }, []);
  const submit = useCallback(
    async (form: {
      token: string;
      payment_method_id: string;
      issuer_id: string;
      installments: number;
      payer: { identification?: { type: string; number: string } };
    }) => {
      await latest.current.onSubmit({
        cardToken: form.token,
        paymentMethodId: form.payment_method_id,
        issuerId: form.issuer_id === '' ? null : form.issuer_id,
        installments: form.installments,
        identificationType: form.payer.identification?.type ?? '',
        identificationNumber: form.payer.identification?.number ?? '',
      });
    },
    [],
  );
  return (
    <CardPayment locale="es-AR" initialization={initialization} onReady={ready} onError={failed} onSubmit={submit} />
  );
}
