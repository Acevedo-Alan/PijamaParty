import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';

/** RNF-31.6 (T-E5-12): the page with shipping, delivery times, payment methods and returns. */
export const FAQ_PATH = '/preguntas-frecuentes';

/** UC-29 and RNF-30.6: the full policy of exchanges and returns (T-E7-28, D-18). */
export const RETURNS_POLICY_PATH = '/cambios-y-devoluciones';

/** RNF-30.6 (T-E7-28): the terms of purchase, linked from the checkout too. */
export const TERMS_PATH = '/terminos';

/** RNF-30.5 (T-E6-06): the privacy policy. */
export const PRIVACY_PATH = '/privacidad';

/** RNF-25 (T-E6-06): the cookies and analytics policy. */
export const COOKIES_PATH = '/cookies';

/** UC-28 (T-E6-06, T-E4-12): the data deletion instructions that Meta requires for Facebook Login. */
export const DATA_DELETION_PATH = '/eliminacion-de-datos';

/** UC-31 (T-E7-29, RNF-31.5): the size guides of every category. */
export const SIZE_GUIDES_PATH = '/guia-de-talles';

/** UC-31 (T-E7-28): the steps of a purchase. */
export const HOW_TO_BUY_PATH = '/como-comprar';

/** The link of the footer of every public page. */
export function FaqLink() {
  return <Link to={FAQ_PATH}>{es.faq.title}</Link>;
}
