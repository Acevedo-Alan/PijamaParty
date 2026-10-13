import { Navigate, type RouteObject } from 'react-router';
import { CatalogPage } from './features/catalog/CatalogPage.tsx';
import { ProductDetailPage } from './features/catalog/ProductDetailPage.tsx';
import { NotFoundPage } from './features/errors/NotFoundPage.tsx';
import { OrderTrackingPage } from './features/orders/OrderTrackingPage.tsx';
import { SearchPage } from './features/search/SearchPage.tsx';
import { StoreLayout } from './features/search/StoreLayout.tsx';

/** The back office loads on demand, so shoppers never download it. */
const admin = () => import('./features/admin/adminPages.ts');

/** Feature folders add their routes here (catalog, cart, checkout, account, admin: ARCHITECTURE.md section 11). */
export const routes: RouteObject[] = [
  {
    element: <StoreLayout />,
    children: [
      { path: '/', element: <CatalogPage /> },
      { path: '/categoria/:categorySlug', element: <CatalogPage /> },
      { path: '/producto/:productSlug', element: <ProductDetailPage /> },
      { path: '/buscar', element: <SearchPage /> },
      { path: '/pedido/:token', element: <OrderTrackingPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
  { path: '/admin/ingresar', lazy: async () => ({ Component: (await admin()).SignInPage }) },
  {
    path: '/admin',
    lazy: async () => ({ Component: (await admin()).AdminLayout }),
    children: [
      { index: true, element: <Navigate to="/admin/productos" replace /> },
      { path: 'productos', lazy: async () => ({ Component: (await admin()).ProductsPage }) },
      { path: 'productos/nuevo', lazy: async () => ({ Component: (await admin()).NewProductPage }) },
      { path: 'productos/:productId', lazy: async () => ({ Component: (await admin()).ProductEditPage }) },
      {
        path: 'productos/:productId/variantes/:variantId/stock',
        lazy: async () => ({ Component: (await admin()).StockPage }),
      },
      { path: 'categorias', lazy: async () => ({ Component: (await admin()).CategoriesPage }) },
      { path: 'cupones', lazy: async () => ({ Component: (await admin()).CouponsPage }) },
      { path: 'envios', lazy: async () => ({ Component: (await admin()).ShippingPage }) },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
