import { Navigate, type RouteObject } from 'react-router';
import {
  ACCOUNT_PATH,
  ADDRESSES_PATH,
  ORDERS_PATH,
  RECOVER_PATH,
  REGISTER_PATH,
  SIGN_IN_PATH,
} from './features/account/AccountLink.tsx';
import { AddressBookPage } from './features/account/AddressBookPage.tsx';
import { COMPLETE_EMAIL_PATH, CompleteEmailPage } from './features/account/CompleteEmailPage.tsx';
import { ConfirmLinkPage, LINK_PATH } from './features/account/ConfirmLinkPage.tsx';
import { MyAccountPage } from './features/account/MyAccountPage.tsx';
import { MyOrderPage } from './features/account/MyOrderPage.tsx';
import { MyOrdersPage } from './features/account/MyOrdersPage.tsx';
import { RecoverPasswordPage } from './features/account/RecoverPasswordPage.tsx';
import { RegisterPage } from './features/account/RegisterPage.tsx';
import { RESET_PATH, ResetPasswordPage } from './features/account/ResetPasswordPage.tsx';
import { SignInPage as CustomerSignInPage } from './features/account/SignInPage.tsx';
import { VERIFY_PATH, VerifyEmailPage } from './features/account/VerifyEmailPage.tsx';
import { CatalogPage } from './features/catalog/CatalogPage.tsx';
import { ProductDetailPage } from './features/catalog/ProductDetailPage.tsx';
import { CheckoutPage } from './features/checkout/CheckoutPage.tsx';
import { CheckoutResultPage } from './features/checkout/CheckoutResultPage.tsx';
import { NotFoundPage } from './features/errors/NotFoundPage.tsx';
import { OrderTrackingPage } from './features/orders/OrderTrackingPage.tsx';
import { SearchPage } from './features/search/SearchPage.tsx';
import { StoreLayout } from './features/search/StoreLayout.tsx';
import { WITHDRAWAL_PATH } from './features/withdrawal/WithdrawalLink.tsx';
import { WithdrawalPage } from './features/withdrawal/WithdrawalPage.tsx';

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
      { path: '/checkout', element: <CheckoutPage /> },
      { path: '/checkout/result', element: <CheckoutResultPage /> },
      { path: '/pedido/:token', element: <OrderTrackingPage /> },
      { path: WITHDRAWAL_PATH, element: <WithdrawalPage /> },
      { path: REGISTER_PATH, element: <RegisterPage /> },
      { path: SIGN_IN_PATH, element: <CustomerSignInPage /> },
      { path: VERIFY_PATH, element: <VerifyEmailPage /> },
      { path: RECOVER_PATH, element: <RecoverPasswordPage /> },
      { path: RESET_PATH, element: <ResetPasswordPage /> },
      { path: COMPLETE_EMAIL_PATH, element: <CompleteEmailPage /> },
      { path: LINK_PATH, element: <ConfirmLinkPage /> },
      { path: ACCOUNT_PATH, element: <MyAccountPage /> },
      { path: ADDRESSES_PATH, element: <AddressBookPage /> },
      { path: ORDERS_PATH, element: <MyOrdersPage /> },
      { path: `${ORDERS_PATH}/:orderId`, element: <MyOrderPage /> },
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
      { path: 'cupones', lazy: async () => ({ Component: (await admin()).CouponsPage }) },
      { path: 'resenas', lazy: async () => ({ Component: (await admin()).ReviewsPage }) },
      { path: 'envios', lazy: async () => ({ Component: (await admin()).ShippingPage }) },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
