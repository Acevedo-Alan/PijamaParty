import { useId } from 'react';
import { Link, NavLink } from 'react-router';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { useWorkloadCounts, type WorkloadCounts } from './dashboardApi.ts';

interface NavEntry {
  to: string;
  label: string;
  /** The count shown beside the entry, when it is above 0. */
  badge?: keyof WorkloadCounts;
}

interface NavGroup {
  /** Null for an entry that stands alone, as "Envíos". */
  title: string | null;
  entries: readonly NavEntry[];
}

/**
 * The back office's sections, grouped (E8a navigation). A data list, so a phase adds its screens here without
 * touching the menu's logic: Resumen, Clientes, Stock and Datos de la tienda join when their tasks arrive.
 */
export const NAV_GROUPS: readonly NavGroup[] = [
  {
    title: esAdmin.navGroups.sales,
    entries: [
      { to: '/admin/pedidos', label: esAdmin.orders, badge: 'paidOrders' },
      { to: '/admin/arrepentimientos', label: esAdmin.withdrawals },
    ],
  },
  {
    title: esAdmin.navGroups.catalog,
    entries: [
      { to: '/admin/productos', label: esAdmin.products },
      { to: '/admin/categorias', label: esAdmin.categories },
    ],
  },
  {
    title: esAdmin.navGroups.marketing,
    entries: [
      { to: '/admin/cupones', label: esAdmin.coupons },
      { to: '/admin/resenas', label: esAdmin.reviews },
      { to: '/admin/portada', label: esAdmin.home },
    ],
  },
  { title: null, entries: [{ to: '/admin/envios', label: esAdmin.shipping }] },
  { title: esAdmin.navGroups.settings, entries: [{ to: '/admin/actividad', label: esAdmin.activity }] },
];

function Entry({ entry, counts, onChoose }: { entry: NavEntry; counts: WorkloadCounts | undefined; onChoose?: () => void }) {
  const count = entry.badge === undefined ? 0 : (counts?.[entry.badge] ?? 0);
  return (
    <li>
      {/* NavLink marks the current page with aria-current="page". */}
      <NavLink to={entry.to} onClick={onChoose}>
        <span>{entry.label}</span>
        {count > 0 && (
          <span className="admin-nav-badge">
            <span aria-hidden="true">{count}</span>
            <span className="visually-hidden">{esAdmin.paidWaiting(count)}</span>
          </span>
        )}
      </NavLink>
    </li>
  );
}

function Group({ group, counts, onChoose }: { group: NavGroup; counts: WorkloadCounts | undefined; onChoose?: () => void }) {
  const titleId = useId();
  const list = (
    <ul aria-labelledby={group.title === null ? undefined : titleId}>
      {group.entries.map((entry) => (
        <Entry key={entry.to} entry={entry} counts={counts} {...(onChoose === undefined ? {} : { onChoose })} />
      ))}
    </ul>
  );
  return (
    <div className="admin-nav-group">
      {group.title !== null && (
        <p id={titleId} className="admin-nav-group-title">
          {group.title}
        </p>
      )}
      {list}
    </div>
  );
}

interface AdminNavProps {
  signingOut: boolean;
  onSignOut: () => void;
  /** The phone panel closes when a link is chosen. */
  onChoose?: () => void;
}

/** The grouped sections, then "Ver tienda" and "Cerrar sesión": the side menu from 860 px and the phone panel. */
export function AdminNav({ signingOut, onSignOut, onChoose }: AdminNavProps) {
  const counts = useWorkloadCounts().data;
  return (
    <nav aria-label={esAdmin.nav} className="admin-nav">
      {NAV_GROUPS.map((group) => (
        <Group
          key={group.entries[0]?.to}
          group={group}
          counts={counts}
          {...(onChoose === undefined ? {} : { onChoose })}
        />
      ))}
      <div className="admin-nav-group admin-nav-end">
        <ul>
          <li>
            <Link to="/" onClick={onChoose}>
              {esAdmin.viewStore}
            </Link>
          </li>
          <li>
            <button type="button" className="link-btn" disabled={signingOut} onClick={onSignOut}>
              {esAdmin.signOut}
            </button>
          </li>
        </ul>
      </div>
    </nav>
  );
}
