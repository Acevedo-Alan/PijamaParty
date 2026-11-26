import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, ChevronDown, ChevronRight } from 'lucide-react';
import { useId, useState } from 'react';
import { Link } from 'react-router';
import { LazyChibi } from '../../components/chibi/LazyChibi.tsx';
import { Button } from '../../components/ui/Button.tsx';
import { Card, CardContent, CardTitle } from '../../components/ui/Card.tsx';
import { cn } from '../../components/ui/cn.ts';
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

/** T-E8-32: whether the steps were left open, per browser; without storage the card starts collapsed. */
const OPEN_KEY = 'pp-admin-setup-open';

function wasOpen(): boolean {
  try {
    return window.localStorage.getItem(OPEN_KEY) === '1';
  } catch {
    return false;
  }
}

function rememberOpen(open: boolean): void {
  try {
    window.localStorage.setItem(OPEN_KEY, open ? '1' : '0');
  } catch {
    // It starts collapsed next time.
  }
}

export function useSetupGuide() {
  return useQuery({ queryKey: SETUP_GUIDE_KEY, queryFn: ({ signal }) => fetchSetupGuide(signal), retry: false });
}

/** A row's state mark: decorative, the row's hidden text says the state. */
function StateMark({ state }: { state: SetupEntry['state'] }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full border-2',
        state === 'DONE' && 'border-[var(--success)] bg-[var(--success)] text-white',
        state === 'PENDING' && 'border-[var(--line-strong)]',
        state === 'OMITTED' && 'border-dashed border-[var(--line-strong)]',
      )}
    >
      {state === 'DONE' && <Check className="size-3" strokeWidth={3} />}
    </span>
  );
}

interface RowProps {
  entry: SetupEntry;
  busy: boolean;
  onChange: (omit: boolean) => void;
}

/** One step: its mark, its title (with how many, for the two that count) and reason, its actions on the right. */
function StepRow({ entry, busy, onChange }: RowProps) {
  const texts = messages.items[entry.item];
  const action = 'action' in texts ? texts.action : messages.configure;
  const counted = entry.state === 'PENDING' && entry.total > 0;
  return (
    <li className="flex items-start gap-3 border-b border-border py-3 last:border-b-0">
      <StateMark state={entry.state} />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <p className={cn('m-0 text-sm font-semibold', entry.state !== 'PENDING' && 'text-muted-foreground')}>
          <span className="visually-hidden">{`${messages.states[entry.state]}: `}</span>
          {texts.title}
          {counted && <span className="font-normal text-muted-foreground tabular-nums">{` (${String(entry.total)})`}</span>}
        </p>
        {entry.state === 'PENDING' && <p className="m-0 text-[13px] text-muted-foreground">{texts.why}</p>}
      </div>
      <div className="flex shrink-0 flex-wrap items-center justify-end gap-1">
        {entry.state === 'PENDING' && (
          <Button asChild variant="secondary" size="sm">
            <Link to={FIX_AT[entry.item]} aria-label={messages.actionLabel(action, texts.title)}>
              {action}
              <ChevronRight aria-hidden="true" />
            </Link>
          </Button>
        )}
        {entry.state === 'PENDING' && OMITTABLE.has(entry.item) && (
          <Button
            variant="ghost"
            size="sm"
            className="text-muted-foreground"
            aria-label={messages.omitLabel(texts.title)}
            aria-disabled={busy}
            onClick={() => {
              if (!busy) {
                onChange(true);
              }
            }}
          >
            {messages.omit}
          </Button>
        )}
        {entry.state === 'OMITTED' && (
          <Button
            variant="ghost"
            size="sm"
            aria-label={messages.showAgainLabel(texts.title)}
            aria-disabled={busy}
            onClick={() => {
              if (!busy) {
                onChange(false);
              }
            }}
          >
            {messages.showAgain}
          </Button>
        )}
      </div>
    </li>
  );
}

/**
 * UC-46, T-E8-32: at the top of the summary, collapsed to one line with the chibi, "Terminá de configurar tu tienda",
 * its progress and "Ver pasos", so the day's figures stay in sight; expanded, the six steps in rows of one pattern.
 * Instagram and the banner can be omitted, kept on the server. With nothing pending, one line and the chibi
 * celebrating. Its own read: only this card says when it fails.
 */
export function SetupGuideCard() {
  const titleId = useId();
  const stepsId = useId();
  const [open, setOpen] = useState(wasOpen);
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
        <CardContent className="flex flex-row flex-wrap items-center gap-3">
          <CardTitle id={titleId} className="text-[15px]">
            {messages.title}
          </CardTitle>
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
  const rows = (
    <ul id={stepsId} hidden={!open} className="m-0 list-none p-0">
      {entries.map((entry) => (
        <StepRow
          key={entry.item}
          entry={entry}
          busy={change.isPending}
          onChange={(omit) => {
            change.mutate({ item: entry.item, omit });
          }}
        />
      ))}
    </ul>
  );

  if (pending.length === 0) {
    return (
      <Card aria-labelledby={titleId}>
        <CardContent className="flex flex-col gap-2">
          <div className="flex flex-wrap items-center gap-3">
            <LazyChibi mood="success" pose="guide" className="size-10 shrink-0" />
            <CardTitle id={titleId} className="flex-1 text-[15px]">
              {messages.allDone}
            </CardTitle>
            {/* With something omitted, its steps stay reachable to show it again. */}
            {omitted.length > 0 && (
              <StepsToggle open={open} stepsId={stepsId} onToggle={setOpen} />
            )}
          </div>
          {omitted.length > 0 && rows}
          {failed}
        </CardContent>
      </Card>
    );
  }

  return (
    <Card aria-labelledby={titleId}>
      <CardContent className="flex flex-col gap-2">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          <LazyChibi mood="idle" pose="guide" className="size-10 shrink-0" />
          <div className="flex min-w-48 flex-1 flex-col gap-1.5">
            <CardTitle id={titleId} className="text-[15px]">
              {messages.title}
            </CardTitle>
            <div className="flex items-center gap-3">
              <progress
                className="setup-progress"
                value={ready}
                max={entries.length}
                aria-label={messages.ready(ready, entries.length)}
              />
              <span className="shrink-0 text-[13px] text-muted-foreground tabular-nums" aria-hidden="true">
                {messages.ready(ready, entries.length)}
              </span>
            </div>
          </div>
          <StepsToggle open={open} stepsId={stepsId} onToggle={setOpen} />
        </div>
        {rows}
        {failed}
      </CardContent>
    </Card>
  );
}

function StepsToggle({ open, stepsId, onToggle }: { open: boolean; stepsId: string; onToggle: (open: boolean) => void }) {
  return (
    <Button
      variant="ghost"
      size="sm"
      aria-expanded={open}
      aria-controls={stepsId}
      onClick={() => {
        rememberOpen(!open);
        onToggle(!open);
      }}
    >
      {open ? messages.hideSteps : messages.showSteps}
      <ChevronDown aria-hidden="true" className={cn('transition-transform', open && 'rotate-180')} />
    </Button>
  );
}
