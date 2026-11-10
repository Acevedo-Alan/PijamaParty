import { useId } from 'react';
import './chibi.css';
import './chibiBox.css';

/**
 * What the chibi is doing: on the account pages (T-E7-47) waiting, looking at a text field, hiding its eyes from a
 * password (or peeking at one shown), waiting for the server, sad after a refusal or happy when it is done; asleep in
 * the empty cart (T-E7-48); puzzled where there is nothing to show (T-E7-49).
 */
export type ChibiMood = 'idle' | 'text' | 'password' | 'peek' | 'waiting' | 'error' | 'success' | 'asleep' | 'puzzled';

/**
 * Where the chibi is, which gives it a touch of its own: on the account pages (T-E7-47) it waves on "Crear cuenta",
 * wonders with a question mark on "Recuperar contraseña" and holds a key on "Elegir contraseña nueva"; on the paid
 * order little stars fall around it, and in the empty cart it hugs a pillow (T-E7-48); a search without results gives
 * it a magnifier, the 404 page an open map, and "Mis pedidos" without orders an empty little bag (T-E7-49); while a
 * payment is awaited, three little stars orbit over its head (T-E7-50).
 */
export type ChibiPose =
  'signIn' | 'register' | 'recover' | 'reset' | 'paid' | 'cart' | 'search' | 'lost' | 'orders' | 'paying';

/** The poses with a "?" over its head. */
const WONDERING: readonly ChibiPose[] = ['recover', 'search', 'lost'];

/** What the right hand holds, drawn before the mitten so the hand grips it, and moving with the hand. */
type Held = 'key' | 'magnifier' | 'bag';

const HELD_BY_POSE: Partial<Record<ChibiPose, Held>> = { reset: 'key', search: 'magnifier', orders: 'bag' };

function HeldThing({ held }: { held: Held }) {
  switch (held) {
    case 'key':
      return (
        <g className="chibi-key">
          <path className="chibi-key-shaft" d="M150 122 134 154M140 143l-7-4M137 149l-7-4" />
          <circle className="chibi-key-bow" cx="155" cy="113" r="10" />
          <circle className="chibi-key-hole" cx="155" cy="113" r="3.2" />
        </g>
      );
    case 'magnifier':
      return (
        <g className="chibi-magnifier">
          <path className="chibi-magnifier-handle" d="M148 128 134 152" />
          <circle className="chibi-magnifier-lens" cx="156" cy="115" r="14" />
          <path className="chibi-magnifier-shine" d="M148 110q3-5 8-6" />
        </g>
      );
    case 'bag':
      return (
        <g className="chibi-bag">
          <path className="chibi-bag-handle" d="M142 158c0-14 20-14 20 0" />
          <path className="chibi-bag-body" d="M130 158h44l-4 34h-36z" />
        </g>
      );
  }
}

/**
 * One little mitten hand, its three fingers bumps along the top and its thumb toward the body; it rests at the side of
 * the small body and rises over an eye, fingers up. The left hand is drawn and the right one is its mirror around
 * x = 100. The right hand may hold something (a key, a magnifier, a bag), which moves with it.
 */
function Hand({ side, held }: { side: 'left' | 'right'; held?: Held | undefined }) {
  return (
    <g className={`chibi-hand chibi-hand-${side}`}>
      {held !== undefined && <HeldThing held={held} />}
      <g transform={side === 'left' ? 'translate(14 10)' : 'matrix(-1 0 0 1 186 10)'}>
        <ellipse className="chibi-mitten" cx="65" cy="147" rx="6" ry="5" />
        <path
          className="chibi-mitten"
          d="M38 150c-3-10 0-20 5-20 3-5 7-3 7 1 1-7 7-7 7 0 2-5 8-3 8 3 3 8 1 18-13 21-8 1-12-1-14-5z"
        />
      </g>
    </g>
  );
}

/** One chibi eye: a tall dark oval with a highlight at the top and a small one below, as the style draws them. */
function Eye({ cx }: { cx: number }) {
  const cy = 92;
  return (
    <g>
      <ellipse className="chibi-pupil" cx={cx} cy={cy} rx="7.5" ry="14" />
      <ellipse className="chibi-shine" cx={cx - 2.5} cy={cy - 7} rx="2.6" ry="3.8" />
      <circle className="chibi-shine" cx={cx + 2.2} cy={cy + 6} r="1.4" />
    </g>
  );
}

/** A four-point sparkle star centered on 0,0, 20 units across: the chibi's falling stars and the account sky's. */
export const STAR_PATH = 'M0-10C1-3 3-1 10 0 3 1 1 3 0 10-1 3-3 1-10 0-3-1-1-3 0-10z';

/** The little stars that fall around it on the paid order, as [x, y, size, delay in ms]. */
const CONFETTI = [
  [18, 20, 0.7, 0],
  [182, 30, 0.6, 160],
  [44, -4, 0.5, 320],
  [160, 2, 0.8, 80],
  [8, 70, 0.5, 400],
  [194, 84, 0.55, 240],
] as const;

interface ChibiProps {
  mood: ChibiMood;
  pose?: ChibiPose;
  className?: string;
}

/**
 * The store's chibi in pajamas and nightcap (an original drawing, not a known character), in the super-deformed chibi
 * style: a big head about two thirds of its height, a tiny body, tall oval eyes, a thick outline and flat colors, a
 * stray lock of hair out of its cap (T-E7-47). The account pages, the paid order and the empty cart share it
 * (T-E7-48). Decorative: hidden from screen readers and with no text to read. Every gesture is a CSS state of
 * `data-mood` and `data-pose`, so the page decides what it feels and the drawing only shows it.
 */
export function Chibi({ mood, pose = 'signIn', className }: ChibiProps) {
  // The gradients need ids unique in the page; useId's own characters are not all valid in url(#…).
  const prefix = `chibi${useId().replace(/[^a-zA-Z0-9]/g, '')}`;
  const hair = `${prefix}-hair`;
  const cap = `${prefix}-cap`;
  const pajama = `${prefix}-pajama`;
  return (
    <svg
      className={className === undefined ? 'chibi' : `chibi ${className}`}
      viewBox="0 0 200 200"
      data-mood={mood}
      data-pose={pose}
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient id={hair} x1="0" y1="0" x2="0" y2="1">
          <stop className="chibi-hair-light" offset="0" />
          <stop className="chibi-hair-dark" offset="1" />
        </linearGradient>
        <linearGradient id={cap} x1="0" y1="0" x2="1" y2="1">
          <stop className="chibi-cap-light" offset="0" />
          <stop className="chibi-cap-dark" offset="1" />
        </linearGradient>
        <linearGradient id={pajama} x1="0" y1="0" x2="1" y2="0">
          <stop className="chibi-pajama-light" offset="0.4" />
          <stop className="chibi-pajama-dark" offset="1" />
        </linearGradient>
      </defs>
      {/* Its shadow on the ground stays put when it breathes or hops. */}
      <ellipse className="chibi-ground" cx="100" cy="193" rx="34" ry="5" />
      <g className="chibi-breath">
        <g className="chibi-figure">
          {/* The tiny body: legs, feet, and the pajama top with its collar and buttons. */}
          <path className="chibi-pajama" fill={`url(#${pajama})`} d="M85 162h12v18H85zM103 162h12v18h-12z" />
          <ellipse className="chibi-foot" cx="90" cy="184" rx="10" ry="6" />
          <ellipse className="chibi-foot" cx="110" cy="184" rx="10" ry="6" />
          <path
            className="chibi-pajama"
            fill={`url(#${pajama})`}
            d="M79 126c-5 12-6 28-2 40 1 4 45 4 46 0 4-12 3-28-2-40z"
          />
          <path className="chibi-collar" d="M86 127q7 12 14 6 7 6 14-6z" />
          <circle className="chibi-button" cx="100" cy="146" r="2.4" />
          <circle className="chibi-button" cx="100" cy="157" r="2.4" />
          <path className="chibi-dot" d="M88 150l1.2 2.4 2.6.4-1.9 1.8.5 2.6-2.4-1.3-2.4 1.3.5-2.6-1.9-1.8 2.6-.4z" />
          <g className="chibi-head">
            {/* The long hair behind the face, falling past the shoulders. */}
            <path
              className="chibi-hair-back"
              d="M38 96C34 60 60 34 100 34s66 26 62 62c2 34-2 62-12 76l-14-22-6-30H70l-6 30-14 22c-10-14-14-42-12-76z"
            />
            <path className="chibi-skin" d="M44 84c0-34 24-48 56-48s56 14 56 48c0 30-24 48-56 49-32-1-56-19-56-49z" />
            <path className="chibi-skin-shade" d="M50 76c16-6 36-8 50-6 14-2 34 0 50 6-4 6-96 6-100 0z" />
            {/* The bangs, in pointed locks, with the light band of anime hair. */}
            <path
              className="chibi-hair"
              fill={`url(#${hair})`}
              d="M44 70c12-16 36-20 56-20s44 4 56 20l2 26c-6-10-10-16-16-20l-4 8c-6-10-12-14-20-16l-6 12c-4-8-8-12-14-14l-6 14c-4-8-10-12-18-12l-4 14c-6-6-12-8-20-6l-8 20z"
            />
            <path className="chibi-hair-shine" d="M64 60q8-4 16-4M90 55q10-1 20 0M122 56q8 1 14 4" />
            {/* The stray lock that pokes out of the cap. */}
            <path className="chibi-hair" fill={`url(#${hair})`} d="M98 16c-8-14 6-24 20-18-9 0-14 7-12 18z" />
            <path
              className="chibi-cap"
              fill={`url(#${cap})`}
              d="M36 74C38 30 74 10 108 12c32 2 58 20 74 48l-16 6C144 42 56 46 36 74z"
            />
            <path className="chibi-cap-fold" d="M122 18q14 14 18 30M154 30q8 10 10 22" />
            {/* The cap's lower edge runs under the band, so no face shows between them. */}
            <path className="chibi-cap-band" d="M36 72c20-28 108-32 128-10l-2 12c-18-18-104-14-124 12z" />
            <circle className="chibi-pompom" cx="182" cy="66" r="12" />
            <path className="chibi-pompom-fluff" d="M176 60q3-2 6 0M180 72q3 2 6 0M186 64q2 2 1 5" />
            <g className="chibi-brows">
              <path d="M68 76q8-1 16-5M116 71q8 4 16 5" />
            </g>
            <g className="chibi-eyes">
              <g className="chibi-eyes-open">
                <Eye cx={78} />
                <Eye cx={122} />
              </g>
              <path className="chibi-eyes-happy" d="M69 96q9-13 18 0M113 96q9-13 18 0" />
              <path className="chibi-eyes-closed" d="M70 93q8 7 16 0M114 93q8 7 16 0" />
            </g>
            <ellipse className="chibi-blush" cx="62" cy="111" rx="9" ry="5" />
            <ellipse className="chibi-blush" cx="138" cy="111" rx="9" ry="5" />
            <path
              className="chibi-blush-lines"
              d="M59 108l-2 5M63 108l-2 5M67 108l-2 5M135 108l-2 5M139 108l-2 5M143 108l-2 5"
            />
            <path className="chibi-mouth chibi-mouth-smile" d="M93 115q3.5 4 7 0q3.5 4 7 0" />
            <ellipse className="chibi-mouth chibi-mouth-o" cx="100" cy="118" rx="3.5" ry="4.5" />
            <path className="chibi-mouth chibi-mouth-happy" d="M90 113q10 14 20 0zM94 114l2 4 2-4" />
            <path className="chibi-mouth chibi-mouth-sad" d="M93 120q7-6 14 0" />
          </g>
          {/* The empty cart's pillow, hugged against its body, its hands on either side. */}
          {pose === 'cart' && (
            <g className="chibi-pillow-hug">
              <rect className="chibi-pillow" x="66" y="130" width="68" height="40" rx="16" />
              <path className="chibi-pillow-seam" d="M76 150h48" />
            </g>
          )}
          {/* The 404's map, open in front of it, held by both hands: three folds and a dotted way to a cross. */}
          {pose === 'lost' && (
            <g className="chibi-map">
              <path className="chibi-map-paper" d="M54 128l30-6 32 6 30-6v44l-30 6-32-6-30 6z" />
              <path className="chibi-map-fold" d="M84 122v44M116 128v44" />
              <path className="chibi-map-way" d="M68 158q12-16 26-6t26-12" />
              <path className="chibi-map-cross" d="M122 134l8 8M130 134l-8 8" />
            </g>
          )}
          <Hand side="left" />
          <Hand side="right" held={HELD_BY_POSE[pose]} />
        </g>
      </g>
      {WONDERING.includes(pose) && (
        <text className="chibi-wonder" x="158" y="36">
          ?
        </text>
      )}
      {/* While a payment is awaited: three little stars orbiting over its head, 40 units around (100, 20). */}
      {pose === 'paying' && (
        <g className="chibi-orbit">
          {[
            [140, 20],
            [80, 54.6],
            [80, -14.6],
          ].map(([x = 0, y = 0]) => (
            <g key={`${String(x)},${String(y)}`} transform={`translate(${String(x)} ${String(y)}) scale(1.1)`}>
              <path d={STAR_PATH} />
            </g>
          ))}
        </g>
      )}
      {pose === 'paid' && (
        <g className="chibi-confetti">
          {CONFETTI.map(([x, y, size, delay]) => (
            <g key={x} transform={`translate(${String(x)} ${String(y)}) scale(${String(size)})`}>
              <path style={{ animationDelay: `${String(delay)}ms` }} d={STAR_PATH} />
            </g>
          ))}
        </g>
      )}
      <g className="chibi-sleep">
        <text x="150" y="40">
          z
        </text>
        <text x="164" y="22">
          z
        </text>
        <text x="178" y="6">
          Z
        </text>
      </g>
      <g className="chibi-hearts">
        {['translate(30 44) scale(1.2)', 'translate(172 50)', 'translate(104 12) scale(0.8)'].map((place, index) => (
          <g key={place} transform={place}>
            <path
              className="chibi-heart"
              style={{ animationDelay: `${String(index * 80)}ms` }}
              d="M0 4C-6-4-14 2 0 12 14 2 6-4 0 4z"
            />
          </g>
        ))}
      </g>
    </svg>
  );
}
