import logo144 from '../../assets/brand/logo-144.webp';
import logo48 from '../../assets/brand/logo-48.webp';
import logo96 from '../../assets/brand/logo-96.webp';
import { es } from '../../i18n/es.ts';

/**
 * The business's logo (D-15), 48 px tall as in the prototype, fixed in the build (D-03): the header and the top of
 * the phone menu (UC-31). Its alternative text is the store name.
 */
export function StoreLogo() {
  return (
    <img src={logo48} srcSet={`${logo48} 1x, ${logo96} 2x, ${logo144} 3x`} width={58} height={48} alt={es.store.name} />
  );
}
