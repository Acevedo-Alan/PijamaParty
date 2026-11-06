import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { payWithCard, type CardDetails } from './cardPaymentApi.ts';

const CARD: CardDetails = {
  cardToken: 'card-token-1',
  paymentMethodId: 'visa',
  issuerId: null,
  installments: 1,
  identificationType: 'DNI',
  identificationNumber: '12345678',
};

function refused(status: number, error: unknown) {
  return { data: undefined, error, response: new Response(null, { status }) } as never;
}

describe('payWithCard', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given a 409 CHARGE_IN_PROGRESS, when a card is sent, then another charge of the order is in progress', async () => {
    vi.spyOn(api, 'POST').mockResolvedValue(refused(409, { problem: 'CHARGE_IN_PROGRESS' }));

    await expect(payWithCard('token', 'key', CARD)).resolves.toEqual({ status: 'chargeInProgress' });
  });

  it('given a 409 NOT_PAYABLE, when a card is sent, then the order is no longer payable', async () => {
    vi.spyOn(api, 'POST').mockResolvedValue(refused(409, { problem: 'NOT_PAYABLE' }));

    await expect(payWithCard('token', 'key', CARD)).resolves.toEqual({ status: 'notPayable' });
  });
});
