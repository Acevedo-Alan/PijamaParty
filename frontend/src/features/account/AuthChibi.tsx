import logoFrame from '../../assets/account/logo-frame.webp';
import { Chibi, STAR_PATH, type ChibiMood, type ChibiPose } from '../../components/chibi/Chibi.tsx';
import './authChibi.css';

export type { ChibiMood, ChibiPose };

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

interface AuthChibiProps {
  mood: ChibiMood;
  pose?: ChibiPose;
}

/**
 * The store's chibi (the shared drawing of `components/chibi`) in a pastel night sky with the store's sign, beside the
 * card of the account pages (T-E7-47). Decorative: hidden from screen readers and with no text. The sky reacts to the
 * same `data-mood` as the drawing: the sign bounces and sways with it and little stars rain when it is happy.
 */
export function AuthChibi({ mood, pose = 'signIn' }: AuthChibiProps) {
  return (
    <div className="auth-sky" data-mood={mood} data-pose={pose} aria-hidden="true">
      {SCENE}
      {/* The chibi and, in front of it, the sign: from 860 px one stage, the chibi peeking over the sign's frame. */}
      <div className="auth-stage">
        <Chibi mood={mood} pose={pose} />
        <img className="auth-logo" src={logoFrame} width={407} height={345} alt="" decoding="async" draggable={false} />
      </div>
    </div>
  );
}
