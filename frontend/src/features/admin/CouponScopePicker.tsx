import { useId, useState } from 'react';
import { es } from '../../i18n/es.ts';
import { MAX_SCOPE_TARGETS, type CouponScope } from './couponsApi.ts';
import { useCategories } from './useCategories.ts';
import { useProductSummaries } from './useProductSummaries.ts';

const messages = es.admin.couponForm;

interface Target {
  id: string;
  name: string;
}

/** The catalog's targets, after the picked ones it no longer has, labeled as removed so they can be unticked. */
function targetsOf(known: readonly Target[], picked: readonly string[]): Target[] {
  const knownIds = new Set(known.map((target) => target.id));
  const removed = picked.filter((id) => !knownIds.has(id)).map((id) => ({ id, name: messages.removed }));
  return [...removed, ...known];
}

function normalized(text: string): string {
  return text.trim().toLocaleLowerCase('es');
}

interface CheckListProps {
  targets: readonly Target[];
  picked: readonly string[];
  /** The scope has its most targets: only unticking is allowed. */
  full: boolean;
  onToggle: (id: string, picked: boolean) => void;
}

function CheckList({ targets, picked, full, onToggle }: CheckListProps) {
  return (
    <ul className="check-list">
      {targets.map((target) => {
        const checked = picked.includes(target.id);
        return (
          <li key={target.id}>
            <label className="switch">
              <input
                type="checkbox"
                checked={checked}
                disabled={!checked && full}
                onChange={(event) => {
                  onToggle(target.id, event.target.checked);
                }}
              />
              <span>{target.name}</span>
            </label>
          </li>
        );
      })}
    </ul>
  );
}

interface CouponScopePickerProps {
  scope: CouponScope;
  onChange: (scope: CouponScope) => void;
  /** The server refused the scope (`SCOPE`). */
  invalid: boolean;
}

/**
 * UC-11, UC-23: the categories and products an automatic coupon discounts, picked from the catalog's back-office
 * lists. Nothing picked is the whole cart. A picked target the catalog no longer has shows as "Eliminado".
 */
export function CouponScopePicker({ scope, onChange, invalid }: CouponScopePickerProps) {
  const categories = useCategories();
  const products = useProductSummaries();
  const [filter, setFilter] = useState('');
  const hintId = useId();
  const errorId = useId();
  const filterId = useId();
  const count = scope.categoryIds.length + scope.productIds.length;
  const full = count >= MAX_SCOPE_TARGETS;

  function toggle(key: keyof CouponScope, id: string, picked: boolean) {
    const others = scope[key].filter((other) => other !== id);
    onChange({ ...scope, [key]: picked ? [...others, id] : others });
  }

  const query = normalized(filter);
  const productTargets = targetsOf(products.data ?? [], scope.productIds).filter(
    (target) => query === '' || normalized(target.name).includes(query),
  );

  return (
    <fieldset className="scope-picker" aria-describedby={invalid ? `${hintId} ${errorId}` : hintId}>
      <legend>{messages.scope}</legend>
      <p id={hintId} className="field-hint">
        {messages.scopeHint}
      </p>
      <p className="field-hint" aria-live="polite">
        {messages.scopeCount(count)}
      </p>
      {invalid && (
        <p id={errorId} className="field-error">
          {messages.errors.SCOPE}
        </p>
      )}
      {(categories.isPending || products.isPending) && <p role="status">{es.admin.loading}</p>}
      {(categories.isError || products.isError) && (
        <div role="alert">
          <p>{messages.scopeLoadError}</p>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => {
              void categories.refetch();
              void products.refetch();
            }}
          >
            {es.admin.retry}
          </button>
        </div>
      )}
      {categories.isSuccess && products.isSuccess && (
        <>
          <fieldset>
            <legend>{messages.categories}</legend>
            <CheckList
              targets={targetsOf(categories.data, scope.categoryIds)}
              picked={scope.categoryIds}
              full={full}
              onToggle={(id, picked) => {
                toggle('categoryIds', id, picked);
              }}
            />
          </fieldset>
          <fieldset>
            <legend>{messages.products}</legend>
            <div className="field">
              <label htmlFor={filterId}>{messages.productFilter}</label>
              <input
                id={filterId}
                type="search"
                value={filter}
                autoComplete="off"
                onChange={(event) => {
                  setFilter(event.target.value);
                }}
              />
            </div>
            {productTargets.length === 0 ? (
              <p className="field-hint">{messages.noProducts}</p>
            ) : (
              <CheckList
                targets={productTargets}
                picked={scope.productIds}
                full={full}
                onToggle={(id, picked) => {
                  toggle('productIds', id, picked);
                }}
              />
            )}
          </fieldset>
        </>
      )}
    </fieldset>
  );
}
