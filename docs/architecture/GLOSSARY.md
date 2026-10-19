# Glossary (Spanish business terms → English code names)

The code, database and API use the English names. Spanish appears only in user-facing texts.

| Spanish | English (code) | Notes |
|---|---|---|
| Producto | `Product` | Parent item; groups variants |
| Variante / SKU | `Variant` / `Sku` | One size + color combination |
| Talle | `Size` | |
| Color | `Color` | |
| Categoría | `Category` | |
| Temporada | `Season` | `SUMMER`, `MID_SEASON`, `WINTER` |
| Público | `Audience` | `WOMEN`, `MEN`, `KIDS` |
| Carrito | `Cart` | |
| Cupón | `Coupon` | Manual code or automatic rule |
| Pedido | `Order` | Table `orders` (`order` is a reserved word) |
| Pago | `Payment` | |
| Envío | `Shipping` | Cost by province or zone |
| DNI | `NationalId` | Sensitive personal data |
| Libreta de direcciones | `AddressBook` | A customer's saved addresses, at most 10 (UC-20) |
| Dirección principal | primary address (`is_primary`) | Exactly one when the book has addresses; preselected at checkout (UC-21) |
| Reseña | `Review` | |
| Administrador | `Administrator` | Single admin: the shop owner |
| Visitante | `Visitor` | Not signed in |
| Invitado | `Guest` | Buys without an account |
| Cliente | `Customer` | Registered user |
| Comprador | `Buyer` | Whoever placed an order, guest or customer. The email type `ORDER_PAID_CUSTOMER` goes to the buyer |
| Reserva de stock | `StockReservation` | Expires after 30 minutes |
| Movimiento de stock | `StockMovement` | Append-only |
| Enlace de seguimiento | `TrackingLink` | Token-based order page |
| Botón de arrepentimiento | `WithdrawalRequest` | Legal right to cancel within 10 days; tracked with a code |
| Identidad externa | `ExternalIdentity` | A Google or Facebook account linked to a `User` |
| Guía de talles | `SizeGuide` | Measurements per category |
| Banner de personajes | `HomeBanner` | The one editable banner of the home page (UC-32) |
| Backoffice | `back office` | Admin area |

## Order states

| Spanish | English |
|---|---|
| PENDIENTE_PAGO | `AWAITING_PAYMENT` |
| PAGADO | `PAID` |
| PREPARANDO | `PREPARING` |
| ENVIADO | `SHIPPED` |
| ENTREGADO | `DELIVERED` |
| CANCELADO | `CANCELLED` |
| EXPIRADO | `EXPIRED` |

## Table and route names

| Concept | Table | API route |
|---|---|---|
| Category | `category` | `/api/catalog/categories` |
| Product | `product` | `/api/catalog/products` |
| Variant | `variant` | nested in product |
| Product image | `product_image` | `/api/admin/catalog/products/{id}/images` |
| Stock | `stock_level`, `stock_movement` | `/api/admin/stock-adjustments` |
| Cart | `cart`, `cart_item` | `/api/cart` |
| Coupon | `coupon` | `/api/cart/coupon`, `/api/admin/coupons` |
| Order | `orders`, `order_item` | `/api/checkout`, `/api/orders/{token}` |
| Payment | `payment`, `payment_event` | `/api/payments/mercadopago/webhook` |
| User | `app_user` | `/api/account/...` |
| Address | `address` | `/api/account/addresses` |
| Review | `review` | `/api/catalog/products/{slug}/reviews` |
| Idempotency | `idempotency_key` | `Idempotency-Key` header |
| Email | `email_outbox`, `email_event` | `/api/mail/webhook` |
| Shipping | `shipping_zone_rate`, `shop_setting` | `/api/admin/shipping/zones` |
