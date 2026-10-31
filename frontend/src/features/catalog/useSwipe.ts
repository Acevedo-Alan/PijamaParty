import { useRef, useState, type DragEvent, type MouseEvent, type PointerEvent } from 'react';

/** Pixels a pointer moves before it counts as a drag, so a press with a shaky finger still opens the photo. */
const SLOP = 8;
/** Share of the width a drag has to travel to move to the next photo... */
const COMMIT = 0.2;
/** ...unless it is a flick faster than this, in pixels per millisecond. */
const FLICK = 0.45;
/** Past the first and the last photo the drag moves only this share of the finger: it resists instead of looping. */
const EDGE_RESISTANCE = 0.3;

interface Drag {
  pointerId: number;
  x: number;
  y: number;
  time: number;
  width: number;
  dragging: boolean;
}

interface Swipe {
  /** Pixels the photos follow the finger while it drags; null when nothing is dragged, so they slide again. */
  offset: number | null;
  handlers: {
    onPointerDown: (event: PointerEvent<HTMLElement>) => void;
    onPointerMove: (event: PointerEvent<HTMLElement>) => void;
    onPointerUp: (event: PointerEvent<HTMLElement>) => void;
    onPointerCancel: () => void;
    onClickCapture: (event: MouseEvent<HTMLElement>) => void;
    onDragStart: (event: DragEvent<HTMLElement>) => void;
  };
}

/**
 * A horizontal swipe or mouse drag between photos (E7 gallery motion). The element keeps `touch-action: pan-y`, so a
 * vertical move scrolls the page and is never captured. The click that ends a drag is swallowed, so dragging the main
 * photo does not also open the enlarged view. Without `followFinger` the offset stays null, so a caller that cannot
 * show the drag (the home carousel fades) does not re-render on every move.
 */
export function useSwipe(
  index: number,
  count: number,
  onChange: (index: number) => void,
  enabled = true,
  followFinger = true,
): Swipe {
  const [offset, setOffset] = useState<number | null>(null);
  const drag = useRef<Drag | null>(null);
  const dragged = useRef(false);

  function cancel() {
    drag.current = null;
    setOffset(null);
  }

  return {
    offset,
    handlers: {
      onPointerDown(event) {
        if (!enabled || count < 2 || (event.pointerType === 'mouse' && event.button !== 0)) {
          return;
        }
        dragged.current = false;
        drag.current = {
          pointerId: event.pointerId,
          x: event.clientX,
          y: event.clientY,
          time: event.timeStamp,
          width: event.currentTarget.getBoundingClientRect().width,
          dragging: false,
        };
      },
      onPointerMove(event) {
        const current = drag.current;
        if (current?.pointerId !== event.pointerId) {
          return;
        }
        const dx = event.clientX - current.x;
        const dy = event.clientY - current.y;
        if (!current.dragging) {
          if (Math.abs(dx) < SLOP && Math.abs(dy) < SLOP) {
            return;
          }
          if (Math.abs(dy) >= Math.abs(dx)) {
            // A vertical move belongs to the page.
            drag.current = null;
            return;
          }
          current.dragging = true;
          event.currentTarget.setPointerCapture(event.pointerId);
        }
        if (followFinger) {
          const pastEdge = (dx > 0 && index === 0) || (dx < 0 && index === count - 1);
          setOffset(pastEdge ? dx * EDGE_RESISTANCE : dx);
        }
      },
      onPointerUp(event) {
        const current = drag.current;
        drag.current = null;
        if (current?.pointerId !== event.pointerId || !current.dragging) {
          return;
        }
        dragged.current = true;
        setOffset(null);
        const dx = event.clientX - current.x;
        const speed = Math.abs(dx) / Math.max(1, event.timeStamp - current.time);
        const next = index + (dx < 0 ? 1 : -1);
        if ((Math.abs(dx) > current.width * COMMIT || speed > FLICK) && next >= 0 && next < count) {
          onChange(next);
        }
      },
      onPointerCancel: cancel,
      onClickCapture(event) {
        if (dragged.current) {
          dragged.current = false;
          event.preventDefault();
          event.stopPropagation();
        }
      },
      onDragStart(event) {
        // The browser's own drag of the image would steal the pointer from the swipe.
        event.preventDefault();
      },
    },
  };
}
