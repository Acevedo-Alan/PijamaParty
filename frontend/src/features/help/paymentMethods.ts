import { es } from '../../i18n/es.ts';
import type { PaymentProvider } from '../checkout/checkoutForm.ts';

const DISJUNCTION = new Intl.ListFormat('es', { type: 'disjunction' });

/** The names of the payment methods as one phrase, e.g. "MercadoPago o MODO". */
export function paymentMethodNames(providers: readonly PaymentProvider[]): string {
  return DISJUNCTION.format(providers.map((provider) => es.checkout.providers[provider].name));
}
