import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import './withdrawal.css';

/** UC-29: where the withdrawal button opens; RNF-30.1 asks for direct access from the home page and the footer. */
export const WITHDRAWAL_PATH = '/arrepentimiento';

/** The link of the footer of every public page (RNF-30.1). */
export function WithdrawalLink() {
  return <Link to={WITHDRAWAL_PATH}>{es.withdrawal.link}</Link>;
}

/** The visible block of the home page (RNF-30.1). */
export function WithdrawalHomeBlock() {
  return (
    <section className="withdrawal-home" aria-label={es.withdrawal.link}>
      <p>{es.withdrawal.homeText}</p>
      <Link to={WITHDRAWAL_PATH} className="btn btn-secondary">
        {es.withdrawal.link}
      </Link>
    </section>
  );
}
