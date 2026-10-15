import { api } from '../../api/client.ts';

/** POST /api/withdrawals (UC-29, T-E3-25): the request's procedure code, or why there is none. */
export type WithdrawalAnswer =
  | { status: 'requested'; code: string }
  | { status: 'alreadyRequested'; code: string }
  | { status: 'noMatchingOrder' }
  | { status: 'notWithdrawable' }
  | { status: 'tooManyAttempts' };

/** The order number and the email go as typed: the server reads `#1001` and normalizes the email. */
export async function requestWithdrawal(orderNumber: string, email: string): Promise<WithdrawalAnswer> {
  const { data, response } = await api.POST('/api/withdrawals', { body: { orderNumber, email } });
  if (data?.code !== undefined) {
    return response.status === 201
      ? { status: 'requested', code: data.code }
      : { status: 'alreadyRequested', code: data.code };
  }
  switch (response.status) {
    case 422:
      return { status: 'noMatchingOrder' };
    case 409:
      return { status: 'notWithdrawable' };
    case 429:
      return { status: 'tooManyAttempts' };
    default:
      throw new Error(`Requesting the withdrawal failed with status ${String(response.status)}`);
  }
}
