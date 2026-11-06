import { useInfiniteQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { Field, FieldInput, FieldLabel, FieldSelect } from '../../components/ui/Field.tsx';
import { FilterBar } from '../../components/ui/FilterBar.tsx';
import { es } from '../../i18n/es.ts';
import { ListSkeleton } from '../../components/ui/Skeleton.tsx';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { formatDateTime } from '../../i18n/formatDate.ts';
import {
  ACTIVITY_AREAS,
  fetchActivity,
  type ActivityArea,
  type ActivityChange,
  type ActivityFilter,
} from './activityApi.ts';
import { subjectLabel, subjectPath } from './activitySubjects.ts';

const messages = esAdmin.activityList;
/** The recorded names translated, with the order statuses and coupon types in the words of their own screens. */
const values: Readonly<Partial<Record<string, Readonly<Partial<Record<string, string>>>>>> = {
  ...messages.values,
  status: es.tracking.statuses,
  type: esAdmin.couponForm.types,
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

function changeText({ field, before, after }: ActivityChange): string {
  const value = (text: string | null) => (text === null ? messages.noValue : (values[field]?.[text] ?? text));
  return messages.change(messages.fields[field] ?? field, value(before), value(after));
}

/**
 * UC-37 (T-E8-01) at /admin/actividad: the changes made from the back office, newest first, 50 at a time, filtered
 * by area and by store dates if chosen, each with what it was about and what changed.
 */
export function ActivityPage() {
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
      <title>{`${messages.title} | ${esAdmin.title}`}</title>
      <div className="admin-title-row">
        <h1>{messages.title}</h1>
      </div>
      {/* The filters apply as they change; the shared bar links the period's error to the dates (T-E8-14). */}
      <FilterBar
        aria-label={messages.filters}
        onSubmit={(event) => {
          event.preventDefault();
        }}
      >
        <Field>
          <FieldLabel>{messages.area}</FieldLabel>
          <FieldSelect
            name={AREA_PARAM}
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
          </FieldSelect>
        </Field>
        <Field error={invalidPeriod ? messages.invalidPeriod : undefined}>
          <FieldLabel>{messages.from}</FieldLabel>
          <FieldInput
            name={FROM_PARAM}
            autoComplete="off"
            type="date"
            value={filter.from ?? ''}
            onChange={(event) => {
              change(FROM_PARAM, event.target.value);
            }}
          />
        </Field>
        <Field>
          <FieldLabel>{messages.to}</FieldLabel>
          <FieldInput
            name={TO_PARAM}
            autoComplete="off"
            type="date"
            value={filter.to ?? ''}
            onChange={(event) => {
              change(TO_PARAM, event.target.value);
            }}
          />
        </Field>
      </FilterBar>
      {!invalidPeriod && activity.isPending && <ListSkeleton />}
      {activity.isError && items.length === 0 && (
        <div role="alert">
          <p>{esAdmin.loadError}</p>
          <button type="button" className="btn" onClick={() => void activity.refetch()}>
            {esAdmin.retry}
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
      {activity.isFetchNextPageError && <p role="alert">{esAdmin.loadError}</p>}
      {activity.hasNextPage && (
        <button
          type="button"
          className="btn btn-secondary show-more-btn"
          disabled={activity.isFetchingNextPage}
          onClick={() => void activity.fetchNextPage()}
        >
          {activity.isFetchingNextPage ? esAdmin.loading : esAdmin.showMore}
        </button>
      )}
    </>
  );
}
