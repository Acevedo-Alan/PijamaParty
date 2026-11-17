import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client.ts';
import { cardTypeOf, payWithCard, type CardDetails } from './cardPaymentApi.ts';

const CARD: CardDetails = {
  cardToken: 'card-token-1',
  paymentMethodId: 'visa',
  paymentType: 'credit_card',
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

  it('given a debit card, when it is sent, then the store is told it is a debit card (T-E3-34)', async () => {
    const post = vi.spyOn(api, 'POST').mockResolvedValue(refused(409, { problem: 'NOT_PAYABLE' }));

    await payWithCard('token', 'key', { ...CARD, paymentMethodId: 'debvisa', paymentType: 'debit_card' });

    expect(post.mock.calls[0]?.[1]).toMatchObject({ body: { paymentMethodId: 'debvisa', paymentType: 'debit_card' } });
  });
});

describe('cardTypeOf (T-E3-34)', () => {
  it('given the form tells the card type, when read, then it is that one', () => {
    expect(cardTypeOf('debit_card', 'visa')).toBe('debit_card');
    expect(cardTypeOf('credit_card', 'debvisa')).toBe('credit_card');
  });

  it('given the form does not tell it, when read, then MercadoPago debit brands are debit and the rest credit', () => {
    expect(cardTypeOf(undefined, 'debvisa')).toBe('debit_card');
    expect(cardTypeOf(undefined, 'debmaster')).toBe('debit_card');
    expect(cardTypeOf(undefined, 'maestro')).toBe('debit_card');
    expect(cardTypeOf(undefined, 'master')).toBe('credit_card');
    expect(cardTypeOf('prepaid_card', 'visa')).toBe('credit_card');
  });
});
