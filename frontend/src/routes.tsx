import { type RouteObject } from 'react-router';
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
import { HomePage } from './features/catalog/HomePage.tsx';
import { ProductDetailPage } from './features/catalog/ProductDetailPage.tsx';
import { NotFoundPage } from './features/errors/NotFoundPage.tsx';
import { RootLayout, RouteLoadingFallback } from './features/errors/RootLayout.tsx';
import { RouteErrorPage } from './features/errors/RouteErrorPage.tsx';
import { FAQ_PATH, HOW_TO_BUY_PATH, RETURNS_POLICY_PATH, SIZE_GUIDES_PATH, TERMS_PATH } from './features/help/FaqLink.tsx';
import { SearchPage } from './features/search/SearchPage.tsx';
import { StoreLayout } from './features/search/StoreLayout.tsx';
import { WITHDRAWAL_PATH } from './features/withdrawal/WithdrawalLink.tsx';

/**
 * T-E6-02 (RNF-11): the main bundle holds only the pages shoppers arrive on from links (catalog, product, search).
 * The rest loads on demand, and the back office as well, so shoppers never download it.
 */
const account = () => import('./features/account/accountPages.ts');
const checkout = () => import('./features/checkout/checkoutPages.ts');
const helpPages = () => import('./features/help/HelpPages.tsx');

/** Feature folders add their routes here (catalog, cart, checkout, account, admin: ARCHITECTURE.md section 11). */
export const routes: RouteObject[] = [
  {
    element: <StoreLayout />,
    children: [
      { path: '/', element: <HomePage /> },
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
      { path: HOW_TO_BUY_PATH, lazy: async () => ({ Component: (await helpPages()).HowToBuyPage }) },
      { path: RETURNS_POLICY_PATH, lazy: async () => ({ Component: (await helpPages()).ReturnsPolicyPage }) },
      { path: TERMS_PATH, lazy: async () => ({ Component: (await helpPages()).TermsPage }) },
      {
        path: SIZE_GUIDES_PATH,
        lazy: async () => ({ Component: (await import('./features/catalog/SizeGuidesPage.tsx')).SizeGuidesPage }),
      },
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
  {
    path: '/admin/ingresar',
    lazy: async () => ({ Component: (await import('./features/admin/SignInPage.tsx')).SignInPage }),
  },
  {
    path: '/admin',
    lazy: async () => ({ Component: (await import('./features/admin/AdminLayout.tsx')).AdminLayout }),
    children: [
      {
        index: true,
        lazy: async () => ({ Component: (await import('./features/admin/DashboardPage.tsx')).DashboardPage }),
      },
      {
        path: 'pedidos',
        lazy: async () => ({ Component: (await import('./features/admin/OrdersPage.tsx')).OrdersPage }),
      },
      {
        path: 'pedidos/:orderId',
        lazy: async () => ({ Component: (await import('./features/admin/OrderDetailPage.tsx')).OrderDetailPage }),
      },
      {
        path: 'pedidos/:orderId/remito',
        lazy: async () => ({ Component: (await import('./features/admin/PackingSlipPage.tsx')).PackingSlipPage }),
      },
      {
        path: 'arrepentimientos',
        lazy: async () => ({ Component: (await import('./features/admin/WithdrawalsPage.tsx')).WithdrawalsPage }),
      },
      {
        path: 'clientes',
        lazy: async () => ({ Component: (await import('./features/admin/CustomersPage.tsx')).CustomersPage }),
      },
      {
        path: 'clientes/:customerId',
        lazy: async () => ({ Component: (await import('./features/admin/CustomerProfilePage.tsx')).CustomerProfilePage }),
      },
      {
        path: 'productos',
        lazy: async () => ({ Component: (await import('./features/admin/ProductsPage.tsx')).ProductsPage }),
      },
      {
        path: 'productos/nuevo',
        lazy: async () => ({ Component: (await import('./features/admin/NewProductPage.tsx')).NewProductPage }),
      },
      {
        path: 'productos/:productId',
        lazy: async () => ({ Component: (await import('./features/admin/ProductEditPage.tsx')).ProductEditPage }),
      },
      {
        path: 'productos/:productId/variantes/:variantId/stock',
        lazy: async () => ({ Component: (await import('./features/admin/StockPage.tsx')).StockPage }),
      },
      {
        path: 'stock',
        lazy: async () => ({ Component: (await import('./features/admin/StockOverviewPage.tsx')).StockOverviewPage }),
      },
      {
        path: 'categorias',
        lazy: async () => ({ Component: (await import('./features/admin/CategoriesPage.tsx')).CategoriesPage }),
      },
      {
        path: 'categorias/:categoryId/guia-de-talles',
        lazy: async () => ({ Component: (await import('./features/admin/SizeGuidePage.tsx')).SizeGuidePage }),
      },
      {
        path: 'cupones',
        lazy: async () => ({ Component: (await import('./features/admin/CouponsPage.tsx')).CouponsPage }),
      },
      {
        path: 'resenas',
        lazy: async () => ({ Component: (await import('./features/admin/ReviewsPage.tsx')).ReviewsPage }),
      },
      {
        path: 'envios',
        lazy: async () => ({ Component: (await import('./features/admin/ShippingPage.tsx')).ShippingPage }),
      },
      {
        path: 'datos-de-la-tienda',
        lazy: async () => ({ Component: (await import('./features/admin/StoreSettingsPage.tsx')).StoreSettingsPage }),
      },
      {
        path: 'portada',
        lazy: async () => ({ Component: (await import('./features/admin/HomeBannerPage.tsx')).HomeBannerPage }),
      },
      {
        path: 'actividad',
        lazy: async () => ({ Component: (await import('./features/admin/ActivityPage.tsx')).ActivityPage }),
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];

/**
 * What the browser router is built from (T-E8-21): every route under one root that owns the Spanish error page
 * (a failed lazy chunk, typically after a deploy), the first-visit fallback and the navigation progress bar.
 */
export function withRoot(children: RouteObject[]): RouteObject[] {
  return [
    {
      element: <RootLayout />,
      errorElement: <RouteErrorPage />,
      hydrateFallbackElement: <RouteLoadingFallback />,
      children,
    },
  ];
}

/**
 * The routes of the browser router (T-E8-22). `prefetchAdminSession` starts the session request while the back
 * office layout's chunk downloads, so the first page waits for the slower of the two, not for both in a row.
 */
export function createAppRoutes(prefetchAdminSession: () => Promise<unknown>): RouteObject[] {
  return withRoot(
    routes.map((route) =>
      route.path === '/admin'
        ? {
            ...route,
            lazy: async () => {
              const [{ AdminLayout }] = await Promise.all([
                import('./features/admin/AdminLayout.tsx'),
                prefetchAdminSession(),
              ]);
              return { Component: AdminLayout };
            },
          }
        : route,
    ),
  );
}
