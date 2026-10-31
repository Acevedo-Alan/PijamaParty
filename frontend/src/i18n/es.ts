/**
 * User-facing texts (Spanish). Code, API routes and identifiers stay in English; only these strings and the public
 * storefront paths (/categoria, /producto: UC-01) are shown to customers (CLAUDE.md "Language"). Keep one entry per
 * text so a future translation tool can take this file as is.
 */
export const es = {
  store: {
    name: 'Pijama Party Santa Fe',
  },
  /** T-E6-23 (RNF-31.1): the descriptions search engines show; drafted for the owner to review. */
  seo: {
    homeDescription: 'Pijamas y ropa de dormir para mujer, hombre y niños. Tienda online de Pijama Party Santa Fe.',
    categoryDescription: (category: string) => `${category}: pijamas y ropa de dormir de Pijama Party Santa Fe.`,
    faqDescription: 'Costos y plazos de envío, medios de pago y cambios de Pijama Party Santa Fe.',
    howToBuyDescription: 'Cómo comprar en Pijama Party Santa Fe: elegí tus pijamas, pagá y seguí tu pedido en pocos pasos.',
    returnsDescription: 'Cambios por talle o color dentro de los 30 días y arrepentimiento de compra en Pijama Party Santa Fe.',
    termsDescription: 'Términos y condiciones de compra en la tienda online de Pijama Party Santa Fe.',
    sizeGuidesDescription: 'Guía de talles de los pijamas de Pijama Party Santa Fe, con las medidas de cada categoría.',
    withdrawalDescription:
      'Botón de arrepentimiento de Pijama Party Santa Fe: revocá tu compra dentro de los 10 días corridos, sin costo.',
    /** The price (with "Desde" when prices differ), then the description cut at 200 characters. */
    productDescription: (price: string, description: string) =>
      description === '' ? price : `${price}. ${description}`,
  },
  catalog: {
    allTitle: 'Todos los productos',
    categoryTitle: 'Productos',
    fromPrice: 'Desde',
    soldOut: 'Agotado',
    /** D-05: a product created in the last 30 days. */
    isNew: 'Nuevo',
    /** T-E7-34: the newest products, in the empty cart and a search without results. */
    newest: 'Lo más nuevo',
    /** D-04: what a screen reader hears for the swatches of a card, e.g. "Colores: Rosa, Beige y Salvia". */
    colors: (colors: readonly string[]) =>
      `${colors.length === 1 ? 'Color' : 'Colores'}: ${new Intl.ListFormat('es', { type: 'conjunction' }).format(colors)}`,
    /** T-E7-35: what a screen reader hears for the sizes in stock of a card, e.g. "Talles: S y L". */
    sizes: (sizes: readonly string[]) =>
      `${sizes.length === 1 ? 'Talle' : 'Talles'}: ${new Intl.ListFormat('es', { type: 'conjunction' }).format(sizes)}`,
    /** D-11: the number of products that match, e.g. "1 producto" or "1.250 productos". */
    productCount: (count: number) =>
      `${count.toLocaleString('es-AR')} ${count === 1 ? 'producto' : 'productos'}`,
    showMore: 'Ver más productos',
    /** How far the visitor got, above "Ver más" (E7), e.g. "Viste 24 de 134". */
    seenOf: (seen: number, total: number) =>
      `Viste ${seen.toLocaleString('es-AR')} de ${total.toLocaleString('es-AR')}`,
    loading: 'Cargando productos…',
    loadingMore: 'Cargando…',
    emptyShop: 'Todavía no hay productos.',
    emptyCategory: 'No hay productos en esta categoría.',
    seeAll: 'Ver todo el catálogo',
    loadError: 'No pudimos cargar los productos.',
    loadMoreError: 'No pudimos cargar más productos.',
    retry: 'Reintentar',
    noMatches: 'No encontramos productos con los filtros elegidos.',
    clearFilters: 'Limpiar filtros',
    /** T-E7-06: the grid or the list, kept for the visit. */
    view: 'Vista del listado',
    gridView: 'Ver en cuadrícula',
    listView: 'Ver en lista',
  },
  home: {
    /** UC-30 (T-E7-04): the fixed texts of the home page (D-03); changing them needs a deploy. */
    carousel: {
      label: 'Bienvenida',
      roleDescription: 'carrusel',
      slideRoleDescription: 'imagen',
      /** The label of a slide, e.g. "1 de 3". */
      slide: (position: number, total: number) => `${String(position)} de ${String(total)}`,
      showSlide: (position: number, total: number) => `Ver imagen ${String(position)} de ${String(total)}`,
      pause: 'Pausar carrusel',
      resume: 'Reanudar carrusel',
    },
    eyebrow: 'Nueva colección · Santa Fe',
    title: 'Noches suaves, mañanas con calma',
    text: 'Pijamas y ropa de descanso para bajar el ritmo y sentirte bien, todos los días.',
    discover: 'Descubrí la colección',
    audienceTitle: 'Elegí para quién',
    audienceText: 'Ropa cómoda para cada integrante de la familia.',
    /** T-E7-11: below "Todos los productos" on the home page, as in the prototype. */
    listingText: 'Filtrá por talle, color, temporada y más.',
    audiences: { WOMEN: 'Mujer', MEN: 'Hombre', KIDS: 'Niños' },
  },
  filters: {
    /** The bar of buttons that open the panel at one group (T-E7-06). */
    bar: 'Filtrar por',
    /** A group's button with the number of its chosen values, e.g. "Talle (2)". */
    groupWithCount: (group: string, count: number) => (count === 0 ? group : `${group} (${String(count)})`),
    title: 'Filtros',
    close: 'Cerrar filtros',
    apply: 'Ver resultados',
    clear: 'Limpiar',
    loading: 'Cargando filtros…',
    loadError: 'No pudimos cargar los filtros.',
    retry: 'Reintentar',
    none: 'No hay filtros para estos productos.',
    size: 'Talle',
    color: 'Color',
    material: 'Material',
    season: 'Temporada',
    audience: 'Público',
    price: 'Precio',
    priceHint: (min: string, max: string) => `Entre ${min} y ${max}`,
    minPrice: 'Mínimo ($)',
    maxPrice: 'Máximo ($)',
    priceRangeError: 'El mínimo no puede ser mayor que el máximo.',
    priceInvalid: 'Ingresá montos en pesos, sin centavos.',
    seasons: { SUMMER: 'Verano', MID_SEASON: 'Media estación', WINTER: 'Invierno' },
    audiences: { WOMEN: 'Mujer', MEN: 'Hombre', KIDS: 'Infantil' },
    /** The sizes of the panel, apart by kind when there are several (changes to UC-02). */
    sizeKinds: { letters: 'Letras', numbers: 'Números', other: 'Otros' },
    /** A group's header says what is chosen in it, e.g. "M, L", or "3 elegidos" when that would not fit a phone. */
    chosenSummary: (values: readonly string[]) => {
      const joined = values.join(', ');
      return values.length === 1 || joined.length <= 20 ? joined : `${String(values.length)} elegidos`;
    },
    priceFrom: (min: string) => `Desde ${min}`,
    priceUpTo: (max: string) => `Hasta ${max}`,
    priceBetween: (min: string, max: string) => `${min} – ${max}`,
    /** The chips of the applied filters above the grid. */
    applied: 'Filtros aplicados',
    sizeChip: (size: string) => `Talle ${size}`,
    removeChip: (label: string) => `Quitar filtro ${label}`,
    clearAll: 'Limpiar todo',
  },
  product: {
    /** E7: back in history when the visitor came from the store, otherwise to the home page. */
    breadcrumbs: 'Ruta de navegación',
    home: 'Inicio',
    loading: 'Cargando producto…',
    loadError: 'No pudimos cargar el producto.',
    retry: 'Reintentar',
    fromPrice: 'Desde',
    material: 'Material',
    season: 'Temporada',
    gallery: 'Fotos del producto',
    /** Alternative text of a photo: name, color and view (RNF-31.4), e.g. "Pijama Luna, rosa, foto 2". */
    photoAlt: (name: string, color: string | null, position: number) =>
      color === null ? `${name}, foto ${String(position)}` : `${name}, ${color}, foto ${String(position)}`,
    showPhoto: (position: number) => `Ver foto ${String(position)}`,
    noPhoto: 'Sin fotos',
    /** E7: the enlarged view of the main photo. */
    enlargePhoto: 'Ampliar foto',
    enlarged: 'Foto ampliada',
    closeEnlarged: 'Cerrar foto ampliada',
    previousPhoto: 'Foto anterior',
    nextPhoto: 'Foto siguiente',
    /** The position in the enlarged view, e.g. "2 / 5". */
    photoCount: (position: number, total: number) => `${String(position)} / ${String(total)}`,
    size: 'Talle',
    color: 'Color',
    /** The units to add (E7), from 1 to the available stock of the picked variant. */
    quantity: 'Cantidad',
    lessUnits: 'Restar una unidad',
    moreUnits: 'Sumar una unidad',
    /** The name of a size or color without stock, e.g. "Celeste, agotado". */
    soldOutOption: (value: string) => `${value}, agotado`,
    /** The exact available stock of the picked size and color (UC-03). */
    available: (count: number) => (count === 1 ? 'Queda 1 disponible' : `Hay ${String(count)} disponibles`),
    noStock: 'Sin stock en este talle y color',
    /** T-E7-36: a picked color sold out in every size. */
    noStockInColor: 'Sin stock en este color',
    /** T-E7-36: below a group still without a pick, once the add button was pressed. */
    pickSize: 'Elegí un talle',
    pickColor: 'Elegí un color',
    /** E7: what the fixed bar on phones says is picked, e.g. "Talle M · Rosa"; null without picks. */
    pickedSummary: (size: string | null, color: string | null) => {
      const parts = [size === null ? null : `Talle ${size}`, color].filter((part) => part !== null);
      return parts.length === 0 ? null : parts.join(' · ');
    },
    addToCart: 'Agregar al carrito',
    adding: 'Agregando…',
    /** E7 motion: the add button confirms a successful add for a moment. */
    added: 'Agregado',
    /** UC-08: an add above the available stock, e.g. "Solo quedan 3". */
    onlyLeft: (count: number) =>
      count === 0 ? 'Sin stock' : count === 1 ? 'Solo queda 1' : `Solo quedan ${String(count)}`,
    noLongerAvailable: 'Este producto ya no está disponible.',
    addError: 'No pudimos agregar el producto. Probá de nuevo.',
    /** RNF-31.5: the size guide of the category, in a dialog opened beside "Talle" (T-E7-36). */
    sizeGuide: 'Guía de talles',
    closeSizeGuide: 'Cerrar guía de talles',
    sizeGuideTable: 'Tabla de talles',
    /** A blank value of the size guide. */
    noMeasure: '–',
    /** E7: the collapsed sections below the add button. */
    composition: 'Composición',
    returns: 'Cambios y devoluciones',
    returnsText: 'Tenés 10 días corridos desde que recibiste tu compra para arrepentirte, sin costo para vos.',
    returnsLink: 'Cómo cambiar o devolver un producto',
    /** E7 (D-07): the shipping cost of one province, the same the checkout charges (UC-12). */
    shipping: 'Calculá el envío',
    chooseProvince: 'Elegí tu provincia',
    shippingTo: (province: string, cost: string) => `Envío a ${province}: ${cost}`,
    freeShippingTo: (province: string) => `Envío gratis a ${province}`,
    noShippingTo: 'No hacemos envíos a esa provincia',
    /** E7 (D-08): the newest products of the same category, below the product. */
    related: 'Completá tu look',
    /** T-E7-33: the last products this browser opened. */
    recentlyViewed: 'Vistos recientemente',
  },
  reviews: {
    /** UC-24 (T-E5-07): the approved reviews on the product page. */
    product: {
      title: 'Opiniones',
      /** The average and count, e.g. "4,5 · 12 opiniones". */
      summary: (average: string, count: number) =>
        `${average} · ${count === 1 ? '1 opinión' : `${String(count)} opiniones`}`,
      /** The accessible name of the average, e.g. "4,5 de 5 estrellas". */
      stars: (average: string) => `${average} de 5 estrellas`,
      /** The accessible name of a review's rating, e.g. "4 de 5 estrellas". */
      rating: (count: number) => `${String(count)} de 5 estrellas`,
      empty: 'Todavía no hay opiniones de este producto.',
      loading: 'Cargando opiniones…',
      loadError: 'No pudimos cargar las opiniones.',
      retry: 'Reintentar',
      more: 'Ver más opiniones',
      loadingMore: 'Cargando…',
      moreError: 'No pudimos cargar más opiniones. Probá de nuevo.',
    },
    /** UC-24: the customer's own review on the product page. */
    mine: {
      title: 'Tu opinión',
      signIn: 'Ingresá',
      signInToReview: 'para dejar tu opinión si compraste este producto.',
      loading: 'Cargando tu opinión…',
      loadError: 'No pudimos cargar tu opinión.',
      notABuyer: 'Solo pueden opinar quienes compraron este producto.',
      statusLabel: 'Estado:',
      status: { PENDING: 'En revisión', APPROVED: 'Publicada', REJECTED: 'No publicada' },
      rating: 'Tu calificación',
      /** The accessible name of a rating, e.g. "4 estrellas". */
      stars: (count: number) => (count === 1 ? '1 estrella' : `${String(count)} estrellas`),
      yourRating: (count: number) => `Tu calificación: ${count === 1 ? '1 estrella' : `${String(count)} estrellas`}.`,
      pickRating: 'Elegí una calificación de 1 a 5 estrellas.',
      text: 'Contanos qué te pareció (opcional)',
      /** Characters typed of the most allowed, e.g. "120/1000". */
      count: (typed: number, max: number) => `${String(typed)}/${String(max)}`,
      textTooLong: (max: number) => `El texto puede tener hasta ${max.toLocaleString('es-AR')} caracteres.`,
      send: 'Enviar opinión',
      save: 'Guardar cambios',
      sending: 'Enviando…',
      sent: '¡Gracias! Tu opinión se publicará cuando la revisemos.',
      error: 'No pudimos guardar tu opinión. Probá de nuevo.',
    },
  },
  cart: {
    title: 'Mi carrito',
    /** The header button with the units in the cart, e.g. "Carrito, 3 productos". */
    open: (count: number) =>
      count === 0 ? 'Carrito' : count === 1 ? 'Carrito, 1 producto' : `Carrito, ${String(count)} productos`,
    close: 'Cerrar carrito',
    loading: 'Cargando carrito…',
    loadError: 'No pudimos cargar el carrito.',
    retry: 'Reintentar',
    empty: 'Tu carrito está vacío.',
    emptyHint: 'Explorá la colección para empezar.',
    keepShopping: 'Empezar a comprar',
    /** Alternative text of an item's photo (RNF-31.4), e.g. "Pijama Luna, Rosa". */
    photoAlt: (name: string, color: string) => `${name}, ${color}`,
    /** An item's color and size, e.g. "Rosa · Talle M". */
    variant: (color: string, size: string) => `${color} · Talle ${size}`,
    /** The quantity control of an item (UC-09), named after its product, e.g. "Cantidad de Pijama Luna". */
    quantityOf: (name: string) => `Cantidad de ${name}`,
    decrease: (name: string) => `Restar una unidad de ${name}`,
    increase: (name: string) => `Sumar una unidad de ${name}`,
    remove: 'Quitar',
    removeLabel: (name: string) => `Quitar ${name} del carrito`,
    changeError: 'No pudimos cambiar el carrito. Probá de nuevo.',
    /** An UNAVAILABLE item: sold out or no longer sold; it is not bought. */
    soldOut: 'Sin stock',
    /** An INSUFFICIENT_STOCK item, after "Solo quedan N". */
    lowerToBuy: 'Bajá la cantidad para comprarlo.',
    /** A changed unit price, read before the struck-through previous one and the current one. */
    previousPrice: 'Precio anterior',
    currentPrice: 'Precio actual',
    pricesChanged: 'Cambiaron los precios de algunos productos.',
    acknowledge: 'Entendido',
    acknowledgeError: 'No pudimos confirmar los precios. Probá de nuevo.',
    unknownProduct: 'Producto no disponible',
    subtotal: 'Subtotal',
    discount: 'Descuento',
    /** The discount line of the coupon that discounts (UC-10, UC-23), e.g. "Descuento PIJAMA10". */
    discountWith: (code: string) => `Descuento ${code}`,
    total: 'Total',
    /** The free-shipping bar (E7), with the missing amount already formatted, e.g. "$ 25.001". */
    freeShippingMissing: (missing: string) => `Te faltan ${missing} para envío gratis`,
    freeShippingReached: '¡Tenés envío gratis!',
    /** The notice after adding from the product page (E7). */
    addedNotice: 'Agregado al carrito',
    closeNotice: 'Cerrar aviso',
    couponCode: 'Código de cupón',
    applyCoupon: 'Aplicar',
    /** The applied coupon, e.g. "Cupón PIJAMA10". */
    coupon: (code: string) => `Cupón ${code}`,
    removeCoupon: 'Quitar',
    removeCouponLabel: (code: string) => `Quitar el cupón ${code}`,
    /** Why a code was refused, or why the applied coupon does not discount now (UC-10). */
    couponRefused: {
      UNKNOWN: 'No encontramos ese cupón.',
      INACTIVE: 'Este cupón no está activo.',
      NOT_STARTED: 'Este cupón todavía no está vigente.',
      EXPIRED: 'El cupón venció.',
      USED_UP: 'Este cupón ya no tiene usos disponibles.',
    },
    /** A cart below the coupon's minimum purchase, with the missing amount already formatted, e.g. "$ 3.000". */
    couponBelowMinimum: (missing: string) => `Te faltan ${missing} para usar este cupón.`,
    /** UC-23: the applied code applies, but an automatic coupon that takes more discounts instead. */
    couponNotAdded: (code: string) => `Tu cupón ${code} no se suma: ya tenés un descuento mejor.`,
    couponTooManyAttempts: 'Hiciste muchos intentos. Esperá un minuto y probá de nuevo.',
    couponApplyError: 'No pudimos aplicar el cupón. Probá de nuevo.',
    couponRemoveError: 'No pudimos quitar el cupón. Probá de nuevo.',
    startCheckout: 'Iniciar compra',
  },
  checkout: {
    title: 'Finalizar compra',
    backToStore: '← Volver a la tienda',
    loading: 'Cargando tu compra…',
    loadError: 'No pudimos cargar tu carrito.',
    retry: 'Reintentar',
    empty: 'Tu carrito está vacío.',
    emptyHint: 'Agregá productos para comprarlos.',
    goShopping: 'Ir a la tienda',
    steps: 'Pasos de la compra',
    /** The step indicator's names, each after its numbered circle (T-E7-38). */
    stepNames: { 1: 'Envío', 2: 'Pago', 3: 'Revisión' },
    currentStep: 'paso actual',
    /** T-E7-38: what screen readers hear after a completed step. */
    stepDone: 'completado',
    /** A cart with items that cannot be bought as they are (UC-09). */
    cartNotReady: 'Algunos productos de tu carrito no se pueden comprar así: no hay stock suficiente o cambió su precio.',
    reviewCart: 'Revisar carrito',
    shippingTitle: 'Datos de envío',
    /** UC-21: the saved addresses of a signed-in customer, and the choice of a new one. */
    savedAddresses: 'Dirección de envío',
    otherAddress: 'Otra dirección',
    saveAddress: 'Guardar en mis direcciones',
    /** D-13 (T-E7-10): a guest on step 1 may sign in and come back to the checkout with the cart. */
    haveAccount: '¿Ya tenés una cuenta?',
    signIn: 'Iniciar sesión',
    fields: {
      firstName: 'Nombre',
      lastName: 'Apellido',
      email: 'Email',
      phone: 'Teléfono',
      nationalId: 'DNI',
      street: 'Calle',
      number: 'Número',
      floor: 'Piso',
      apartment: 'Departamento',
      city: 'Ciudad',
      province: 'Provincia',
      postalCode: 'Código postal',
    },
    phoneHint: 'Con código de área',
    optional: 'Opcional',
    chooseProvince: 'Elegí tu provincia',
    /** What each field needs, shown when it is empty or breaks a rule of UC-13. */
    errors: {
      firstName: 'Ingresá tu nombre, de hasta 50 caracteres.',
      lastName: 'Ingresá tu apellido, de hasta 50 caracteres.',
      email: 'Ingresá un email válido.',
      phone: 'Ingresá un teléfono con código de área, de 8 a 15 números.',
      nationalId: 'Ingresá un DNI de 7 u 8 números, sin puntos.',
      street: 'Ingresá la calle.',
      number: 'Ingresá el número, de hasta 10 caracteres.',
      floor: 'El piso puede tener hasta 10 caracteres.',
      apartment: 'El departamento puede tener hasta 10 caracteres.',
      city: 'Ingresá la ciudad.',
      province: 'Elegí la provincia.',
      postalCode: 'Ingresá un código postal de 4 números o un CPA como S3000ABC.',
    },
    noShipping: 'Por ahora no hacemos envíos a esta provincia.',
    /** Before the suggested email, a button, and a closing "?" (T-E3-24). */
    emailSuggestion: '¿Quisiste decir',
    shippingLoading: 'Calculando el envío…',
    shippingError: 'No pudimos calcular el envío.',
    toPayment: 'Continuar a pago',
    paymentTitle: 'Medio de pago',
    /** The options of step 2 (T-E3b-04); MODO's name stands in for its logo until the brand kit arrives. */
    providers: {
      MERCADOPAGO: { name: 'MercadoPago', hint: 'Tarjetas de crédito y débito, dinero en cuenta' },
      MODO: { name: 'MODO', hint: 'Pagá con la app de tu banco' },
    },
    toReview: 'Revisar compra',
    back: 'Volver',
    reviewTitle: 'Revisá tu compra',
    contactAndShipping: 'Contacto y envío',
    paymentMethod: 'Medio de pago',
    edit: 'Editar',
    /** The edit links of the review, named after their block, e.g. "Editar contacto y envío". */
    editLabel: (block: string) => `Editar ${block.toLowerCase()}`,
    /** The address lines of the review: "Calle 123, piso 2, depto. B". */
    floorPart: (floor: string) => `piso ${floor}`,
    apartmentPart: (apartment: string) => `depto. ${apartment}`,
    /** The city, province and postal code, e.g. "Santa Fe, Santa Fe (3000)". */
    place: (city: string, province: string, postalCode: string) => `${city}, ${province} (${postalCode})`,
    nationalIdLine: (nationalId: string) => `DNI ${nationalId}`,
    termsBefore: 'Acepto los ',
    terms: 'términos y condiciones',
    termsBetween: ' y la ',
    returns: 'política de cambios y devoluciones',
    termsRequired: 'Aceptá los términos para confirmar la compra.',
    confirm: 'Confirmar y pagar',
    /** T-E7-38: the final button with the total the summary shows, e.g. "Confirmar y pagar · $ 219.600". */
    confirmWithTotal: (total: string) => `Confirmar y pagar · ${total}`,
    /** T-E7-38: under the final button; where the buyer pays, not a security promise. */
    payAt: (provider: string) => `Vas a completar el pago en ${provider}.`,
    confirming: 'Confirmando…',
    openingPayment: 'Abriendo el pago…',
    /** Why the confirmation did not place the order. */
    refused: {
      emptyCart: 'Tu carrito quedó vacío. Agregá productos para comprarlos.',
      cartNotReady: 'Algunos productos cambiaron. Revisá tu carrito antes de confirmar.',
      couponNotApplicable: 'Tu cupón ya no aplica. Quitalo para seguir con la compra.',
      tooManyAttempts: 'Hiciste muchos intentos. Esperá un minuto y probá de nuevo.',
      paymentProviderNotEnabled: 'Ese medio de pago ya no está disponible. Elegí otro para seguir.',
      unknown: 'No pudimos confirmar tu compra. Probá de nuevo.',
    },
    summaryTitle: 'Mi compra',
    /** T-E7-38: opens the cart panel, where quantities and the coupon change. */
    backToCart: 'Volver al carrito',
    /** An item's color and size, e.g. "Rosa · Talle M"; the quantity goes on its own line (T-E7-38). */
    itemDetail: (color: string, size: string) => `${color} · Talle ${size}`,
    itemQuantity: (quantity: number) => `Cantidad: ${String(quantity)}`,
    subtotal: 'Subtotal',
    discount: 'Descuento',
    discountWith: (code: string) => `Descuento ${code}`,
    shipping: 'Envío',
    shippingPending: 'Elegí tu provincia',
    free: 'Gratis',
    total: 'Total',
  },
  checkoutResult: {
    title: 'Gracias por tu compra',
    /** Back from MercadoPago or MODO in a tab without the order's token: another tab or device. */
    noOrder: 'Cuando se confirme el pago te va a llegar un email con el enlace para seguir tu pedido.',
    backToStore: 'Volver a la tienda',
  },
  consent: {
    title: 'Cookies',
    text: 'Usamos Google Analytics y Meta Pixel para medir las visitas y mejorar la tienda. Solo se activan si aceptás.',
    accept: 'Aceptar',
    reject: 'Rechazar',
    current: { accepted: 'Ahora están aceptadas.', rejected: 'Ahora están rechazadas.' },
    footerLink: 'Cookies',
  },
  search: {
    label: 'Buscar productos',
    placeholder: 'Buscar productos',
    open: 'Buscar productos',
    close: 'Cerrar',
    title: 'Buscar',
    suggestions: 'Sugerencias',
    /** The title of the results page, e.g. "Resultados para "pijama"". */
    resultsFor: (text: string) => `Resultados para "${text}"`,
    noResults: (text: string) => `No encontramos productos para "${text}". Probá con otra palabra o mirá las categorías.`,
    categories: 'Categorías',
    tooShort: 'Escribí al menos 2 letras para buscar.',
  },
  /** UC-31 (T-E7-02, T-E7-39): the header, its navigation, the phone menu and the announcement ticker. */
  header: {
    navigation: 'Navegación principal',
    menuTitle: 'Menú',
    /** T-E7-39: inside a level of the phone menu, back to its first level. */
    back: 'Volver',
    backLabel: 'Volver al menú',
    /** T-E7-39: the first link of the "Todo" level, to every product. */
    seeAll: 'Ver todo',
    /** T-E7-39: the ticker above the header; no installments or discounts here (D-01, D-02). */
    ticker: {
      label: 'Anuncios',
      carrier: 'Envíos por Correo Argentino',
      pause: 'Pausar anuncios',
      resume: 'Reanudar anuncios',
    },
    all: 'Todo',
    audiences: { WOMEN: 'Mujer', MEN: 'Hombre', KIDS: 'Niños' },
    winter: 'Temporada Invierno',
    openMenu: 'Abrir menú',
    closeMenu: 'Cerrar menú',
    account: 'Tu cuenta',
    shortcuts: 'Accesos rápidos',
    /** T-E7-37: the menu's cart shortcut with the units in the cart, e.g. "Mi carrito, 3 productos". */
    cartShortcut: (count: number) =>
      count === 0 ? 'Mi carrito' : count === 1 ? 'Mi carrito, 1 producto' : `Mi carrito, ${String(count)} productos`,
    help: '¿Necesitás ayuda?',
    /** No installments or discounts here until the owner decides them (D-01, D-02). */
    freeShippingFrom: (amount: string) => `Envío gratis desde ${amount}`,
  },
  /**
   * UC-31 (T-E7-03, T-E7-20, T-E7-24, T-E7-25): the footer's store block and columns; the withdrawal and Cookies links
   * keep their own texts.
   */
  footer: {
    /** The first line of the footer's store block. */
    tagline: 'Pijamas para toda la familia',
    /** The header's navigation, again. */
    shop: 'Tienda',
    help: 'Ayuda y legales',
    /** T-E7-28: the help pages, after the FAQ. */
    howToBuy: 'Cómo comprar',
    sizeGuides: 'Guía de talles',
    returns: 'Cambios y devoluciones',
    terms: 'Términos y condiciones',
    contact: 'Contacto',
    social: 'Redes sociales',
    instagram: 'Instagram',
    tiktok: 'TikTok',
    payments: 'Medios de pago',
    /** T-E7-26: the carrier, under the payment providers. */
    shipping: 'Envíos',
    carrier: 'Correo Argentino',
    /** The tooltip of the address. */
    maps: 'Ver en Google Maps',
    /** The last line, e.g. "© 2026 Pijama Party Santa Fe". */
    copyright: (year: number, store: string) => `© ${String(year)} ${store}`,
  },
  /**
   * UC-31 (T-E7-21): the benefits bar above the footer. The titles are uppercased in CSS. It promises no payment
   * method, installments, discount or surcharge the checkout does not have (D-01, D-02).
   */
  benefits: {
    label: 'Beneficios',
    shippingTitle: 'Enviamos tu compra',
    shippingText: 'Entregas dentro de Santa Fe Capital y todo el país',
    paymentsTitle: 'Todas las formas de pago',
    securityTitle: 'Comprá con seguridad',
    securityText: 'Tus datos siempre protegidos',
  },
  /** UC-30 (T-E7-03, D-09): "Contactanos" at the end of the home page, links only. */
  contact: {
    title: 'Contactanos',
    text: 'Escribinos para resolver dudas o finalizar tu pedido de forma personalizada.',
    whatsApp: 'Pedir por WhatsApp',
    /** T-E7-27: the name of the floating button. */
    whatsAppButton: 'Escribinos por WhatsApp',
  },
  sort: {
    label: 'Ordenar por',
    RELEVANCE: 'Relevancia',
    NEWEST: 'Novedades',
    PRICE_ASC: 'Menor precio',
    PRICE_DESC: 'Mayor precio',
  },
  /** The 24 jurisdictions of Argentina (UC-12), by the Province code of the API. */
  provinces: {
    BUENOS_AIRES: 'Buenos Aires',
    CIUDAD_DE_BUENOS_AIRES: 'Ciudad Autónoma de Buenos Aires',
    CATAMARCA: 'Catamarca',
    CHACO: 'Chaco',
    CHUBUT: 'Chubut',
    CORDOBA: 'Córdoba',
    CORRIENTES: 'Corrientes',
    ENTRE_RIOS: 'Entre Ríos',
    FORMOSA: 'Formosa',
    JUJUY: 'Jujuy',
    LA_PAMPA: 'La Pampa',
    LA_RIOJA: 'La Rioja',
    MENDOZA: 'Mendoza',
    MISIONES: 'Misiones',
    NEUQUEN: 'Neuquén',
    RIO_NEGRO: 'Río Negro',
    SALTA: 'Salta',
    SAN_JUAN: 'San Juan',
    SAN_LUIS: 'San Luis',
    SANTA_CRUZ: 'Santa Cruz',
    SANTA_FE: 'Santa Fe',
    SANTIAGO_DEL_ESTERO: 'Santiago del Estero',
    TIERRA_DEL_FUEGO: 'Tierra del Fuego',
    TUCUMAN: 'Tucumán',
  },
  tracking: {
    /** The page title with the order number, e.g. "Pedido #1001". */
    title: (number: number) => `Pedido #${String(number)}`,
    greeting: (firstName: string) => `Hola, ${firstName}`,
    loading: 'Cargando tu pedido…',
    loadError: 'No pudimos cargar tu pedido.',
    retry: 'Reintentar',
    notFoundTitle: 'No encontramos tu pedido',
    notFoundBody: 'Revisá que el enlace esté completo, tal como te llegó.',
    backToStore: 'Volver a la tienda',
    /** The order states (GLOSSARY.md). */
    statuses: {
      AWAITING_PAYMENT: 'Pago pendiente',
      PAID: 'Pagado',
      PREPARING: 'En preparación',
      SHIPPED: 'Enviado',
      DELIVERED: 'Entregado',
      CANCELLED: 'Cancelado',
      EXPIRED: 'Expirado',
    },
    /** The time the reservation ends, already formatted, e.g. "14:30". */
    reservedUntil: (time: string) => `Reservamos tus productos hasta las ${time}. Completá el pago para confirmar la compra.`,
    pay: 'Pagar',
    paying: 'Abriendo el pago…',
    /** Back from an approved payment before its confirmation arrived (T-E3-11). */
    confirmingPayment: 'Estamos confirmando tu pago. Esto puede tardar unos segundos.',
    reservationOver: 'La reserva de tus productos venció y el pedido no se pagó. Podés volver a comprarlos desde la tienda.',
    providerUnavailable: 'El medio de pago no respondió. Esperá un momento y probá de nuevo.',
    tooManyAttempts: 'Hiciste muchos intentos. Esperá un minuto y probá de nuevo.',
    payError: 'No pudimos abrir el pago. Probá de nuevo.',
    shipment: 'Número de seguimiento del envío',
    items: 'Productos',
    /** An item's color, size and quantity, e.g. "Rosa · Talle M · 2 unidades". */
    itemDetail: (color: string, size: string, quantity: number) =>
      `${color} · Talle ${size} · ${quantity === 1 ? '1 unidad' : `${String(quantity)} unidades`}`,
    subtotal: 'Subtotal',
    discountWith: (code: string) => `Descuento ${code}`,
    discount: 'Descuento',
    shipping: 'Envío',
    free: 'Gratis',
    total: 'Total',
    /** Where the order goes: the city and province only, e.g. "Envío a Santa Fe, Santa Fe". */
    shipTo: (city: string, province: string) => `Envío a ${city}, ${province}`,
    /** UC-17 (T-E3-23): the buyer corrects its email; the page never shows the one the order has. */
    emailCorrection: {
      open: '¿Escribiste mal tu email?',
      label: 'Tu email correcto',
      save: 'Guardar',
      saving: 'Guardando…',
      /** The email as the buyer just typed it. */
      resent: (email: string) => `Listo. Te reenviamos la confirmación a ${email}.`,
      savedForLater: (email: string) =>
        `Listo. Cuando se acredite el pago, te enviaremos la confirmación a ${email}.`,
      invalid: 'Revisá el email: no parece válido.',
      tooManyAttempts: 'Cambiaste el email muchas veces. Probá de nuevo en un rato.',
      error: 'No pudimos guardar el email. Probá de nuevo.',
    },
  },
  /** UC-29 (T-E3-25). The texts about the right are placeholders, to be validated with a professional. */
  withdrawal: {
    link: 'Botón de arrepentimiento',
    title: 'Botón de arrepentimiento',
    intro:
      'Podés revocar tu compra dentro de los 10 días corridos desde que la recibiste o desde que la hiciste, lo que haya pasado último. La devolución no tiene costo para vos. Completá el número de pedido y el email con el que compraste.',
    orderNumber: 'Número de pedido',
    orderNumberHint: 'Por ejemplo, 1001',
    email: 'Email de la compra',
    send: 'Enviar solicitud',
    sending: 'Enviando…',
    requested: 'Recibimos tu solicitud. Tu código de trámite es:',
    alreadyRequested: 'Ya habías hecho esta solicitud. Tu código de trámite es:',
    keepIt: 'Guardalo; también te lo enviamos por email.',
    noMatchingOrder: 'No encontramos un pedido con ese número y ese email. Revisá los datos.',
    notWithdrawable: 'Este pedido no tiene un pago para devolver.',
    tooManyAttempts: 'Hiciste muchos intentos. Probá de nuevo en un rato.',
    error: 'No pudimos enviar la solicitud. Probá de nuevo.',
  },
  account: {
    signInLink: 'Ingresar',
    greeting: (firstName: string) => `Hola, ${firstName}`,
    signOut: 'Salir',
    email: 'Email',
    password: 'Contraseña',
    tooManyAttempts: 'Hiciste muchos intentos. Probá de nuevo en un rato.',
    /** T-E7-23: the eye button of the password fields, and what an empty field says once the form is sent. */
    showPassword: 'Mostrar contraseña',
    missing: {
      name: 'Escribí tu nombre y apellido.',
      email: 'Escribí tu email.',
      password: 'Escribí tu contraseña.',
    },
    register: {
      title: 'Crear cuenta',
      intro: 'Con una cuenta guardás tus datos para la próxima compra. También podés comprar sin cuenta.',
      name: 'Nombre y apellido',
      passwordHint: 'Al menos 8 caracteres.',
      send: 'Crear cuenta',
      sending: 'Creando…',
      done: (email: string) =>
        `Te enviamos un email a ${email} con un enlace para verificar tu cuenta. El enlace vence en 24 horas. Si no lo ves, revisá la carpeta de spam.`,
      haveAccount: '¿Ya tenés cuenta?',
      emailTaken: 'Ya hay una cuenta con ese email.',
      emailTakenAction: 'Ingresá',
      invalidEmail: 'Revisá el email: no parece válido.',
      invalidName: 'Escribí tu nombre (hasta 100 caracteres).',
      passwordTooShort: 'La contraseña tiene que tener al menos 8 caracteres.',
      passwordTooLong: 'La contraseña es demasiado larga.',
      error: 'No pudimos crear la cuenta. Probá de nuevo.',
    },
    signIn: {
      title: 'Ingresar',
      send: 'Ingresar',
      sending: 'Ingresando…',
      intro: 'Ingresá para ver tus pedidos y comprar más rápido.',
      firstTime: '¿Primera vez acá?',
      verified: '¡Listo! Verificaste tu email. Ya podés ingresar.',
      invalidCredentials: 'El email o la contraseña no son correctos.',
      emailNotVerified:
        'Todavía no verificaste tu email. Te enviamos un enlace nuevo: abrilo desde tu correo y después ingresá.',
      locked: (time: string) => `Por seguridad bloqueamos el ingreso hasta las ${time}. Probá de nuevo después.`,
      error: 'No pudimos ingresar. Probá de nuevo.',
      forgotPassword: '¿Olvidaste tu contraseña?',
      passwordReset: '¡Listo! Cambiaste tu contraseña. Ya podés ingresar con la nueva.',
    },
    recover: {
      title: 'Recuperar contraseña',
      intro: 'Escribí el email de tu cuenta y te enviamos un enlace para elegir una contraseña nueva.',
      send: 'Enviar enlace',
      sending: 'Enviando…',
      done: (email: string) =>
        `Si hay una cuenta con ${email}, te enviamos un email con un enlace para elegir una contraseña nueva. El enlace vence en 30 minutos. Si no lo ves, revisá la carpeta de spam.`,
      backToSignIn: 'Volver a ingresar',
      remembered: '¿Te acordaste de tu contraseña?',
      error: 'No pudimos enviar el enlace. Probá de nuevo.',
    },
    reset: {
      title: 'Elegir contraseña nueva',
      intro: 'Elegí la contraseña con la que vas a ingresar de ahora en más.',
      newPassword: 'Contraseña nueva',
      passwordHint: 'Al menos 8 caracteres.',
      send: 'Guardar contraseña',
      sending: 'Guardando…',
      invalidLink: 'Este enlace ya se usó o venció. Pedí uno nuevo para elegir tu contraseña.',
      newLink: 'Pedir un enlace nuevo',
      passwordTooShort: 'La contraseña tiene que tener al menos 8 caracteres.',
      passwordTooLong: 'La contraseña es demasiado larga.',
      error: 'No pudimos guardar la contraseña. Probá de nuevo.',
    },
    addressesLink: 'Mis direcciones',
    ordersLink: 'Mis pedidos',
    myAccountLink: 'Mi cuenta',
    /** UC-31 (T-E7-02): the header's entries; with a session, "Mi cuenta" opens the list of the account links. */
    registerLink: 'Crear cuenta',
    menu: 'Mi cuenta',
    /** UC-27 (T-E4-15): how the customer signs in, linking and unlinking providers and creating a password. */
    myAccount: {
      title: 'Mi cuenta',
      methodsTitle: 'Formas de ingresar',
      methodsIntro: 'Podés ingresar con tu contraseña o con las cuentas que vincules.',
      loading: 'Cargando…',
      loadError: 'No pudimos cargar tus formas de ingresar.',
      retry: 'Reintentar',
      password: 'Contraseña',
      passwordSet: 'Ingresás con tu email y tu contraseña.',
      passwordMissing: 'Todavía no tenés contraseña.',
      createPassword: 'Crear contraseña',
      newPassword: 'Nueva contraseña',
      passwordHint: 'Al menos 8 caracteres.',
      savePassword: 'Guardar contraseña',
      savingPassword: 'Guardando…',
      cancel: 'Cancelar',
      passwordCreated: 'Listo: ya podés ingresar también con tu email y tu contraseña.',
      passwordAlreadySet: 'Tu cuenta ya tiene contraseña. Si no la recordás, podés restablecerla.',
      passwordTooShort: 'La contraseña tiene que tener al menos 8 caracteres.',
      passwordTooLong: 'La contraseña es demasiado larga.',
      passwordError: 'No pudimos guardar la contraseña. Probá de nuevo.',
      linkedAs: (email: string) => `Vinculada con ${email}`,
      notLinked: 'Sin vincular',
      link: 'Vincular',
      linkProvider: (provider: string) => `Vincular ${provider}`,
      unlink: 'Desvincular',
      unlinkProvider: (provider: string) => `Desvincular ${provider}`,
      confirmUnlink: (provider: string) =>
        `¿Desvincular ${provider}? Después no vas a poder ingresar con esa cuenta hasta que la vuelvas a vincular.`,
      confirmUnlinkYes: 'Sí, desvincular',
      unlinked: (provider: string) => `Desvinculaste ${provider}.`,
      notLinkedAnymore: 'Esa cuenta ya no estaba vinculada.',
      lastSignInMethod: (provider: string) =>
        `No podés desvincular ${provider}: es tu única forma de ingresar. Creá una contraseña o vinculá otra cuenta primero.`,
      unlinkError: 'No pudimos desvincular la cuenta. Probá de nuevo.',
      linkError: 'No pudimos empezar la vinculación. Probá de nuevo.',
      linkNotEnabled: 'Ese servicio no está disponible en este momento.',
      social: {
        linked: 'Listo: vinculaste la cuenta. Ya podés ingresar con ella.',
        alreadyLinked:
          'Esa cuenta ya está vinculada con otra cuenta de la tienda, o ya tenés otra cuenta de ese servicio vinculada.',
        cancelled: 'Cancelaste la vinculación. No cambió nada.',
        failed: 'No pudimos vincular la cuenta. Probá de nuevo.',
        reauthenticated: 'Confirmaste que sos vos. Ya podés eliminar tu cuenta.',
        reauthenticationCancelled: 'Cancelaste la confirmación. Tu cuenta sigue igual.',
        reauthenticationFailed:
          'No pudimos confirmar que sos vos. Ingresá con la misma cuenta que tenés vinculada y probá de nuevo.',
      },
      /** UC-28 (T-E4-16): downloading the customer's data and deleting its account. */
      dataTitle: 'Tus datos',
      dataIntro: 'Podés descargar una copia de tus datos o eliminar tu cuenta.',
      download: 'Descargar mis datos',
      downloading: 'Preparando…',
      downloadError: 'No pudimos preparar tus datos. Probá de nuevo.',
      deleteAccount: 'Eliminar mi cuenta',
      deleteTitle: '¿Eliminar tu cuenta?',
      deleteExplanation:
        'Vamos a borrar tu cuenta, tus direcciones, tus cuentas vinculadas y tu carrito, y vas a salir en todos tus dispositivos. No se puede deshacer.',
      deleteOrders:
        'Tus pedidos se conservan por las obligaciones legales de la tienda, pero ya no van a estar asociados a una cuenta.',
      deletePassword: 'Tu contraseña',
      deleteWithPassword: 'Eliminar mi cuenta',
      deleting: 'Eliminando…',
      orConfirmWith: 'O confirmá ingresando de nuevo con tu cuenta vinculada:',
      confirmWithIntro: 'Para confirmar, ingresá de nuevo con tu cuenta vinculada.',
      confirmWith: (provider: string) => `Confirmar con ${provider}`,
      deleteNow: 'Sí, eliminar mi cuenta',
      wrongPassword: 'La contraseña no es correcta.',
      reauthenticationRequired:
        'Pasaron más de 5 minutos desde que confirmaste. Volvé a confirmar para eliminar tu cuenta.',
      reauthenticationNotLinked: 'Esa cuenta ya no está vinculada.',
      reauthenticationError: 'No pudimos empezar la confirmación. Probá de nuevo.',
      deleteError: 'No pudimos eliminar tu cuenta. Probá de nuevo.',
      accountDeleted: 'Tu cuenta fue eliminada.',
    },
    /** UC-22 (T-E4-07): the customer's orders and the detail of each, with its tracking. */
    orders: {
      title: 'Mis pedidos',
      loading: 'Cargando tus pedidos…',
      loadError: 'No pudimos cargar tus pedidos.',
      retry: 'Reintentar',
      empty: 'Todavía no hiciste ningún pedido.',
      goShopping: 'Ir a la tienda',
      /** The order's date, already formatted, e.g. "Hecho el 13/10/26". */
      placedOn: (date: string) => `Hecho el ${date}`,
      more: 'Ver más',
      loadingMore: 'Cargando…',
      moreError: 'No pudimos cargar más pedidos. Probá de nuevo.',
      back: 'Volver a mis pedidos',
      loadingOrder: 'Cargando tu pedido…',
      loadOrderError: 'No pudimos cargar tu pedido.',
      notFoundTitle: 'No encontramos tu pedido',
      notFoundBody: 'No está entre los pedidos de tu cuenta.',
      items: 'Productos',
      address: 'Dirección de envío',
      shipment: 'Número de seguimiento del envío',
      noShipmentYet: 'Cuando despachemos tu pedido vas a ver acá el número de seguimiento.',
      copy: 'Copiar',
      copied: 'Copiado',
      trackAtCorreo: 'Seguir el envío en Correo Argentino',
      trackingHint: 'Copiá el número y pegalo en la página de Correo Argentino.',
    },
    /** UC-20 (T-E4-05): the address book. The fields use the checkout's labels and rules. */
    addresses: {
      title: 'Mis direcciones',
      intro: 'Guardá hasta 10 direcciones. La principal aparece elegida cuando comprás.',
      loading: 'Cargando tus direcciones…',
      loadError: 'No pudimos cargar tus direcciones.',
      retry: 'Reintentar',
      empty: 'Todavía no guardaste ninguna dirección.',
      primary: 'Principal',
      add: 'Agregar dirección',
      newTitle: 'Nueva dirección',
      editTitle: 'Editar dirección',
      edit: (line: string) => `Editar ${line}`,
      makePrimary: (line: string) => `Usar ${line} como principal`,
      remove: (line: string) => `Eliminar ${line}`,
      editShort: 'Editar',
      makePrimaryShort: 'Usar como principal',
      removeShort: 'Eliminar',
      confirmRemove: '¿Eliminar esta dirección? Tus pedidos conservan la dirección con la que se enviaron.',
      confirmRemoveYes: 'Sí, eliminar',
      cancel: 'Cancelar',
      primaryCheckbox: 'Usar como dirección principal',
      save: 'Guardar dirección',
      saving: 'Guardando…',
      full: 'Llegaste al máximo de 10 direcciones. Eliminá una para agregar otra.',
      saved: 'Guardamos la dirección.',
      removed: 'Eliminamos la dirección.',
      primaryChanged: 'Cambiamos tu dirección principal.',
      notFound: 'Esa dirección ya no existe. Actualizamos la lista.',
      error: 'No pudimos guardar la dirección. Probá de nuevo.',
      removeError: 'No pudimos eliminar la dirección. Probá de nuevo.',
    },
    /** UC-27 (T-E4-14): sign-in and registration with Google and Facebook. */
    social: {
      continueWith: (provider: string) => `Continuar con ${provider}`,
      or: 'o con tu email',
      confirmLink:
        'Ya hay una cuenta con ese email. Te enviamos un correo para confirmar que es tuyo: abrilo y después continuá de nuevo con el mismo botón.',
      cancelled: 'Cancelaste el ingreso. Podés probar de nuevo o ingresar con tu email.',
      alreadyLinked:
        'Tu cuenta ya está vinculada con otra cuenta de ese servicio. Ingresá con esa o con tu email y contraseña.',
      failed: 'No pudimos ingresar con ese servicio. Probá de nuevo o ingresá con tu email.',
    },
    completeEmail: {
      title: 'Completá tu email',
      intro: 'Tu cuenta de Facebook no nos dio un email. Escribilo y te enviamos un enlace para confirmar que es tuyo.',
      send: 'Enviar enlace',
      sending: 'Enviando…',
      done: (email: string) =>
        `Te enviamos un email a ${email} con los pasos a seguir. Si no lo ves, revisá la carpeta de spam.`,
      invalidEmail: 'Revisá el email: no parece válido.',
      expired: 'Pasó demasiado tiempo. Volvé a continuar con Facebook para empezar de nuevo.',
      backToSignIn: 'Volver a ingresar',
      error: 'No pudimos enviar el enlace. Probá de nuevo.',
    },
    link: {
      title: 'Vincular cuenta',
      intro: 'Vas a conectar una cuenta de Facebook a tu cuenta de Pijama Party. Después vas a poder ingresar con ella.',
      notYou:
        'Si no pediste esta vinculación, no continúes: cerrá esta página y tu cuenta sigue igual. Quien tenga esa cuenta de Facebook podría ingresar a la tuya.',
      confirm: 'Vincular mi cuenta de Facebook',
      linking: 'Vinculando tu cuenta…',
      linked: '¡Listo! Vinculamos tu cuenta. Ya podés ingresar con el mismo botón que usaste.',
      invalidLink: 'Este enlace ya se usó o venció. Continuá de nuevo con el mismo botón y te enviamos uno nuevo.',
      alreadyLinked:
        'No pudimos vincularla: esa cuenta ya está vinculada con otra, o la tuya ya tiene otra cuenta de ese servicio.',
      error: 'No pudimos vincular tu cuenta. Probá abrir el enlace de nuevo.',
      signIn: 'Ir a ingresar',
    },
    verify: {
      title: 'Verificar email',
      verifying: 'Verificando tu email…',
      invalidLink:
        'Este enlace ya se usó o venció. Si todavía no verificaste tu email, ingresá con tu email y tu contraseña y te enviamos uno nuevo.',
      error: 'No pudimos verificar tu email. Probá abrir el enlace de nuevo.',
      signIn: 'Ir a ingresar',
    },
  },
  /** UC-31 (T-E7-28): "Cómo comprar", the steps of the real flow. Payment methods are not named: the server has them. */
  howToBuy: {
    title: 'Cómo comprar',
    intro: 'Comprar en Pijama Party es fácil y no necesitás crear una cuenta.',
    steps: [
      {
        title: 'Elegí tus pijamas',
        text: 'Recorré las categorías o usá el buscador. En cada producto elegí el talle y el color; si dudás con el talle, abrí la guía de talles de la ficha cuando el producto la tenga.',
      },
      {
        title: 'Revisá tu carrito',
        text: 'Desde el carrito podés cambiar las cantidades, quitar productos y aplicar un cupón de descuento si tenés uno.',
      },
      {
        title: 'Completá tus datos',
        text: 'Finalizá la compra en 3 pasos: tus datos y la dirección de entrega, donde ves el costo de envío a tu provincia; el medio de pago; y la revisión de todo antes de confirmar.',
      },
      {
        title: 'Pagá',
        text: 'Te llevamos al medio de pago que elegiste para que pagues de forma segura. Mientras tanto, reservamos tus productos.',
      },
      {
        title: 'Seguí tu pedido',
        text: 'Cuando se acredita el pago, te llega un email con la confirmación y el enlace de tu pedido. Ahí vas a ver el número de seguimiento de Correo Argentino cuando lo despachemos.',
      },
    ],
    more: '¿Te quedó alguna duda? Mirá las',
    moreLink: 'preguntas frecuentes',
  },
  /** UC-31 (T-E7-28): the exchanges policy (D-18, draft until T-E6-21) and the withdrawal right (UC-29). */
  returnsPolicy: {
    title: 'Cambios y devoluciones',
    exchangesTitle: 'Cambios',
    exchanges: [
      'Podés cambiar tu compra por otro talle o color dentro de los 30 días corridos desde que la recibiste, según el stock disponible.',
      'La prenda tiene que estar sin uso, sin lavar y con sus etiquetas.',
      'El envío de ida y vuelta corre por tu cuenta. Si la prenda tiene una falla de fábrica, nos hacemos cargo del envío.',
    ],
    howTo:
      'Para pedir un cambio, escribinos con tu número de pedido; los datos de contacto están al pie de la página. Te indicamos cómo enviarnos la prenda.',
    withdrawalTitle: 'Arrepentimiento de compra',
    withdrawal:
      'Además, tenés 10 días corridos desde que recibiste tu compra para arrepentirte, sin costo. Te devolvemos el dinero por el mismo medio de pago.',
    withdrawalLink: 'Pedilo desde el',
  },
  /**
   * UC-31 (T-E7-28): the terms of purchase (RNF-30.6), a draft until T-E6-21 validates it; store.ordering.terms-version
   * changes whenever these texts change. The seller's identification is in the footer (RNF-30.2).
   */
  terms: {
    title: 'Términos y condiciones',
    sections: {
      acceptance: {
        title: 'Aceptación',
        text: 'Estos términos rigen las compras en la tienda online de Pijama Party Santa Fe. Al confirmar una compra los aceptás, y guardamos con tu pedido la versión vigente en ese momento.',
      },
      products: {
        title: 'Productos y precios',
        text: 'Los precios están en pesos argentinos y son finales, con impuestos incluidos. Respetamos el precio que confirmaste al pagar. Las fotos son ilustrativas: los colores pueden verse un poco distintos según tu pantalla.',
      },
      purchase: {
        title: 'Compra y pago',
        text: 'Pagás con los medios de pago que ofrecemos al finalizar la compra. Mientras completás el pago, reservamos tus productos durante 30 minutos. El pedido se confirma cuando el pago se acredita. Si el pago falla, podés reintentarlo desde el enlace de tu pedido mientras dure la reserva; después, el pedido vence y los productos vuelven a estar disponibles.',
      },
      shipping: {
        title: 'Envíos',
        text: 'Enviamos por Correo Argentino a las provincias que figuran en las preguntas frecuentes, y ves el costo de envío antes de pagar. A Santa Fe llega en 1 a 3 días hábiles y al resto del país en 3 a 7, contados desde que se acredita el pago.',
      },
      data: {
        title: 'Datos personales',
        text: 'Tratamos tus datos personales según la Ley 25.326 de Protección de los Datos Personales. Si tenés cuenta, podés eliminarla desde Mi cuenta.',
      },
      law: {
        title: 'Ley aplicable',
        text: 'Estos términos se rigen por las leyes de la República Argentina, incluida la Ley 24.240 de Defensa del Consumidor. Ante un reclamo, podés acudir a los tribunales de tu domicilio.',
      },
      contact: {
        title: 'Contacto',
        text: 'Para cualquier consulta, escribinos: los datos de contacto están al pie de la página.',
      },
    },
    exchangesTitle: 'Cambios y arrepentimiento',
    exchangesBefore: 'Podés cambiar tu compra según la',
    policyLink: 'política de cambios y devoluciones',
    exchangesBetween: 'y arrepentirte dentro de los 10 días corridos desde que la recibiste con el',
  },
  /** UC-31 (T-E7-29, RNF-31.5): the size guide page, with the guides of the back office. */
  sizeGuides: {
    title: 'Guía de talles',
    intro: 'Las medidas de cada categoría, las mismas que ves en la ficha de sus productos.',
    /** The name of each category's table. */
    tableOf: (category: string) => `Tabla de talles de ${category}`,
    none: 'Todavía no hay guías de talles. Si tenés dudas con un talle, mirá las',
    faq: 'preguntas frecuentes',
    loading: 'Cargando las guías de talles…',
    loadError: 'No pudimos cargar las guías de talles.',
    retry: 'Reintentar',
  },
  /** RNF-31.6 (T-E5-12): the FAQ page and its summary on the product page. */
  faq: {
    title: 'Preguntas frecuentes',
    retry: 'Reintentar',
    shipping: {
      question: '¿Cuánto cuesta el envío?',
      intro: 'El costo depende de la provincia de entrega:',
      table: 'Costos de envío por provincia',
      province: 'Provincia',
      cost: 'Costo',
      free: 'Gratis',
      notListed: 'Por ahora no hacemos envíos a las provincias que no figuran en la tabla.',
      /** The free-shipping threshold of UC-12, compared with the subtotal after the discount. */
      freeFrom: (amount: string) => `El envío es gratis en compras desde ${amount}, con el descuento aplicado.`,
      none: 'Por ahora no estamos haciendo envíos.',
      loading: 'Cargando los costos de envío…',
      loadError: 'No pudimos cargar los costos de envío.',
    },
    delivery: {
      question: '¿Cuánto tarda en llegar mi pedido?',
      answer:
        'A Santa Fe llega en 1 a 3 días hábiles y al resto del país en 3 a 7 días hábiles, contados desde que se acredita el pago.',
      tracking: 'Cuando despachamos tu pedido, el número de seguimiento aparece en el enlace de tu pedido.',
    },
    payment: {
      question: '¿Cómo puedo pagar?',
      intro: 'Elegís el medio de pago al finalizar la compra:',
      loading: 'Cargando los medios de pago…',
      loadError: 'No pudimos cargar los medios de pago.',
    },
    returns: {
      question: '¿Puedo cambiar o devolver un producto?',
      withdrawal:
        'Tenés 10 días corridos desde que recibiste tu compra para arrepentirte, sin costo para vos. Pedilo desde el',
      policy: 'Conocé todas las condiciones en la',
      policyLink: 'política de cambios y devoluciones',
    },
    /** Below the add to cart of the product page. */
    summary: {
      label: 'Envíos y pagos',
      freeFrom: (amount: string) => `Envío gratis en compras desde ${amount}`,
      delivery: 'Llega en 1 a 3 días hábiles a Santa Fe y en 3 a 7 al resto del país',
      /** The payment methods as one phrase, e.g. "Pagá con MercadoPago o MODO". */
      payWith: (methods: string) => `Pagá con ${methods}`,
      link: 'Envíos, pagos y cambios',
    },
  },
  notFound: {
    title: 'No encontramos esta página',
    body: 'Puede que el enlace esté mal escrito o que la página ya no exista.',
    backToStore: 'Volver a la tienda',
  },
} as const;

export type Messages = typeof es;
