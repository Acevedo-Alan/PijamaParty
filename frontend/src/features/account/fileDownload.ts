/** Hands the browser a file to save (UC-28). Its own module so tests can watch it. */
export function saveFile(file: Blob, name: string): void {
  const url = URL.createObjectURL(file);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = name;
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
