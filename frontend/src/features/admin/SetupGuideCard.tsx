import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronRight } from 'lucide-react';
import { useId, useState } from 'react';
import { Link } from 'react-router';
import { LazyChibi } from '../../components/chibi/LazyChibi.tsx';
import { Button } from '../../components/ui/Button.tsx';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/Card.tsx';
import { esAdmin } from '../../i18n/esAdmin.ts';
import {
  fetchSetupGuide,
  OMITTABLE,
  omitSetupItem,
  SETUP_GUIDE_KEY,
  showSetupItem,
  type SetupEntry,
  type SetupItem,
} from './setupGuideApi.ts';

const messages = esAdmin.dashboard.setupGuide;

/** Where each item is fixed (UC-46). */
const FIX_AT: Record<SetupItem, string> = {
  WHATSAPP: '/admin/datos-de-la-tienda',
  EMAIL: '/admin/datos-de-la-tienda',
  SIZE_GUIDES: '/admin/categorias',
  PRODUCT_PHOTOS: '/admin/productos',
  INSTAGRAM: '/admin/datos-de-la-tienda',
  HOME_BANNER: '/admin/portada',
};

/** Each name an item lists leads to its own screen: a category's size guide, a product's edit page. */
function nameLink(item: SetupItem, id: string): string {
  return item === 'SIZE_GUIDES' ? `/admin/categorias/${id}/guia-de-talles` : `/admin/productos/${id}`;
}

export function useSetupGuide() {
  return useQuery({ queryKey: SETUP_GUIDE_KEY, queryFn: ({ signal }) => fetchSetupGuide(signal), retry: false });
}

/** A pending item: its title links to where it is fixed, its reason below; the names it lists, each a link. */
function PendingItem({ entry, onOmit, busy }: { entry: SetupEntry; onOmit: () => void; busy: boolean }) {
  const texts = messages.items[entry.item];
  const more = entry.total - entry.names.length;
  return (
    <li className="flex flex-col gap-1 border-b border-border py-3 last:border-b-0">
      <div className="flex items-start justify-between gap-3">
        <Link
          to={FIX_AT[entry.item]}
          className="inline-flex min-h-11 items-center gap-1 text-sm font-semibold text-foreground no-underline hover:text-primary"
        >
          {texts.title}
          {entry.total > 0 && <span className="text-muted-foreground tabular-nums">({entry.total})</span>}
          <ChevronRight aria-hidden="true" className="size-4 shrink-0" />
        </Link>
        {OMITTABLE.has(entry.item) && (
          <Button
            variant="link"
            size="sm"
            className="shrink-0 text-muted-foreground"
            aria-label={messages.omitLabel(texts.title)}
            aria-disabled={busy}
            onClick={() => {
              if (!busy) {
                onOmit();
              }
            }}
          >
            {messages.omit}
          </Button>
        )}
      </div>
      <p className="m-0 text-[13px] text-muted-foreground">{texts.why}</p>
      {entry.names.length > 0 && (
        <p className="m-0 flex flex-wrap gap-x-2 gap-y-1 text-[13px]">
          {entry.names.map((name, index) => (
            <span key={name.id}>
              <Link to={nameLink(entry.item, name.id)}>{name.name}</Link>
              {index < entry.names.length - 1 || more > 0 ? ',' : ''}
            </span>
          ))}
          {more > 0 && <Link to={FIX_AT[entry.item]}>{messages.more(more)}</Link>}
        </p>
      )}
    </li>
  );
}

/** The omitted items, each with "Volver a mostrar", behind "Ver omitidos (N)". */
function OmittedItems({ omitted, onShow, busy }: { omitted: SetupEntry[]; onShow: (item: SetupItem) => void; busy: boolean }) {
  const [open, setOpen] = useState(false);
  const listId = useId();
  if (omitted.length === 0) {
    return null;
  }
  return (
    <div className="flex flex-col items-start gap-1">
      <Button
        variant="link"
        size="sm"
        aria-expanded={open}
        aria-controls={listId}
        onClick={() => {
          setOpen(!open);
        }}
      >
        {open ? messages.hideOmitted : messages.showOmitted(omitted.length)}
      </Button>
      <ul id={listId} hidden={!open} className="m-0 flex w-full list-none flex-col p-0">
        {omitted.map((entry) => {
          const title = messages.items[entry.item].title;
          return (
            <li key={entry.item} className="flex min-h-11 items-center justify-between gap-3 text-sm text-muted-foreground">
              {title}
              <Button
                variant="link"
                size="sm"
                aria-label={messages.showAgainLabel(title)}
                aria-disabled={busy}
                onClick={() => {
                  if (!busy) {
                    onShow(entry.item);
                  }
                }}
              >
                {messages.showAgain}
              </Button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

/**
 * UC-46: "Tu tienda, lista" at the top of the summary. The store's chibi, holding a clipboard, lists what the store is
 * still missing, read from its own data; Instagram and the banner can be omitted, kept on the server. With nothing
 * pending, one line and the chibi celebrating. Its own read: only this card says when it fails.
 */
export function SetupGuideCard() {
  const titleId = useId();
  const guide = useSetupGuide();
  const queryClient = useQueryClient();
  const change = useMutation({
    mutationFn: ({ item, omit }: { item: SetupItem; omit: boolean }) =>
      omit ? omitSetupItem(item) : showSetupItem(item),
    onSettled: () => queryClient.invalidateQueries({ queryKey: SETUP_GUIDE_KEY }),
  });

  if (guide.isPending) {
    return null;
  }
  if (guide.isError) {
    return (
      <Card aria-labelledby={titleId}>
        <CardHeader>
          <CardTitle id={titleId}>{messages.title}</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col items-start gap-2">
          <p className="m-0 text-sm" role="alert">
            {messages.loadError}
          </p>
          <Button size="sm" onClick={() => void guide.refetch()}>
            {esAdmin.retry}
          </Button>
        </CardContent>
      </Card>
    );
  }

  const entries = guide.data;
  const pending = entries.filter((entry) => entry.state === 'PENDING');
  const omitted = entries.filter((entry) => entry.state === 'OMITTED');
  const ready = entries.length - pending.length;
  const failed = change.isError && (
    <p className="m-0 text-sm text-destructive-strong" role="alert">
      {messages.saveError}
    </p>
  );

  if (pending.length === 0) {
    return (
      <Card aria-labelledby={titleId}>
        {/* The card keeps its name for screen readers; the line says it is done. */}
        <CardTitle id={titleId} className="visually-hidden">
          {messages.title}
        </CardTitle>
        <CardContent className="flex-row flex-wrap items-center gap-3">
          <LazyChibi mood="success" pose="guide" className="size-12 shrink-0" />
          <p className="m-0 text-[15px] font-bold">{messages.allDone}</p>
          <OmittedItems omitted={omitted} busy={change.isPending} onShow={(item) => { change.mutate({ item, omit: false }); }} />
          {failed}
        </CardContent>
      </Card>
    );
  }

  return (
    <Card aria-labelledby={titleId}>
      <CardHeader className="flex-nowrap items-center justify-start gap-4">
        <LazyChibi mood="idle" pose="guide" className="size-24 shrink-0" />
        <div className="flex flex-col gap-1">
          <CardTitle id={titleId}>{messages.title}</CardTitle>
          <CardDescription className="tabular-nums">{messages.ready(ready, entries.length)}</CardDescription>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-2">
        <ul className="m-0 flex list-none flex-col p-0">
          {pending.map((entry) => (
            <PendingItem
              key={entry.item}
              entry={entry}
              busy={change.isPending}
              onOmit={() => {
                change.mutate({ item: entry.item, omit: true });
              }}
            />
          ))}
        </ul>
        <OmittedItems omitted={omitted} busy={change.isPending} onShow={(item) => { change.mutate({ item, omit: false }); }} />
        {failed}
      </CardContent>
    </Card>
  );
}
