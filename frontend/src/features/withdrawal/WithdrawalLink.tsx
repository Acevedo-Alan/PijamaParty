import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import './withdrawal.css';

/** UC-29: where the withdrawal button opens; RNF-30.1 asks for direct access from the footer of every page. */
export const WITHDRAWAL_PATH = '/arrepentimiento';

/** The link of the footer of every public page, the home page included (RNF-30.1). */
export function WithdrawalLink() {
  return <Link to={WITHDRAWAL_PATH}>{es.withdrawal.link}</Link>;
}
