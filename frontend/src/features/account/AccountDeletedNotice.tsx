import { useLocation } from 'react-router';
import { es } from '../../i18n/es.ts';

/**
 * What the deletion leaves in the navigation state for the page it goes to (UC-28). Its own file: the store layout
 * shows the notice on every page, and the data section it came from brings the account code with it (RNF-11).
 */
export interface AccountDeletedState {
  accountDeleted?: boolean;
}

/** The message of the page the deletion goes to: the home page. */
export function AccountDeletedNotice() {
  const state = useLocation().state as AccountDeletedState | null;
  if (state?.accountDeleted !== true) {
    return null;
  }
  return (
    <p className="wrap account-deleted" role="status">
      {es.account.myAccount.accountDeleted}
    </p>
  );
}
