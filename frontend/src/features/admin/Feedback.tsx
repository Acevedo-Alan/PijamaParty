/** The outcome of the last change on a back-office page. */
export interface Notice {
  kind: 'ok' | 'error';
  text: string;
}

/** Errors are announced at once (alert); confirmations politely (status). */
export function Feedback({ notice }: { notice: Notice | null }) {
  if (notice === null) {
    return null;
  }
  return notice.kind === 'error' ? (
    <p className="form-error" role="alert">
      {notice.text}
    </p>
  ) : (
    <p className="form-ok" role="status">
      {notice.text}
    </p>
  );
}
