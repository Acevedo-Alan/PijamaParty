import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';

/** RNF-31.6 (T-E5-12): the page with shipping, delivery times, payment methods and returns. */
export const FAQ_PATH = '/preguntas-frecuentes';

/** UC-29 and RNF-30.6: the full policy of exchanges and returns (T-E7-28, D-18). */
export const RETURNS_POLICY_PATH = '/cambios-y-devoluciones';

/** RNF-30.6 (T-E7-28): the terms of purchase, linked from the checkout too. */
export const TERMS_PATH = '/terminos';

/** UC-31 (T-E7-28): the steps of a purchase. */
export const HOW_TO_BUY_PATH = '/como-comprar';

/** The link of the footer of every public page. */
export function FaqLink() {
  return <Link to={FAQ_PATH}>{es.faq.title}</Link>;
}
