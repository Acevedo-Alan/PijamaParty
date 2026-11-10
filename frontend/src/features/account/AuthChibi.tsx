import { useId } from 'react';
import logoFrame from '../../assets/account/logo-frame.webp';
import './authChibi.css';

/**
 * What the account pages' chibi is doing (T-E7-47): waiting, looking at a text field, hiding its eyes from a password
 * (or peeking at one shown), waiting for the server, sad after a refusal, or happy when it is done.
 */
export type ChibiMood = 'idle' | 'text' | 'password' | 'peek' | 'waiting' | 'error' | 'success';

/**
 * Which page the chibi is on, which gives it a touch of its own (T-E7-47): it waves on "Crear cuenta", wonders with a
 * question mark on "Recuperar contraseña" and holds a little key on "Elegir contraseña nueva".
 */
export type ChibiPose = 'signIn' | 'register' | 'recover' | 'reset';

/**
 * The stars of the sky as [x, y, size, seconds of one blink, delay in seconds], spread over the whole sky so the low
 * band of a phone keeps some too. Each one turns on and off at its own pace, so the sky never blinks all at once.
 */
const STARS = [
  [64, 70, 1, 3.2, 0],
  [132, 132, 0.7, 4.1, 1.3],
  [252, 44, 0.8, 2.7, 0.6],
  [360, 172, 0.6, 3.6, 2.2],
  [40, 210, 0.7, 4.6, 0.9],
  [196, 92, 0.5, 2.9, 3.1],
  [300, 236, 0.6, 3.9, 1.8],
  [88, 252, 0.5, 3.3, 2.7],
  [236, 160, 0.4, 2.5, 0.3],
  [20, 120, 0.45, 3.7, 1.1],
  [168, 28, 0.55, 4.3, 2.4],
  [330, 96, 0.5, 3.1, 3.6],
  [112, 196, 0.35, 2.6, 1.6],
  [380, 260, 0.45, 4.4, 0.5],
  [276, 300, 0.4, 3.4, 2.9],
] as const;

/** The little stars that rain over the sky when it is done, as [x, delay in ms]. */
const RAIN = [
  [40, 0],
  [96, 180],
  [150, 60],
  [204, 260],
  [258, 120],
  [312, 320],
  [366, 40],
  [70, 420],
  [230, 500],
  [340, 600],
] as const;

const STAR_PATH = 'M0-10C1-3 3-1 10 0 3 1 1 3 0 10-1 3-3 1-10 0-3-1-1-3 0-10z';

/**
 * The night sky behind the chibi, clipped to its own rounded box: stars that turn on and off, a shooting star now and
 * then, the moon in its top right corner, clouds along the bottom, and the rain of little stars that falls when the
 * chibi is happy.
 */
function Scene() {
  return (
    <div className="auth-sky-scene">
      <svg className="auth-sky-stars" viewBox="0 0 400 400" preserveAspectRatio="xMidYMid slice">
        {STARS.map(([x, y, size, seconds, delay], index) => (
          // The place is the group's attribute; the twinkle animates the star inside it, so the two never collide.
          <g key={index} transform={`translate(${String(x)} ${String(y)}) scale(${String(size)})`}>
            <path
              className="auth-sky-star"
              style={{ animationDuration: `${String(seconds)}s`, animationDelay: `${String(delay)}s` }}
              d={STAR_PATH}
            />
          </g>
        ))}
      </svg>
      <span className="auth-shooting-star" />
      <svg className="auth-sky-moon" viewBox="0 0 80 80">
        <path d="M44 6a34 34 0 1 0 30 50A28 28 0 1 1 44 6z" />
      </svg>
      <svg className="auth-sky-clouds" viewBox="0 0 400 60" preserveAspectRatio="none">
        <ellipse cx="60" cy="60" rx="110" ry="46" />
        <ellipse cx="200" cy="70" rx="120" ry="44" />
        <ellipse cx="340" cy="58" rx="110" ry="48" />
      </svg>
      <svg className="auth-star-rain" viewBox="0 0 400 400" preserveAspectRatio="xMidYMin slice">
        {RAIN.map(([x, delay]) => (
          <g key={x + delay} transform={`translate(${String(x)} -14) scale(0.7)`}>
            <path className="auth-rain-star" style={{ animationDelay: `${String(delay)}ms` }} d={STAR_PATH} />
          </g>
        ))}
      </svg>
    </div>
  );
}

/** The scene never changes, so it is built once and not again on every change of mood. */
const SCENE = <Scene />;

/**
 * One little mitten hand, its three fingers bumps along the top and its thumb toward the body; it rests at the side of
 * the small body and rises over an eye, fingers up (T-E7-47). The left hand is drawn and the right one is its mirror
 * around x = 100. On "Elegir contraseña nueva" the right hand holds a key, which moves with it.
 */
function Hand({ side, withKey = false }: { side: 'left' | 'right'; withKey?: boolean }) {
  return (
    <g className={`chibi-hand chibi-hand-${side}`}>
      {withKey && (
        <g className="chibi-key">
          <path className="chibi-key-shaft" d="M150 122 134 154M140 143l-7-4M137 149l-7-4" />
          <circle className="chibi-key-bow" cx="155" cy="113" r="10" />
          <circle className="chibi-key-hole" cx="155" cy="113" r="3.2" />
        </g>
      )}
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

interface AuthChibiProps {
  mood: ChibiMood;
  pose?: ChibiPose;
}

/**
 * The chibi in pajamas and nightcap of "Confirmar y pagar" (an original drawing, not a known character), drawn in the
 * super-deformed chibi style (a big head about two thirds of its height, a tiny body, tall oval eyes, a thick outline
 * and flat colors, a stray lock of hair out of its cap), in a pastel night sky with the store's sign, beside the card
 * of the account pages (T-E7-47). Decorative: hidden from screen readers and with no text. Every gesture is a CSS
 * state of `data-mood` and `data-pose`, so the page decides what it feels and the drawing only shows it.
 */
export function AuthChibi({ mood, pose = 'signIn' }: AuthChibiProps) {
  // The gradients need ids unique in the page; useId's own characters are not all valid in url(#…).
  const prefix = `chibi${useId().replace(/[^a-zA-Z0-9]/g, '')}`;
  const hair = `${prefix}-hair`;
  const cap = `${prefix}-cap`;
  const pajama = `${prefix}-pajama`;
  return (
    <div className="auth-sky" data-mood={mood} data-pose={pose} aria-hidden="true">
      {SCENE}
      {/* The chibi and, in front of it, the sign: from 860 px one stage, the chibi peeking over the sign's frame. */}
      <div className="auth-stage">
        <svg className="chibi" viewBox="0 0 200 200">
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
              <path
                className="chibi-dot"
                d="M88 150l1.2 2.4 2.6.4-1.9 1.8.5 2.6-2.4-1.3-2.4 1.3.5-2.6-1.9-1.8 2.6-.4z"
              />
              <g className="chibi-head">
                {/* The long hair behind the face, falling past the shoulders. */}
                <path
                  className="chibi-hair-back"
                  d="M38 96C34 60 60 34 100 34s66 26 62 62c2 34-2 62-12 76l-14-22-6-30H70l-6 30-14 22c-10-14-14-42-12-76z"
                />
                <path
                  className="chibi-skin"
                  d="M44 84c0-34 24-48 56-48s56 14 56 48c0 30-24 48-56 49-32-1-56-19-56-49z"
                />
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
              <Hand side="left" />
              <Hand side="right" withKey={pose === 'reset'} />
            </g>
          </g>
          {pose === 'recover' && (
            <text className="chibi-wonder" x="158" y="36">
              ?
            </text>
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
            {['translate(30 44) scale(1.2)', 'translate(172 50)', 'translate(104 12) scale(0.8)'].map(
              (place, index) => (
                <g key={place} transform={place}>
                  <path
                    className="chibi-heart"
                    style={{ animationDelay: `${String(index * 80)}ms` }}
                    d="M0 4C-6-4-14 2 0 12 14 2 6-4 0 4z"
                  />
                </g>
              ),
            )}
          </g>
        </svg>
        <img className="auth-logo" src={logoFrame} width={407} height={345} alt="" decoding="async" draggable={false} />
      </div>
    </div>
  );
}
