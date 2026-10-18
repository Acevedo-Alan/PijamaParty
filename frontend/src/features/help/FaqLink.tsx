import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';

/** RNF-31.6 (T-E5-12): the page with shipping, delivery times, payment methods and returns. */
export const FAQ_PATH = '/preguntas-frecuentes';

/** UC-29 and RNF-30.6: the full policy of exchanges and returns, a page of T-E6-06. */
export const RETURNS_POLICY_PATH = '/cambios-y-devoluciones';

/** The link of the footer of every public page. */
export function FaqLink() {
  return <Link to={FAQ_PATH}>{es.faq.title}</Link>;
}
