/** Not for search engines (RNF-31.1): the back office, the account, checkout, tracking, search and the 404 page. */
export function NoIndex() {
  return <meta name="robots" content="noindex" />;
}
