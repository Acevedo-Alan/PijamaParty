/** The paths of the account area (T-E7-60), apart so the sign-out flow (T-E7-66) reads them without a cycle. */
export const ADDRESSES_PATH = '/cuenta/direcciones';
export const ORDERS_PATH = '/cuenta/pedidos';
export const ACCOUNT_PATH = '/cuenta';
/** "Datos y acceso" (T-E7-62): the personal data, the sign-in methods and the privacy actions. */
export const DATA_PATH = '/cuenta/datos';

/**
 * The pages of the account area (T-E7-60): the summary, "Datos y acceso", the address book, the orders and each
 * order's detail; not the sign-in, registration or link pages under /cuenta.
 */
export function isAccountAreaPath(pathname: string): boolean {
  const path = pathname.replace(/\/+$/, '') || '/';
  return (
    path === ACCOUNT_PATH ||
    path === DATA_PATH ||
    path === ADDRESSES_PATH ||
    path === ORDERS_PATH ||
    path.startsWith(`${ORDERS_PATH}/`)
  );
}
