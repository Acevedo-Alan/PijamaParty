import { api } from '../../api/client.ts';
import type { components } from '../../api/schema.d.ts';

/** Why MercadoPago rejected a card (UC-14, T-E3-29); the page says each one plainly. */
export type CardRejection = NonNullable<components['schemas']['CardPaymentResponse']['rejection']>;

/** POST /api/orders/{token}/card-payment: the charge's result, or why nothing was charged. */
export type CardPaymentAnswer =
  | { status: 'approved' }
  | { status: 'inProcess' }
  | { status: 'rejected'; reason: CardRejection }
  | { status: 'notPayable' }
  | { status: 'providerUnavailable' }
  | { status: 'tooManyAttempts' }
  | { status: 'notFound' };

/** What MercadoPago's card form gives for the card: never the card itself, a single-use token. */
export interface CardDetails {
  cardToken: string;
  paymentMethodId: string;
  issuerId: string | null;
  installments: number;
  identificationType: string;
  identificationNumber: string;
}

/**
 * Charges the card for the order's total (the server's, never an amount from here). `idempotencyKey` is one per
 * submit of the form: a network retry of the same submit never charges twice (RNF-20).
 */
export async function payWithCard(token: string, idempotencyKey: string, card: CardDetails): Promise<CardPaymentAnswer> {
  const { data, response } = await api.POST('/api/orders/{token}/card-payment', {
    params: { path: { token }, header: { 'Idempotency-Key': idempotencyKey } },
    body: {
      cardToken: card.cardToken,
      paymentMethodId: card.paymentMethodId,
      ...(card.issuerId === null ? {} : { issuerId: card.issuerId }),
      installments: card.installments,
      identificationType: card.identificationType,
      identificationNumber: card.identificationNumber,
    },
  });
  if (data !== undefined) {
    switch (data.status) {
      case 'APPROVED':
        return { status: 'approved' };
      case 'IN_PROCESS':
        return { status: 'inProcess' };
      case 'REJECTED':
        return { status: 'rejected', reason: data.rejection ?? 'OTHER' };
      default:
        throw new Error('A card payment without a status');
    }
  }
  switch (response.status) {
    case 409:
      return { status: 'notPayable' };
    case 503:
      return { status: 'providerUnavailable' };
    case 429:
      return { status: 'tooManyAttempts' };
    case 404:
      return { status: 'notFound' };
    default:
      throw new Error(`The card payment failed with status ${String(response.status)}`);
  }
}

/** The public key of MercadoPago's card form (T-E3-29); null when the store has none configured. */
export async function fetchCardFormKey(signal?: AbortSignal): Promise<string | null> {
  const { data, response } = await api.GET('/api/checkout/payment-providers', { signal: signal ?? null });
  if (data === undefined) {
    throw new Error(`The payment providers failed with status ${String(response.status)}`);
  }
  return data.mercadoPagoPublicKey ?? null;
}
