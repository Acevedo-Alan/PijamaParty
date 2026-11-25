import { CircleHelp } from 'lucide-react';
import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { LazyChibi } from '../../components/chibi/LazyChibi.tsx';
import { Button } from '../../components/ui/Button.tsx';
import { esAdmin } from '../../i18n/esAdmin.ts';

const messages = esAdmin.screenHelp;

/** The 13 screens of the back-office menu, each with its own lines (UC-47). */
export type HelpScreen = keyof typeof messages.screens;

/**
 * UC-47: the "?" after a screen's title. A disclosure, not a dialog: the focus stays on "?" and Tab reaches the card
 * next. "Entendido", Escape, a press outside or "?" again closes it; "Entendido" and Escape give the focus back.
 */
function ScreenHelp({ screen, name }: { screen: HelpScreen; name: string }) {
  const [open, setOpen] = useState(false);
  const cardId = useId();
  const titleId = useId();
  const root = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false);
        button.current?.focus();
      }
    }
    function onPress(event: PointerEvent) {
      if (
        event.target instanceof Node &&
        root.current?.contains(event.target) !== true
      ) {
        setOpen(false);
      }
    }
    document.addEventListener('keydown', onKey);
    document.addEventListener('pointerdown', onPress);
    return () => {
      document.removeEventListener('keydown', onKey);
      document.removeEventListener('pointerdown', onPress);
    };
  }, [open]);

  return (
    <div ref={root} className="screen-help">
      <Button
        ref={button}
        variant="ghost"
        size="icon"
        className="text-muted-foreground"
        aria-label={messages.open(name)}
        aria-expanded={open}
        aria-controls={cardId}
        onClick={() => {
          setOpen(!open);
        }}
      >
        <CircleHelp aria-hidden="true" />
      </Button>
      {/* Always there for aria-controls; its content only while open, so a closed card adds nothing to the page. */}
      <section
        id={cardId}
        hidden={!open}
        aria-labelledby={open ? titleId : undefined}
        className="screen-help-card"
      >
        {open && (
          <>
            <LazyChibi mood="idle" pose="guide" className="size-16 shrink-0" />
            <div className="flex min-w-0 flex-col gap-2">
              <h2 id={titleId} className="m-0 text-[15px] font-bold">
                {name}
              </h2>
              <ul className="m-0 flex list-none flex-col gap-1.5 p-0 text-[13px] text-muted-foreground">
                {messages.screens[screen].map((line) => (
                  <li key={line}>{line}</li>
                ))}
              </ul>
              <Button
                size="sm"
                className="self-start"
                onClick={() => {
                  setOpen(false);
                  button.current?.focus();
                }}
              >
                {messages.close}
              </Button>
            </div>
          </>
        )}
      </section>
    </div>
  );
}

/** A back-office screen's `h1` with its "?" (UC-47); the title keeps its own name. */
export function ScreenTitle({
  screen,
  children,
}: {
  screen: HelpScreen;
  children: string;
}): ReactNode {
  return (
    <div className="screen-title">
      <h1>{children}</h1>
      <ScreenHelp screen={screen} name={children} />
    </div>
  );
}
