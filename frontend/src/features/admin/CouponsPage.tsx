import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useId, useState } from 'react';
import { es } from '../../i18n/es.ts';
import { formatMoney } from '../../i18n/formatMoney.ts';
import { CouponForm } from './CouponForm.tsx';
import {
  createCoupon,
  deleteCoupon,
  setCouponActive,
  updateCoupon,
  type Coupon,
  type DeleteAnswer,
} from './couponsApi.ts';
import { Feedback, type Notice } from './Feedback.tsx';
import { COUPONS_KEY, useCoupons } from './useCoupons.ts';

const messages = es.admin.couponList;

/** An ISO day ("2026-12-01") as the shop writes dates ("01/12/2026"), without time zones in between. */
function formatDay(isoDay: string): string {
  const [year = '', month = '', day = ''] = isoDay.split('-');
  return `${day}/${month}/${year}`;
}

function discountText(coupon: Coupon): string {
  return coupon.type === 'PERCENTAGE'
    ? messages.percentOff(coupon.discountValue)
    : messages.amountOff(formatMoney(coupon.discountValue));
}

function validityText({ validFrom, validUntil }: Coupon): string {
  if (validFrom !== null && validUntil !== null) {
    return messages.validBetween(formatDay(validFrom), formatDay(validUntil));
  }
  if (validFrom !== null) {
    return messages.validFrom(formatDay(validFrom));
  }
  if (validUntil !== null) {
    return messages.validUntil(formatDay(validUntil));
  }
  return messages.noExpiry;
}

function deleteProblem(answer: DeleteAnswer): string | null {
  switch (answer.status) {
    case 'deleted':
      return null;
    case 'hasUses':
      return messages.hasUses;
    case 'notFound':
      return messages.notFound;
  }
}

interface RowProps {
  coupon: Coupon;
  busy: boolean;
  onSaved: () => void;
  onToggleActive: () => void;
  onDelete: () => void;
}

function CouponRow({ coupon, busy, onSaved, onToggleActive, onDelete }: RowProps) {
  const [editing, setEditing] = useState(false);

  if (editing) {
    return (
      <li className="admin-card">
        <p className="admin-card-title">{messages.editing(coupon.code)}</p>
        <CouponForm
          initial={coupon}
          submitLabel={es.admin.save}
          onSubmit={(data) => updateCoupon(coupon.id, data)}
          onSaved={() => {
            setEditing(false);
            onSaved();
          }}
          onCancel={() => {
            setEditing(false);
          }}
        />
      </li>
    );
  }

  return (
    <li className="admin-card">
      <div className="admin-card-head">
        <p className="admin-card-title">{coupon.code}</p>
        <p className="admin-card-meta">{discountText(coupon)}</p>
        {!coupon.active && <span className="admin-badge">{messages.inactive}</span>}
      </div>
      <p className="admin-card-meta">{validityText(coupon)}</p>
      {coupon.minimumPurchaseCents !== null && (
        <p className="admin-card-meta">{messages.minimum(formatMoney(coupon.minimumPurchaseCents))}</p>
      )}
      <p className="admin-card-meta">{messages.uses(coupon.uses, coupon.maxUses)}</p>
      <div className="admin-actions">
        <button
          type="button"
          className="btn btn-secondary"
          disabled={busy}
          onClick={() => {
            setEditing(true);
          }}
        >
          {messages.edit}
        </button>
        <button type="button" className="btn btn-secondary" disabled={busy} onClick={onToggleActive}>
          {coupon.active ? messages.deactivate : messages.activate}
        </button>
        <button type="button" className="btn btn-secondary danger" disabled={busy} onClick={onDelete}>
          {messages.delete}
        </button>
      </div>
    </li>
  );
}

/**
 * UC-11 at /admin/cupones: create, edit, activate, deactivate and delete coupons, and see how many times each one was
 * used. A used coupon cannot be deleted; the message offers to deactivate it.
 */
export function CouponsPage() {
  const coupons = useCoupons();
  const queryClient = useQueryClient();
  const [notice, setNotice] = useState<Notice | null>(null);
  const newTitleId = useId();

  function reload() {
    return queryClient.invalidateQueries({ queryKey: COUPONS_KEY });
  }

  // Every change of the list runs one at a time and reloads it, which the server orders.
  const change = useMutation({
    mutationFn: (action: () => Promise<string | null>) => action(),
    onSuccess: (problem) => {
      setNotice(problem === null ? { kind: 'ok', text: es.admin.saved } : { kind: 'error', text: problem });
    },
    onError: () => {
      setNotice({ kind: 'error', text: es.admin.actionError });
    },
    onSettled: reload,
  });

  function run(action: () => Promise<string | null>) {
    setNotice(null);
    change.mutate(action);
  }

  return (
    <>
      <title>{`${messages.title} | ${es.admin.title}`}</title>
      <h1>{messages.title}</h1>
      <section className="admin-section" aria-labelledby={newTitleId}>
        <h2 id={newTitleId}>{messages.newCoupon}</h2>
        <CouponForm
          initial={null}
          submitLabel={messages.create}
          onSubmit={createCoupon}
          onSaved={() => void reload()}
        />
      </section>
      <Feedback notice={notice} />
      {coupons.isPending && <p role="status">{es.admin.loading}</p>}
      {coupons.isError && (
        <div role="alert">
          <p>{es.admin.loadError}</p>
          <button type="button" className="btn" onClick={() => void coupons.refetch()}>
            {es.admin.retry}
          </button>
        </div>
      )}
      {coupons.data?.length === 0 && <p>{messages.empty}</p>}
      {coupons.data !== undefined && coupons.data.length > 0 && (
        <ul className="admin-list">
          {coupons.data.map((coupon) => (
            <CouponRow
              key={coupon.id}
              coupon={coupon}
              busy={change.isPending}
              onSaved={() => {
                setNotice({ kind: 'ok', text: es.admin.saved });
                void reload();
              }}
              onToggleActive={() => {
                run(async () => ((await setCouponActive(coupon.id, !coupon.active)) ? null : messages.notFound));
              }}
              onDelete={() => {
                if (window.confirm(messages.confirmDelete(coupon.code))) {
                  run(async () => deleteProblem(await deleteCoupon(coupon.id)));
                }
              }}
            />
          ))}
        </ul>
      )}
    </>
  );
}
