/**
 * Scrolls a strip just enough to show one of its items, never the page: the product gallery's thumbnails and the
 * account area's row of pills (T-E7-60).
 */
export function keepInSight(list: HTMLElement, item: HTMLElement) {
  if (item.offsetLeft < list.scrollLeft) {
    list.scrollLeft = item.offsetLeft;
  } else if (item.offsetLeft + item.offsetWidth > list.scrollLeft + list.clientWidth) {
    list.scrollLeft = item.offsetLeft + item.offsetWidth - list.clientWidth;
  }
  if (item.offsetTop < list.scrollTop) {
    list.scrollTop = item.offsetTop;
  } else if (item.offsetTop + item.offsetHeight > list.scrollTop + list.clientHeight) {
    list.scrollTop = item.offsetTop + item.offsetHeight - list.clientHeight;
  }
}
