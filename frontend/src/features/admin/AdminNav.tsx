import {
  Activity,
  ExternalLink,
  House,
  Image,
  LayoutGrid,
  LogOut,
  Package,
  ShoppingBag,
  Star,
  Store,
  Ticket,
  Truck,
  Undo2,
  type LucideIcon,
} from 'lucide-react';
import { useId } from 'react';
import { Link, NavLink } from 'react-router';
import { cn } from '../../components/ui/cn.ts';
import { esAdmin } from '../../i18n/esAdmin.ts';
import { useWorkloadCounts, type WorkloadCounts } from './dashboardApi.ts';
import { useSession } from './useSession.ts';

interface NavEntry {
  to: string;
  label: string;
  /** One small icon per entry (D-E8-12), decorative: the label names it. */
  icon: LucideIcon;
  /** The count shown beside the entry, when it is above 0. */
  badge?: keyof WorkloadCounts;
  /** Marked current only on its own path, not on the paths below it ("Resumen" at /admin). */
  end?: boolean;
}

interface NavGroup {
  /** Null for an entry that stands alone, as "Resumen". */
  title: string | null;
  entries: readonly NavEntry[];
}

/**
 * The back office's sections, grouped (E8a navigation). A data list, so a phase adds its screens here without
 * touching the menu's logic: Clientes, Stock and Datos de la tienda join when their tasks arrive.
 */
export const NAV_GROUPS: readonly NavGroup[] = [
  { title: null, entries: [{ to: '/admin', label: esAdmin.summary, icon: House, end: true }] },
  {
    title: esAdmin.navGroups.sales,
    entries: [
      { to: '/admin/pedidos', label: esAdmin.orders, icon: ShoppingBag, badge: 'paidOrders' },
      { to: '/admin/arrepentimientos', label: esAdmin.withdrawals, icon: Undo2 },
    ],
  },
  {
    title: esAdmin.navGroups.catalog,
    entries: [
      { to: '/admin/productos', label: esAdmin.products, icon: Package },
      { to: '/admin/categorias', label: esAdmin.categories, icon: LayoutGrid },
    ],
  },
  {
    title: esAdmin.navGroups.marketing,
    entries: [
      { to: '/admin/cupones', label: esAdmin.coupons, icon: Ticket },
      { to: '/admin/resenas', label: esAdmin.reviews, icon: Star },
      { to: '/admin/portada', label: esAdmin.home, icon: Image },
    ],
  },
  {
    title: esAdmin.navGroups.store,
    entries: [
      { to: '/admin/envios', label: esAdmin.shipping, icon: Truck },
      { to: '/admin/datos-de-la-tienda', label: esAdmin.storeSettings, icon: Store },
      { to: '/admin/actividad', label: esAdmin.activity, icon: Activity },
    ],
  },
];

/** An entry: 44 px tall (Apple HIG), the current page tinted as a selected row. */
const ENTRY = cn(
  'flex min-h-11 items-center gap-2.5 rounded-md px-2.5 text-sm font-medium text-foreground no-underline',
  'transition-colors hover:bg-muted [&_svg]:size-[18px] [&_svg]:shrink-0 [&_svg]:text-muted-foreground',
  'aria-[current=page]:bg-accent aria-[current=page]:font-semibold aria-[current=page]:text-accent-foreground',
  'aria-[current=page]:[&_svg]:text-accent-foreground',
);

function Entry({ entry, counts, onChoose }: { entry: NavEntry; counts: WorkloadCounts | undefined; onChoose?: () => void }) {
  const count = entry.badge === undefined ? 0 : (counts?.[entry.badge] ?? 0);
  const Icon = entry.icon;
  return (
    <li>
      {/* NavLink marks the current page with aria-current="page". */}
      <NavLink to={entry.to} end={entry.end === true} onClick={onChoose} className={ENTRY}>
        <Icon aria-hidden="true" />
        <span className="flex-1">{entry.label}</span>
        {/* A space for screen readers between the label and the count's words; the flex gap spaces it on screen */}
        {count > 0 && ' '}
        {count > 0 && (
          <span className="grid h-5 min-w-5 place-items-center rounded-full bg-primary px-1.5 text-[11px] font-bold text-primary-foreground tabular-nums">
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
  return (
    <div className="flex flex-col gap-0.5">
      {group.title !== null && (
        <p
          id={titleId}
          className="m-0 mt-3 mb-1 px-2.5 text-[11px] font-semibold tracking-wider text-muted-foreground uppercase"
        >
          {group.title}
        </p>
      )}
      <ul className="m-0 flex list-none flex-col gap-0.5 p-0" aria-labelledby={group.title === null ? undefined : titleId}>
        {group.entries.map((entry) => (
          <Entry key={entry.to} entry={entry} counts={counts} {...(onChoose === undefined ? {} : { onChoose })} />
        ))}
      </ul>
    </div>
  );
}

/** The two first letters of the administrator's name, for the avatar. */
function initials(name: string): string {
  const letters = name.split(/\s+/).filter(Boolean).map((word) => word[0]?.toUpperCase() ?? '');
  return letters.slice(0, 2).join('') || 'A';
}

interface AdminNavProps {
  signingOut: boolean;
  onSignOut: () => void;
  /** The phone panel closes when a link is chosen. */
  onChoose?: () => void;
}

/**
 * The grouped sections, then "Ver tienda" and who is signed in, with "Cerrar sesión" (D-E8-12): the side menu from
 * 860 px and the phone panel.
 */
export function AdminNav({ signingOut, onSignOut, onChoose }: AdminNavProps) {
  const counts = useWorkloadCounts().data;
  const session = useSession().data;
  const name = session?.status === 'signedIn' && session.administrator.name !== '' ? session.administrator.name : esAdmin.administrator;
  return (
    <nav aria-label={esAdmin.nav} className="admin-nav flex min-h-full flex-col gap-1">
      {NAV_GROUPS.map((group) => (
        <Group
          key={group.entries[0]?.to}
          group={group}
          counts={counts}
          {...(onChoose === undefined ? {} : { onChoose })}
        />
      ))}
      <div className="mt-auto flex flex-col gap-1 border-t border-border pt-3">
        <ul className="m-0 list-none p-0">
          <li>
            <Link to="/" onClick={onChoose} className={ENTRY}>
              <ExternalLink aria-hidden="true" />
              {esAdmin.viewStore}
            </Link>
          </li>
        </ul>
        <div className="flex items-center gap-2.5 px-2.5 py-2">
          <span
            aria-hidden="true"
            className="grid size-9 shrink-0 place-items-center rounded-full bg-secondary text-[13px] font-bold text-primary-text"
          >
            {initials(name)}
          </span>
          <span className="flex min-w-0 flex-1 flex-col">
            <span className="truncate text-sm font-semibold">{name}</span>
            <span className="text-xs text-muted-foreground">{esAdmin.owner}</span>
          </span>
          <button
            type="button"
            aria-label={esAdmin.signOut}
            title={esAdmin.signOut}
            disabled={signingOut}
            onClick={onSignOut}
            className="grid size-11 shrink-0 cursor-pointer place-items-center rounded-md border-0 bg-transparent text-muted-foreground transition-colors hover:bg-muted hover:text-foreground disabled:opacity-60 [&_svg]:size-[18px]"
          >
            <LogOut aria-hidden="true" />
          </button>
        </div>
      </div>
    </nav>
  );
}
