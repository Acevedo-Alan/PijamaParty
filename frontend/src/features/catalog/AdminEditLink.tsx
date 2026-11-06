import { useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { clearAdmin, maybeAdmin } from '../admin/adminHint.ts';
import { sessionQuery } from '../admin/useSession.ts';

/**
 * T-E8-27 (UC-03): "Editar producto", shown only to an administrator signed in to the back office, which opens the
 * product's form there. The session is asked only when this browser holds the hint of an earlier back-office session;
 * anyone else makes no request. A refused check removes the hint.
 */
export function AdminEditLink({ productId }: { productId: string }) {
  const session = useQuery({ ...sessionQuery, enabled: maybeAdmin() });
  const status = session.data?.status;

  useEffect(() => {
    if (status !== undefined && status !== 'signedIn') {
      clearAdmin();
    }
  }, [status]);

  if (status !== 'signedIn') {
    return null;
  }
  return (
    <p className="pdp-admin-edit">
      <Link to={`/admin/productos/${productId}`} className="btn">
        {es.product.adminEdit}
      </Link>
    </p>
  );
}
