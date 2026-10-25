import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { m, type Transition } from 'motion/react';
import { useState, type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { forgetMotionPreference, preferReducedMotion } from '../../test/reducedMotion.ts';
import { Collapsible } from '../Collapsible.tsx';
import { MotionProvider, useMotionReady } from './MotionProvider.tsx';
import { PanelDialog } from './PanelDialog.tsx';
import { staggered, useEntranceMotion } from './useActionMotion.ts';
import { Toast } from './Toast.tsx';

function ReadyMark() {
  return useMotionReady() ? <span data-testid="motion-ready" /> : null;
}

/** Renders inside the app's MotionProvider and waits until its animation features have loaded. */
async function renderAnimated(ui: ReactNode) {
  const view = render(
    <MotionProvider>
      <ReadyMark />
      {ui}
    </MotionProvider>,
  );
  await screen.findByTestId('motion-ready');
  return view;
}

function Panel({ from = 'right', onClosed }: { from?: 'left' | 'right'; onClosed?: () => void }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        type="button"
        onClick={() => {
          setOpen(true);
        }}
      >
        Abrir
      </button>
      <PanelDialog
        open={open}
        from={from}
        className="test-panel"
        labelledBy="panel-title"
        onDismiss={() => {
          setOpen(false);
        }}
        {...(onClosed === undefined ? {} : { onClosed })}
      >
        <h2 id="panel-title">Panel</h2>
        <button
          type="button"
          onClick={() => {
            setOpen(false);
          }}
        >
          Cerrar
        </button>
      </PanelDialog>
    </>
  );
}

/** A notice opened by a button, as after an action: by then Motion has loaded. */
function Notice() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        type="button"
        onClick={() => {
          setOpen(true);
        }}
      >
        Mostrar
      </button>
      <Toast
        open={open}
        closeLabel="Cerrar aviso"
        onClose={() => {
          setOpen(false);
        }}
      >
        Agregado al carrito
      </Toast>
    </>
  );
}

// fireEvent, not userEvent: the styles are read in the same tick, before Motion's first frame moves them.
const showNotice = () => {
  fireEvent.click(screen.getByRole('button', { name: 'Mostrar' }));
  return screen.getByText('Agregado al carrito').parentElement;
};

const openPanel = () => {
  fireEvent.click(screen.getByRole('button', { name: 'Abrir' }));
  return screen.getByRole('dialog', { name: 'Panel' });
};

describe('side panels (E7 motion)', () => {
  afterEach(() => {
    forgetMotionPreference();
  });

  it('given Motion not loaded yet, when a panel opens, then it shows in place, unanimated', () => {
    render(<Panel />);

    const dialog = openPanel();

    expect(dialog).toHaveAttribute('open');
    expect(dialog.style.transform).toMatch(/^(none)?$/);
    expect(dialog.style.opacity).not.toBe('0');
  });

  it('given motion allowed, when the panels open, then each slides from its own edge', async () => {
    preferReducedMotion(false);
    await renderAnimated(<Panel />);

    expect(openPanel().style.transform).toBe('translateX(100%)');
  });

  it('given motion allowed, when a left panel opens, then it slides from the left edge', async () => {
    preferReducedMotion(false);
    await renderAnimated(<Panel from="left" />);

    expect(openPanel().style.transform).toBe('translateX(-100%)');
  });

  it('given reduced motion, when a panel opens, then it only fades in and nothing translates', async () => {
    preferReducedMotion(true);
    await renderAnimated(<Panel />);

    const dialog = openPanel();

    expect(dialog.style.transform).toMatch(/^(none)?$/);
    expect(dialog.style.opacity).toBe('0');
  });

  it('given an open panel, when closed, then the overlay fades while it slides and the dialog closes at the end', async () => {
    preferReducedMotion(false);
    const onClosed = vi.fn();
    await renderAnimated(<Panel onClosed={onClosed} />);
    const dialog = openPanel();

    await userEvent.click(screen.getByRole('button', { name: 'Cerrar' }));

    expect(dialog).toHaveClass('closing');
    expect(onClosed).not.toHaveBeenCalled();
    await waitFor(() => {
      expect(onClosed).toHaveBeenCalledOnce();
    });
    expect(dialog).not.toHaveAttribute('open');
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });

  it('given an open panel, when Escape is pressed, then the panel slides out instead of closing at once', async () => {
    render(<Panel />);
    const dialog = openPanel();
    const cancel = new Event('cancel', { cancelable: true });

    fireEvent(dialog, cancel);

    expect(cancel.defaultPrevented).toBe(true);
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });
});

describe('notice (E7 motion)', () => {
  afterEach(() => {
    forgetMotionPreference();
  });

  it('given an open notice, when shown, then screen readers get it and its button closes it', async () => {
    const onClose = vi.fn();
    render(
      <Toast open closeLabel="Cerrar aviso" onClose={onClose}>
        Agregado al carrito
      </Toast>,
    );

    expect(screen.getByRole('status')).toHaveTextContent('Agregado al carrito');
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar aviso' }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('given motion allowed, when the notice appears, then it rises from below', async () => {
    preferReducedMotion(false);
    await renderAnimated(<Notice />);

    const notice = showNotice();

    expect(notice?.style.transform).toBe('translateY(24px)');
    expect(notice?.style.opacity).toBe('0');
  });

  it('given reduced motion, when the notice appears, then it only fades in', async () => {
    preferReducedMotion(true);
    await renderAnimated(<Notice />);

    const notice = showNotice();

    expect(notice?.style.transform).toMatch(/^(none)?$/);
    expect(notice?.style.opacity).toBe('0');
  });

  it('given a closed notice, when the page shows, then the status region waits empty', () => {
    render(
      <Toast open={false} closeLabel="Cerrar aviso" onClose={vi.fn()}>
        Agregado al carrito
      </Toast>,
    );

    expect(screen.getByRole('status')).toBeEmptyDOMElement();
  });
});

describe('collapsible section (E7 motion)', () => {
  it('given a closed section, when its title is pressed, then it opens and its content can be reached', async () => {
    render(
      <Collapsible title="Composición">
        <a href="/faq">Cambios y devoluciones</a>
      </Collapsible>,
    );
    const toggle = screen.getByRole('button', { name: 'Composición' });
    const panel = document.getElementById(toggle.getAttribute('aria-controls') ?? '');

    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(panel).toHaveAttribute('inert');
    expect(screen.queryByRole('link', { name: 'Cambios y devoluciones' })).not.toBeInTheDocument();

    await userEvent.click(toggle);

    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(panel).not.toHaveAttribute('inert');
    expect(screen.getByRole('link', { name: 'Cambios y devoluciones' })).toBeVisible();

    await userEvent.click(toggle);

    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(panel).toHaveAttribute('inert');
  });
});

/** Three items that join the page on a button press, as cards after "Ver más" or the links of the opened menu. */
function Entering({ from }: { from?: 'below' | 'left' }) {
  const [shown, setShown] = useState(false);
  const entrance = useEntranceMotion(from);
  return (
    <>
      <button
        type="button"
        onClick={() => {
          setShown(true);
        }}
      >
        Sumar
      </button>
      {shown && (
        <ul>
          {['Uno', 'Dos', 'Tres'].map((label, index) => (
            <m.li key={label} {...staggered(entrance, index)}>
              {label}
            </m.li>
          ))}
        </ul>
      )}
    </>
  );
}

const addItems = () => {
  fireEvent.click(screen.getByRole('button', { name: 'Sumar' }));
  return screen.getByText('Uno');
};

describe('entrances (T-E7-17)', () => {
  afterEach(forgetMotionPreference);

  it('given Motion not loaded yet, when items join the page, then they show in place', () => {
    render(<Entering />);

    const item = addItems();

    expect(item.style.transform).toMatch(/^(none)?$/);
    expect(item.style.opacity).not.toBe('0');
  });

  it('given motion allowed, when items join the page, then they rise 12 px and fade in', async () => {
    preferReducedMotion(false);
    await renderAnimated(<Entering />);

    const item = addItems();

    expect(item.style.transform).toBe('translateY(12px)');
    expect(item.style.opacity).toBe('0');
  });

  it('given motion allowed, when the menu links come in, then they come from the left', async () => {
    preferReducedMotion(false);
    await renderAnimated(<Entering from="left" />);

    expect(addItems().style.transform).toBe('translateX(-12px)');
  });

  it('given reduced motion, when items join the page, then they only fade in', async () => {
    preferReducedMotion(true);
    await renderAnimated(<Entering />);

    const item = addItems();

    expect(item.style.transform).toMatch(/^(none)?$/);
    expect(item.style.opacity).toBe('0');
  });

  it('given a staggered entrance, when its items are counted, then each starts 40 ms later, at most 6 steps', () => {
    const motion: { transition: Transition } = { transition: { duration: 0.24 } };

    expect([0, 1, 2, 6, 20].map((step) => staggered(motion, step).transition.delay)).toEqual([
      0, 0.04, 0.08, 0.24, 0.24,
    ]);
    expect(staggered(motion, 3).transition.duration).toBe(0.24);
  });
});
