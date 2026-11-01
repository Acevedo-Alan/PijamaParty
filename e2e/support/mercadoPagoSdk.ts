import { E2E_CARD_TOKENS } from '../stubs/settings.ts';

/** Where MercadoPago's SDK loads from (@mercadopago/sdk-js); the tests answer it with the fake below. */
export const MERCADOPAGO_SDK_URL = 'https://sdk.mercadopago.com/js/v2';

/**
 * T-E3-31: a fake of MercadoPago's SDK, enough for the store's card form (Card Payment Brick): window.MercadoPago,
 * whose bricks().create('cardPayment', container, settings) draws two buttons and reports the form ready. Each button
 * submits what the real form gives for a card (a token, the brand, the installments and the holder's ID), with the
 * token the MercadoPago stub knows: approved, or without funds. The real SDK needs the internet and real credentials;
 * the sandbox purchase of T-E3-31 checks it.
 */
export const mercadoPagoSdk = `
(() => {
  const tokens = ${JSON.stringify(E2E_CARD_TOKENS)};
  class MercadoPago {
    constructor(publicKey) {
      this.publicKey = publicKey;
    }
    bricks() {
      return {
        create: async (name, containerId, settings) => {
          const container = document.getElementById(containerId);
          const callbacks = settings.callbacks;
          const button = (label, token) => {
            const element = document.createElement('button');
            element.type = 'button';
            element.textContent = label;
            element.addEventListener('click', () => {
              void callbacks.onSubmit({
                token,
                issuer_id: '310',
                payment_method_id: 'visa',
                transaction_amount: settings.initialization.amount,
                installments: 1,
                payer: { email: 'buyer@example.com', identification: { type: 'DNI', number: '30123456' } },
              });
            });
            return element;
          };
          container.replaceChildren(
            button('Pay with an approved card', tokens.approved),
            button('Pay with a card without funds', tokens.withoutFunds),
          );
          callbacks.onReady();
          return {
            unmount: () => container.replaceChildren(),
            update: () => undefined,
          };
        },
      };
    }
  }
  window.MercadoPago = MercadoPago;
})();
`;
