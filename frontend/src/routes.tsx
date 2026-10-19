import { Navigate, type RouteObject } from 'react-router';
import {
  ACCOUNT_PATH,
  ADDRESSES_PATH,
  COMPLETE_EMAIL_PATH,
  LINK_PATH,
  ORDERS_PATH,
  RECOVER_PATH,
  REGISTER_PATH,
  RESET_PATH,
  SIGN_IN_PATH,
  VERIFY_PATH,
} from './features/account/AccountLink.tsx';
import { CatalogPage } from './features/catalog/CatalogPage.tsx';
import { ProductDetailPage } from './features/catalog/ProductDetailPage.tsx';
import { NotFoundPage } from './features/errors/NotFoundPage.tsx';
import { FAQ_PATH } from './features/help/FaqLink.tsx';
import { SearchPage } from './features/search/SearchPage.tsx';
import { StoreLayout } from './features/search/StoreLayout.tsx';
import { WITHDRAWAL_PATH } from './features/withdrawal/WithdrawalLink.tsx';

/**
 * T-E6-02 (RNF-11): the main bundle holds only the pages shoppers arrive on from links (catalog, product, search).
 * The rest loads on demand, and the back office as well, so shoppers never download it.
 */
const account = () => import('./features/account/accountPages.ts');
const checkout = () => import('./features/checkout/checkoutPages.ts');
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
      { path: '/checkout', lazy: async () => ({ Component: (await checkout()).CheckoutPage }) },
      { path: '/checkout/result', lazy: async () => ({ Component: (await checkout()).CheckoutResultPage }) },
      {
        path: '/pedido/:token',
        lazy: async () => ({ Component: (await import('./features/orders/OrderTrackingPage.tsx')).OrderTrackingPage }),
      },
      {
        path: WITHDRAWAL_PATH,
        lazy: async () => ({ Component: (await import('./features/withdrawal/WithdrawalPage.tsx')).WithdrawalPage }),
      },
      { path: FAQ_PATH, lazy: async () => ({ Component: (await import('./features/help/FaqPage.tsx')).FaqPage }) },
      { path: REGISTER_PATH, lazy: async () => ({ Component: (await account()).RegisterPage }) },
      { path: SIGN_IN_PATH, lazy: async () => ({ Component: (await account()).SignInPage }) },
      { path: VERIFY_PATH, lazy: async () => ({ Component: (await account()).VerifyEmailPage }) },
      { path: RECOVER_PATH, lazy: async () => ({ Component: (await account()).RecoverPasswordPage }) },
      { path: RESET_PATH, lazy: async () => ({ Component: (await account()).ResetPasswordPage }) },
      { path: COMPLETE_EMAIL_PATH, lazy: async () => ({ Component: (await account()).CompleteEmailPage }) },
      { path: LINK_PATH, lazy: async () => ({ Component: (await account()).ConfirmLinkPage }) },
      { path: ACCOUNT_PATH, lazy: async () => ({ Component: (await account()).MyAccountPage }) },
      { path: ADDRESSES_PATH, lazy: async () => ({ Component: (await account()).AddressBookPage }) },
      { path: ORDERS_PATH, lazy: async () => ({ Component: (await account()).MyOrdersPage }) },
      { path: `${ORDERS_PATH}/:orderId`, lazy: async () => ({ Component: (await account()).MyOrderPage }) },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
  { path: '/admin/ingresar', lazy: async () => ({ Component: (await admin()).SignInPage }) },
  {
    path: '/admin',
    lazy: async () => ({ Component: (await admin()).AdminLayout }),
    children: [
      { index: true, element: <Navigate to="/admin/productos" replace /> },
      { path: 'pedidos', lazy: async () => ({ Component: (await admin()).OrdersPage }) },
      { path: 'pedidos/:orderId', lazy: async () => ({ Component: (await admin()).OrderDetailPage }) },
      { path: 'arrepentimientos', lazy: async () => ({ Component: (await admin()).WithdrawalsPage }) },
      { path: 'productos', lazy: async () => ({ Component: (await admin()).ProductsPage }) },
      { path: 'productos/nuevo', lazy: async () => ({ Component: (await admin()).NewProductPage }) },
      { path: 'productos/:productId', lazy: async () => ({ Component: (await admin()).ProductEditPage }) },
      {
        path: 'productos/:productId/variantes/:variantId/stock',
        lazy: async () => ({ Component: (await admin()).StockPage }),
      },
      { path: 'categorias', lazy: async () => ({ Component: (await admin()).CategoriesPage }) },
      {
        path: 'categorias/:categoryId/guia-de-talles',
        lazy: async () => ({ Component: (await admin()).SizeGuidePage }),
      },
      { path: 'cupones', lazy: async () => ({ Component: (await admin()).CouponsPage }) },
      { path: 'resenas', lazy: async () => ({ Component: (await admin()).ReviewsPage }) },
      { path: 'envios', lazy: async () => ({ Component: (await admin()).ShippingPage }) },
      { path: 'portada', lazy: async () => ({ Component: (await admin()).HomeBannerPage }) },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
