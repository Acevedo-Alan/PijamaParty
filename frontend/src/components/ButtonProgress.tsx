import './ButtonProgress.css';

/**
 * Where a button with a progress bar is (E7 motion): the bar fills while waiting, completes when done, and turns soft
 * red and drains when the action was refused or failed. The button carries it as `data-state` next to the class
 * `btn-progress`; its CSS reads both.
 */
export type ProgressState = 'idle' | 'loading' | 'done' | 'failed';

/** The chibi in pajamas and nightcap that runs on top of the bar (drawn for the store, not a known character). */
function Chibi() {
  return (
    <svg className="btn-progress-chibi" viewBox="0 0 32 36" aria-hidden="true">
      <g className="btn-progress-leg-a">
        <path d="M13.5 29v4.5" />
      </g>
      <g className="btn-progress-leg-b">
        <path d="M18.5 29v4.5" />
      </g>
      <path className="btn-progress-arm" d="M11 24.5l-3.5 3M21 24.5l3.5 3" />
      <rect className="btn-progress-pajama" x="10" y="21.5" width="12" height="9" rx="4" />
      <circle className="btn-progress-dot" cx="13.5" cy="25" r="0.9" />
      <circle className="btn-progress-dot" cx="18" cy="27.5" r="0.9" />
      <circle className="btn-progress-skin" cx="16" cy="14" r="9" />
      <path className="btn-progress-hair" d="M7 15c0-6.5 4-9.5 9-9.5s9 3 9 9.5c-2-2.5-4.5-3.6-6.8-4.2-2 1.8-6.6 3.2-11.2 4.2z" />
      <path className="btn-progress-cap" d="M7.4 10.5C8 4.5 13 1.6 18 2.4c4 .7 8 2.8 10.5 6.8l-2.3 1.8C22 8.2 13 7.6 7.4 10.5z" />
      <circle className="btn-progress-pompom" cx="28.6" cy="9.6" r="2.3" />
      <ellipse className="btn-progress-eye" cx="12.6" cy="15.6" rx="1.6" ry="2.1" />
      <ellipse className="btn-progress-eye" cx="19.4" cy="15.6" rx="1.6" ry="2.1" />
      <circle className="btn-progress-shine" cx="13.1" cy="14.8" r="0.6" />
      <circle className="btn-progress-shine" cx="19.9" cy="14.8" r="0.6" />
      <ellipse className="btn-progress-blush" cx="10.4" cy="18.4" rx="1.6" ry="1" />
      <ellipse className="btn-progress-blush" cx="21.6" cy="18.4" rx="1.6" ry="1" />
      <path className="btn-progress-mouth" d="M15 18.8q1 1 2 0" />
    </svg>
  );
}

/** The bar and the chibi of a `btn-progress` button; put it first inside the button, before its text. */
export function ButtonProgress() {
  return (
    <>
      <span className="btn-progress-bar" aria-hidden="true" />
      <span className="btn-progress-track" aria-hidden="true">
        <span className="btn-progress-runner">
          <Chibi />
        </span>
      </span>
    </>
  );
}
