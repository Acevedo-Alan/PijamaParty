import { useId } from 'react';
import { PanelDialog } from '../../components/motion/PanelDialog.tsx';
import { es } from '../../i18n/es.ts';
import type { DetailImage } from './productDetailApi.ts';

interface ProductLightboxProps {
  open: boolean;
  image: DetailImage;
  alt: string;
  onClose: () => void;
}

/**
 * The enlarged view of the main photo with its 1200 px file (E7): a modal dialog that slides up and closes with
 * Escape, a tap outside or its close button.
 */
export function ProductLightbox({ open, image, alt, onClose }: ProductLightboxProps) {
  const titleId = useId();
  return (
    <PanelDialog open={open} from="bottom" className="product-lightbox" labelledBy={titleId} onDismiss={onClose}>
      <h2 id={titleId} className="visually-hidden">
        {es.product.enlarged}
      </h2>
      <button type="button" className="icon-btn product-lightbox-close" aria-label={es.product.closeEnlarged} onClick={onClose}>
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M6 6l12 12M18 6 6 18" />
        </svg>
      </button>
      <img src={image.largeSrc} alt={alt} />
    </PanelDialog>
  );
}
