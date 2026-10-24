import { useInfiniteQuery } from '@tanstack/react-query';
import { useId } from 'react';
import { Link, useSearchParams } from 'react-router';
import { es } from '../../i18n/es.ts';
import { formatDateTime } from '../../i18n/formatDate.ts';
import {
  ACTIVITY_AREAS,
  fetchActivity,
  type ActivityArea,
  type ActivityChange,
  type ActivityEntry,
  type ActivityFilter,
} from './activityApi.ts';

const messages = es.admin.activityList;
const subjectNames: Readonly<Partial<Record<string, string>>> = messages.subjects;
const provinceNames: Readonly<Partial<Record<string, string>>> = es.provinces;
/** The recorded names translated, with the order statuses and coupon types in the words of their own screens. */
const values: Readonly<Partial<Record<string, Readonly<Partial<Record<string, string>>>>>> = {
  ...messages.values,
  status: es.tracking.statuses,
  type: es.admin.couponForm.types,
};
const AREA_PARAM = 'seccion';
const FROM_PARAM = 'desde';
const TO_PARAM = 'hasta';
const DATE = /^\d{4}-\d{2}-\d{2}$/;

/** The filters in the address, so they survive opening a product or an order and coming back. */
function filterOf(params: URLSearchParams): ActivityFilter {
  const area = params.get(AREA_PARAM);
  const date = (name: string) => {
    const value = params.get(name);
    return value !== null && DATE.test(value) ? value : null;
  };
  return {
    area: ACTIVITY_AREAS.find((value) => value === area) ?? null,
    from: date(FROM_PARAM),
    to: date(TO_PARAM),
  };
}

/** The screen of the thing an entry is about, when it has one. */
function subjectPath({ type, id }: ActivityEntry['subject']): string | null {
  switch (type) {
    case 'PRODUCT':
      return id === null ? null : `/admin/productos/${id}`;
    case 'ORDER':
      return id === null ? null : `/admin/pedidos/${id}`;
    case 'CATEGORY':
      return '/admin/categorias';
    case 'WITHDRAWAL':
      return '/admin/arrepentimientos';
    case 'COUPON':
      return '/admin/cupones';
    case 'REVIEW':
      return '/admin/resenas';
    case 'SHIPPING':
      return '/admin/envios';
    case 'HOME_BANNER':
      return '/admin/portada';
    default:
      return null;
  }
}

/** How the screen names an entry's subject: its label (a province translated), or else its section. */
function subjectLabel({ type, label }: ActivityEntry['subject']): string | null {
  if (label === null) {
    return subjectNames[type] ?? null;
  }
  return type === 'SHIPPING' ? (provinceNames[label] ?? label) : label;
}

function changeText({ field, before, after }: ActivityChange): string {
  const value = (text: string | null) => (text === null ? messages.noValue : (values[field]?.[text] ?? text));
  return messages.change(messages.fields[field] ?? field, value(before), value(after));
}

/**
 * UC-37 (T-E8-01) at /admin/actividad: the changes made from the back office, newest first, 50 at a time, filtered
 * by area and by store dates if chosen, each with what it was about and what changed.
 */
export function ActivityPage() {
  const areaId = useId();
  const fromId = useId();
  const toId = useId();
  const [params, setParams] = useSearchParams();
  const filter = filterOf(params);
  const invalidPeriod = filter.from !== null && filter.to !== null && filter.from > filter.to;
  const filtered = filter.area !== null || filter.from !== null || filter.to !== null;
  const activity = useInfiniteQuery({
    queryKey: ['admin', 'activity', 'list', filter.area, filter.from, filter.to],
    queryFn: ({ pageParam, signal }) => fetchActivity(filter, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last, _pages, page) => (last.hasMore ? page + 1 : undefined),
    enabled: !invalidPeriod,
  });
  const items = activity.data?.pages.flatMap((page) => page.items) ?? [];

  function change(name: string, value: string) {
    const next = new URLSearchParams(params);
    if (value === '') {
      next.delete(name);
    } else {
      next.set(name, value);
    }
    setParams(next, { replace: true });
  }

  return (
    <>
      <title>{`${messages.title} | ${es.admin.title}`}</title>
      <div className="admin-title-row">
        <h1>{messages.title}</h1>
      </div>
      <div className="activity-filters">
        <div className="field">
          <label htmlFor={areaId}>{messages.area}</label>
          <select
            id={areaId}
            value={filter.area ?? ''}
            onChange={(event) => {
              change(AREA_PARAM, event.target.value);
            }}
          >
            <option value="">{messages.all}</option>
            {ACTIVITY_AREAS.map((value: ActivityArea) => (
              <option key={value} value={value}>
                {messages.areas[value]}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor={fromId}>{messages.from}</label>
          <input
            id={fromId}
            type="date"
            value={filter.from ?? ''}
            aria-invalid={invalidPeriod || undefined}
            onChange={(event) => {
              change(FROM_PARAM, event.target.value);
            }}
          />
        </div>
        <div className="field">
          <label htmlFor={toId}>{messages.to}</label>
          <input
            id={toId}
            type="date"
            value={filter.to ?? ''}
            aria-invalid={invalidPeriod || undefined}
            onChange={(event) => {
              change(TO_PARAM, event.target.value);
            }}
          />
        </div>
      </div>
      {invalidPeriod && (
        <p className="field-error" role="alert">
          {messages.invalidPeriod}
        </p>
      )}
      {!invalidPeriod && activity.isPending && <p role="status">{es.admin.loading}</p>}
      {activity.isError && items.length === 0 && (
        <div role="alert">
          <p>{es.admin.loadError}</p>
          <button type="button" className="btn" onClick={() => void activity.refetch()}>
            {es.admin.retry}
          </button>
        </div>
      )}
      {activity.isSuccess && items.length === 0 && <p>{filtered ? messages.emptyFiltered : messages.empty}</p>}
      {items.length > 0 && (
        <ul className="admin-list">
          {items.map((entry) => {
            const path = subjectPath(entry.subject);
            const label = subjectLabel(entry.subject);
            return (
              <li key={entry.id} className="admin-card">
                <div className="admin-card-head">
                  <span className="admin-card-title">{messages.actions[entry.action]}</span>
                  <span className="admin-badge">{messages.areas[entry.area]}</span>
                </div>
                <p className="admin-card-meta">
                  <time dateTime={entry.occurredAt.toISOString()}>{formatDateTime(entry.occurredAt)}</time>
                  {label !== null && (
                    <>
                      {' · '}
                      {path === null ? <span>{label}</span> : <Link to={path}>{label}</Link>}
                    </>
                  )}
                </p>
                {entry.changes.length > 0 && (
                  <ul className="activity-changes">
                    {entry.changes.map((fieldChange) => (
                      <li key={fieldChange.field}>{changeText(fieldChange)}</li>
                    ))}
                  </ul>
                )}
              </li>
            );
          })}
        </ul>
      )}
      {activity.hasNextPage && (
        <button
          type="button"
          className="btn btn-secondary show-more-btn"
          disabled={activity.isFetchingNextPage}
          onClick={() => void activity.fetchNextPage()}
        >
          {activity.isFetchingNextPage ? es.admin.loading : es.admin.showMore}
        </button>
      )}
    </>
  );
}
