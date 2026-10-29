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
    showMore: 'Ver más',
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
    pickSizeAndColor: 'Elegí talle y color para agregarlo al carrito.',
    addToCart: 'Agregar al carrito',
    adding: 'Agregando…',
    /** E7 motion: the add button confirms a successful add for a moment. */
    added: 'Agregado',
    /** UC-08: an add above the available stock, e.g. "Solo quedan 3". */
    onlyLeft: (count: number) =>
      count === 0 ? 'Sin stock' : count === 1 ? 'Solo queda 1' : `Solo quedan ${String(count)}`,
    noLongerAvailable: 'Este producto ya no está disponible.',
    addError: 'No pudimos agregar el producto. Probá de nuevo.',
    /** RNF-31.5: the collapsed size guide of the category, among the sections of the page (E7). */
    sizeGuide: 'Guía de talles',
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
  /** UC-31 (T-E7-02): the header, its navigation, the phone menu and the free-shipping strip. */
  header: {
    /** The navigation's name; the phone menu shows the prototype's title, "Categorías". */
    navigation: 'Navegación principal',
    menuTitle: 'Categorías',
    all: 'Todo',
    audiences: { WOMEN: 'Mujer', MEN: 'Hombre', KIDS: 'Niños' },
    winter: 'Temporada Invierno',
    openMenu: 'Abrir menú',
    closeMenu: 'Cerrar menú',
    account: 'Tu cuenta',
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
    /** A network the business has not sent yet (D-15): its placeholder in the footer. */
    socialSoon: (network: string) => `${network}, próximamente`,
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
  admin: {
    title: 'Backoffice',
    nav: 'Secciones del backoffice',
    orders: 'Pedidos',
    withdrawals: 'Arrepentimientos',
    products: 'Productos',
    categories: 'Categorías',
    coupons: 'Cupones',
    reviews: 'Reseñas',
    shipping: 'Envíos',
    home: 'Portada',
    activity: 'Actividad',
    viewStore: 'Ver tienda',
    signOut: 'Cerrar sesión',
    loading: 'Cargando…',
    loadError: 'No pudimos cargar los datos.',
    actionError: 'No pudimos guardar el cambio. Revisá la conexión y probá de nuevo.',
    retry: 'Reintentar',
    showMore: 'Ver más',
    save: 'Guardar',
    cancel: 'Cancelar',
    saved: 'Cambios guardados.',
    accessDenied: {
      title: 'Acceso denegado',
      body: 'Esta sección es solo para la administración de la tienda.',
    },
    signIn: {
      title: 'Ingresar al backoffice',
      email: 'Email',
      password: 'Contraseña',
      submit: 'Ingresar',
      submitting: 'Ingresando…',
      invalidCredentials: 'El email o la contraseña no son correctos.',
      /** The unlock time of an account locked after 5 failures (UC-04), e.g. "14:30". */
      locked: (time: string) => `La cuenta está bloqueada por seguridad hasta las ${time}.`,
      tooManyAttempts: 'Hubo demasiados intentos. Esperá un momento y probá de nuevo.',
      expired: 'Tu sesión venció. Ingresá de nuevo para seguir.',
      failed: 'No pudimos ingresar. Probá de nuevo en unos segundos.',
      codeTitle: 'Verificación en dos pasos',
      codeLabel: 'Código de verificación',
      codeHint:
        'Abrí la app de autenticación y escribí los 6 números. Si no tenés el teléfono, usá uno de tus códigos de recuperación.',
      verify: 'Verificar',
      verifying: 'Verificando…',
      invalidCode: 'El código no es correcto o ya se usó.',
      expiredStep: 'Pasaron más de 5 minutos. Ingresá la contraseña de nuevo.',
      enrollTitle: 'Activá la verificación en dos pasos',
      enrollIntro:
        'Para proteger los datos de la tienda, el backoffice pide además un código de una app de autenticación (Google Authenticator, Microsoft Authenticator u otra). Escaneá el código QR con la app o escribí la clave a mano.',
      loadingEnrollment: 'Preparando…',
      qrAlt: 'Código QR para la app de autenticación',
      secretLabel: 'Clave para escribir a mano',
      enrollCodeLabel: 'Código que muestra la app',
      enroll: 'Activar',
      enrolling: 'Activando…',
      recoveryTitle: 'Guardá tus códigos de recuperación',
      recoveryIntro:
        'Si perdés el teléfono, cada uno de estos códigos te deja entrar una sola vez. Es la única vez que se muestran: guardalos en un lugar seguro.',
      download: 'Descargar los códigos',
      recoveryFileName: 'pijama-party-codigos-de-recuperacion.txt',
      saved: 'Guardé los códigos en un lugar seguro',
      continue: 'Entrar al backoffice',
    },
    categoryList: {
      title: 'Categorías',
      newName: 'Nueva categoría',
      create: 'Crear',
      empty: 'Todavía no hay categorías.',
      inactive: 'Inactiva',
      rename: 'Renombrar',
      /** The field to rename a category, e.g. "Nuevo nombre de Pijamas". */
      renameLabel: (name: string) => `Nuevo nombre de ${name}`,
      moveUp: (name: string) => `Subir ${name}`,
      moveDown: (name: string) => `Bajar ${name}`,
      deactivate: 'Desactivar',
      activate: 'Activar',
      delete: 'Eliminar',
      confirmDelete: (name: string) => `¿Eliminar la categoría ${name}? No se puede deshacer.`,
      slugUsed: (slug: string) => `Ya hay una categoría con ese nombre (dirección /categoria/${slug}).`,
      invalidName: 'El nombre tiene que tener letras o números y hasta 80 caracteres.',
      hasProducts: 'La categoría tiene productos, así que no se puede eliminar. Podés desactivarla.',
      notFound: 'La categoría ya no existe.',
      /** RNF-31.5: opens the category's size guide. */
      sizeGuide: 'Guía de talles',
      sizeGuideOf: (name: string) => `Guía de talles de ${name}`,
    },
    sizeGuidePage: {
      /** RNF-31.5 (T-E5-11): the size guide of one category. */
      title: 'Guía de talles',
      /** E.g. "Guía de talles de Pijamas de mujer". */
      heading: (category: string) => `Guía de talles de ${category}`,
      back: 'Volver a categorías',
      hint: 'Nombrá las columnas (por ejemplo «Busto (cm)» o «Edad») y cargá una fila por talle. Se muestra en la ficha de cada producto de la categoría.',
      noGuide: 'Esta categoría todavía no tiene guía de talles.',
      columnsTitle: 'Columnas',
      columnLabel: (position: number) => `Columna ${String(position)}`,
      addColumn: 'Agregar columna',
      /** E.g. "Quitar la columna Busto (cm)". */
      removeColumn: (name: string) => `Quitar la columna ${name}`,
      rowsTitle: 'Talles',
      rowTitle: (position: number) => `Fila ${String(position)}`,
      sizeLabel: 'Talle',
      addRow: 'Agregar talle',
      removeRow: (position: number) => `Quitar la fila ${String(position)}`,
      moveUp: (position: number) => `Subir la fila ${String(position)}`,
      moveDown: (position: number) => `Bajar la fila ${String(position)}`,
      remove: 'Quitar',
      note: 'Nota (opcional)',
      noteHint: 'Por ejemplo, cómo tomarse las medidas.',
      delete: 'Eliminar guía',
      confirmDelete: '¿Eliminar la guía de talles? Los productos de la categoría dejan de mostrarla.',
      deleted: 'Guía eliminada.',
      invalid: 'Revisá lo marcado.',
      invalidColumns: 'Entre 1 y 6 columnas con nombres distintos, de hasta 30 caracteres.',
      invalidRows: 'Entre 1 y 20 talles distintos, de hasta 20 caracteres, con un valor de hasta 20 caracteres en cada columna.',
      invalidNote: 'La nota puede tener hasta 500 caracteres.',
      notFound: 'La categoría ya no existe.',
    },
    homeBanner: {
      /** UC-32 (T-E7-13): the character banner of the home page. */
      pageTitle: 'Portada',
      intro: 'El banner de personajes se muestra en la portada de la tienda mientras está publicado.',
      published: 'Publicado',
      hiddenState: 'Oculto',
      title: 'Título',
      body: 'Texto (opcional)',
      linkText: 'Texto del enlace',
      linkPath: 'Destino del enlace',
      linkPathHint: 'Una página de la tienda, empezando con /. Por ejemplo /categoria/kigurumis o /?publico=infantil.',
      invalid: 'Revisá lo marcado.',
      invalidFields: {
        title: 'El título va de 1 a 60 caracteres.',
        body: 'El texto puede tener hasta 200 caracteres.',
        linkText: 'El texto del enlace va de 1 a 30 caracteres.',
        linkPath: 'Tiene que ser una página de esta tienda: empieza con una sola / y tiene hasta 200 caracteres.',
      },
      photoTitle: 'Foto',
      noPhoto: 'Todavía no hay foto.',
      addPhoto: 'Subir foto (JPG, PNG o WebP)',
      replacePhoto: 'Cambiar foto (JPG, PNG o WebP)',
      photoHint: 'Se achica en el teléfono antes de subirse y reemplaza a la foto actual.',
      previewTitle: 'Vista previa',
      previewNoTitle: 'Escribí un título',
      previewNoLink: 'Texto del enlace',
      publish: 'Publicar',
      hide: 'Ocultar',
      publishHint: 'Se publica lo guardado: guardá los cambios antes de publicar.',
      publishedNotice: 'El banner está publicado.',
      hidden: 'El banner está oculto.',
      parts: {
        photo: 'la foto',
        title: 'el título',
        linkText: 'el texto del enlace',
        linkPath: 'el destino del enlace',
      },
      /** E.g. "Para publicar falta la foto y el título." */
      incomplete: (missing: string[]) =>
        `Para publicar falta ${
          missing.length <= 1 ? (missing[0] ?? '') : `${missing.slice(0, -1).join(', ')} y ${missing.at(-1) ?? ''}`
        }.`,
    },
    couponList: {
      title: 'Cupones',
      newCoupon: 'Nuevo cupón',
      create: 'Crear cupón',
      empty: 'Todavía no hay cupones.',
      inactive: 'Inactivo',
      /** UC-23: a coupon that applies on its own. */
      automatic: 'Automático',
      /** What an automatic coupon discounts, e.g. "Alcance: Pijamas, Camisón Luna". */
      scope: (targets: string) => `Alcance: ${targets}`,
      wholeCart: 'Alcance: todo el carrito',
      /** A scope target the catalog no longer has. */
      removed: 'Eliminado',
      /** The scope while the catalog names cannot be read, e.g. "Alcance: 3 categorías y productos". */
      scopeCount: (count: number) => `Alcance: ${String(count)} categorías y productos`,
      /** E.g. "10 % de descuento". */
      percentOff: (percent: number) => `${String(percent)} % de descuento`,
      /** E.g. "$ 2.000 de descuento". */
      amountOff: (amount: string) => `${amount} de descuento`,
      validBetween: (from: string, until: string) => `Vale del ${from} al ${until}`,
      validFrom: (from: string) => `Vale desde el ${from}`,
      validUntil: (until: string) => `Vale hasta el ${until}`,
      noExpiry: 'Sin vencimiento',
      minimum: (amount: string) => `Compra mínima: ${amount}`,
      /** The confirmed orders that used it, e.g. "Usos: 3 de 10", or "Usos: 3" without a maximum. */
      uses: (uses: number, maxUses: number | null) =>
        maxUses === null ? `Usos: ${String(uses)}` : `Usos: ${String(uses)} de ${String(maxUses)}`,
      edit: 'Editar',
      /** The title of a coupon being edited, e.g. "Editar VERANO10". */
      editing: (code: string) => `Editar ${code}`,
      deactivate: 'Desactivar',
      activate: 'Activar',
      delete: 'Eliminar',
      confirmDelete: (code: string) => `¿Eliminar el cupón ${code}? No se puede deshacer.`,
      hasUses: 'El cupón ya se usó, así que no se puede eliminar. Podés desactivarlo.',
      notFound: 'El cupón ya no existe.',
    },
    couponForm: {
      code: 'Código',
      codeHint: 'De 3 a 20 letras, números o guiones. Se guarda en mayúsculas.',
      type: 'Tipo',
      types: { PERCENTAGE: 'Porcentaje', FIXED_AMOUNT: 'Monto fijo' },
      percent: 'Porcentaje de descuento (%)',
      amount: 'Monto de descuento ($)',
      validFrom: 'Vale desde',
      validUntil: 'Vale hasta',
      optional: 'Opcional.',
      maxUses: 'Usos máximos',
      maxUsesHint: 'Opcional. Vacío: sin límite.',
      minimum: 'Compra mínima ($)',
      minimumHint: 'Opcional. Vacío: sin mínimo.',
      /** UC-23, only when creating: it cannot change later. */
      automatic: 'Automático',
      automaticHint:
        'Se aplica solo cuando el carrito cumple las condiciones, sin escribir el código. El código es el nombre del descuento en el carrito. No se puede cambiar después.',
      scope: 'Alcance',
      scopeHint: 'Opcional. Sin elegir nada, descuenta todo el carrito. Hasta 100 categorías y productos en total.',
      /** How many targets are picked, e.g. "3 de 100 elegidos". */
      scopeCount: (count: number) => `${String(count)} de 100 elegidos`,
      categories: 'Categorías',
      products: 'Productos',
      productFilter: 'Buscar producto',
      noProducts: 'Ningún producto coincide.',
      /** A picked target the catalog no longer has; unticking it takes it out of the scope. */
      removed: 'Eliminado',
      scopeLoadError: 'No pudimos cargar las categorías y los productos.',
      codeUsed: (code: string) => `Ya hay un cupón con el código ${code}.`,
      invalid: 'Revisá los campos marcados.',
      errors: {
        CODE: 'De 3 a 20 letras, números o guiones, sin espacios en el medio.',
        DISCOUNT_VALUE: 'Un porcentaje entero de 1 a 100, o un monto de $ 0,01 a $ 10.000.000.',
        VALIDITY: 'La fecha de inicio no puede ser posterior a la de fin.',
        MAX_USES: 'Un número entero de 1 a 1.000.000.',
        MINIMUM_PURCHASE: 'Un monto de $ 0,01 a $ 10.000.000.',
        SCOPE: 'Solo un cupón automático tiene alcance, de hasta 100 categorías y productos.',
      },
    },
    shippingPage: {
      title: 'Envíos',
      zonesTitle: 'Costo de envío por provincia',
      zonesHint: 'En pesos. Vacío: no hay envío a esa provincia. $ 0: envío gratis a esa provincia.',
      saveZones: 'Guardar costos',
      invalidCost: 'Un monto de $ 0 a $ 10.000.000.',
      invalidZones: 'Revisá los costos marcados.',
      freeShippingTitle: 'Envío gratis',
      threshold: 'Envío gratis desde ($)',
      thresholdHint: 'Se compara con el subtotal con descuento. Vacío: no hay envío gratis.',
      saveThreshold: 'Guardar envío gratis',
      invalidThreshold: 'Un monto de $ 0,01 a $ 10.000.000.',
    },
    orderList: {
      title: 'Pedidos',
      filter: 'Estado',
      all: 'Todos',
      empty: 'No hay pedidos.',
      emptyInStatus: 'No hay pedidos en este estado.',
      /** The order number, e.g. "#1001". */
      number: (number: number) => `#${String(number)}`,
      /** Who and where, e.g. "Ana Pérez · Santa Fe, Santa Fe". */
      buyer: (name: string, city: string, province: string) => `${name} · ${city}, ${province}`,
    },
    withdrawalList: {
      title: 'Arrepentimientos',
      filter: 'Estado',
      all: 'Todas',
      statuses: { RECEIVED: 'Pendiente', RESOLVED: 'Resuelta' },
      empty: 'No hay solicitudes de arrepentimiento.',
      emptyInStatus: 'No hay solicitudes en este estado.',
      /** The order it is for, e.g. "Pedido #1001". */
      order: (number: number) => `Pedido #${String(number)}`,
      outOfTerm: 'Fuera de término',
      /** Who and when, e.g. "Ana Pérez · pedida el 13/10/26, 15:00". */
      requested: (buyerName: string, dateTime: string) => `${buyerName} · pedida el ${dateTime}`,
      resolvedAt: (dateTime: string) => `Resuelta el ${dateTime}`,
      resolve: 'Marcar resuelta',
      resolving: 'Guardando…',
      resolved: (code: string) => `Solicitud ${code} resuelta.`,
      alreadyResolved: 'Esa solicitud ya estaba resuelta. Actualizamos la lista.',
      notFound: 'No encontramos esa solicitud. Actualizamos la lista.',
    },
    /** UC-37 (T-E8-01): the changes made from the back office. */
    activityList: {
      title: 'Actividad',
      area: 'Sección',
      all: 'Todas',
      from: 'Desde',
      to: 'Hasta',
      invalidPeriod: 'La fecha "Desde" no puede ser posterior a "Hasta".',
      empty: 'Todavía no hay cambios registrados.',
      emptyFiltered: 'No hay cambios con estos filtros.',
      areas: {
        CATALOG: 'Catálogo',
        STOCK: 'Stock',
        ORDERS: 'Pedidos',
        PROMOTIONS: 'Cupones',
        REVIEWS: 'Reseñas',
        SHIPPING: 'Envíos',
        CONTENT: 'Portada',
        SETTINGS: 'Datos de la tienda',
        CUSTOMERS: 'Clientes',
        ACCOUNT: 'Ingresos',
      },
      actions: {
        CATEGORY_CREATED: 'Categoría creada',
        CATEGORY_RENAMED: 'Categoría renombrada',
        CATEGORIES_REORDERED: 'Categorías reordenadas',
        CATEGORY_DEACTIVATED: 'Categoría desactivada',
        CATEGORY_ACTIVATED: 'Categoría activada',
        CATEGORY_DELETED: 'Categoría eliminada',
        PRODUCT_CREATED: 'Producto creado',
        PRODUCT_UPDATED: 'Producto editado',
        PRODUCT_PUBLISHED: 'Producto publicado',
        PRODUCT_HIDDEN: 'Producto ocultado',
        VARIANTS_GENERATED: 'Variantes generadas',
        VARIANT_UPDATED: 'Variante editada',
        PRODUCT_IMAGE_UPLOADED: 'Foto subida',
        PRODUCT_IMAGES_REORDERED: 'Fotos reordenadas',
        PRODUCT_IMAGE_DELETED: 'Foto eliminada',
        SIZE_GUIDE_SAVED: 'Guía de talles guardada',
        SIZE_GUIDE_DELETED: 'Guía de talles eliminada',
        STOCK_ADJUSTED: 'Stock ajustado',
        ORDER_STATUS_CHANGED: 'Estado del pedido cambiado',
        TRACKING_NUMBER_CORRECTED: 'Número de seguimiento corregido',
        WITHDRAWAL_RESOLVED: 'Arrepentimiento resuelto',
        COUPON_CREATED: 'Cupón creado',
        COUPON_UPDATED: 'Cupón editado',
        COUPON_ACTIVATED: 'Cupón activado',
        COUPON_DEACTIVATED: 'Cupón desactivado',
        COUPON_DELETED: 'Cupón eliminado',
        REVIEW_APPROVED: 'Reseña aprobada',
        REVIEW_REJECTED: 'Reseña rechazada',
        REVIEW_DELETED: 'Reseña eliminada',
        SHIPPING_ZONES_CHANGED: 'Costos de envío cambiados',
        FREE_SHIPPING_THRESHOLD_CHANGED: 'Envío gratis cambiado',
        HOME_BANNER_UPDATED: 'Banner de la portada editado',
        HOME_BANNER_PUBLISHED: 'Banner de la portada publicado',
        HOME_BANNER_HIDDEN: 'Banner de la portada ocultado',
        HOME_BANNER_PHOTO_REPLACED: 'Foto del banner cambiada',
        STORE_SETTINGS_CHANGED: 'Datos de la tienda cambiados',
        CUSTOMER_VIEWED: 'Ficha de cliente consultada',
        ADMIN_SIGNED_IN: 'Ingreso al backoffice',
        SECOND_FACTOR_ENROLLED: 'Segundo factor configurado',
        RECOVERY_CODE_USED: 'Código de recuperación usado',
      },
      /** What an entry without a label of its own is about, so it still links to its section. */
      subjects: {
        CATEGORY: 'Categorías',
        HOME_BANNER: 'Banner de la portada',
        REVIEW: 'Reseñas',
        SHIPPING: 'Envíos',
      },
      /** The names of the changed fields; a key without a name here shows as it comes. */
      fields: {
        name: 'Nombre',
        order: 'Orden',
        description: 'Descripción',
        category: 'Categoría',
        material: 'Material',
        season: 'Temporada',
        audience: 'Público',
        variants: 'Variantes',
        sizes: 'Talles',
        colors: 'Colores',
        price: 'Precio',
        active: 'Activa',
        title: 'Título',
        body: 'Texto',
        linkText: 'Texto del enlace',
        linkPath: 'Destino del enlace',
        quantity: 'Cantidad',
        stock: 'Stock',
        status: 'Estado',
        trackingNumber: 'Número de seguimiento',
        restock: 'Stock devuelto',
        code: 'Código',
        type: 'Tipo',
        value: 'Descuento',
        validFrom: 'Vale desde',
        validUntil: 'Vale hasta',
        maxUses: 'Usos máximos',
        minimumPurchase: 'Compra mínima',
        scopeProducts: 'Productos del cupón',
        scopeCategories: 'Categorías del cupón',
        cost: 'Costo de envío',
        threshold: 'Envío gratis desde',
      } as Readonly<Partial<Record<string, string>>>,
      /**
       * The values the server records as names (a yes/no, a choice); any other value shows as it comes. The order
       * statuses and coupon types come from their own screens' words.
       */
      values: {
        active: { true: 'Sí', false: 'No' },
        restock: { true: 'Sí', false: 'No' },
        season: { SUMMER: 'Verano', MID_SEASON: 'Media estación', WINTER: 'Invierno' },
        audience: { WOMEN: 'Mujer', MEN: 'Hombre', KIDS: 'Infantil' },
      } as Readonly<Partial<Record<string, Readonly<Partial<Record<string, string>>>>>>,
      /** A value the field did not have before, or no longer has. */
      noValue: '—',
      /** One changed field, e.g. "Precio: $20.000 → $22.000". */
      change: (field: string, before: string, after: string) => `${field}: ${before} → ${after}`,
    },
    /** UC-25 (T-E5-08): review moderation. */
    reviewList: {
      title: 'Reseñas',
      tabs: 'Estado de las reseñas',
      statuses: { PENDING: 'Pendientes', APPROVED: 'Aprobadas', REJECTED: 'Rechazadas' },
      empty: {
        PENDING: 'No hay reseñas pendientes.',
        APPROVED: 'No hay reseñas aprobadas.',
        REJECTED: 'No hay reseñas rechazadas.',
      },
      productGone: 'Producto eliminado',
      /** The accessible name of a rating, e.g. "4 de 5 estrellas". */
      rating: (count: number) => `${String(count)} de 5 estrellas`,
      /** Who and when it was last submitted, e.g. "Ana · enviada el 13/10/26, 15:00". */
      submitted: (authorName: string, dateTime: string) => `${authorName} · enviada el ${dateTime}`,
      noText: 'Sin comentario.',
      approve: 'Aprobar',
      reject: 'Rechazar',
      delete: 'Eliminar',
      confirmDelete: (authorName: string) => `¿Eliminar la reseña de ${authorName}? No se puede deshacer.`,
      approved: (authorName: string) => `Reseña de ${authorName} aprobada.`,
      rejected: (authorName: string) => `Reseña de ${authorName} rechazada.`,
      deleted: (authorName: string) => `Reseña de ${authorName} eliminada.`,
      notFound: 'No encontramos esa reseña. Actualizamos la lista.',
    },
    orderDetail: {
      backToList: 'Volver a pedidos',
      notFound: 'No encontramos este pedido.',
      /** The order number, e.g. "Pedido #1001". */
      title: (number: number) => `Pedido #${String(number)}`,
      /** When it was placed, already formatted, e.g. "Hecho el 13/10/26, 15:00". */
      placedAt: (dateTime: string) => `Hecho el ${dateTime}`,
      deliveredAt: (dateTime: string) => `Entregado el ${dateTime}`,
      alerts: {
        /** The procedure code, and whether it came after the 10-day term. */
        WITHDRAWAL_REQUESTED: (code: string, outOfTerm: boolean) =>
          `El comprador pidió arrepentirse de la compra (trámite ${code}${outOfTerm ? ', fuera de término' : ''}). No lo envíes: coordiná la devolución.`,
        PAID_AFTER_CLOSING:
          'El pago se aprobó después de que el pedido venció o se canceló. Hacé el reembolso desde el panel de MercadoPago.',
        /** The buyer's phone, to call about the email that did not arrive. */
        BUYER_EMAIL_FAILED: (phone: string) =>
          `No pudimos enviarle el correo de confirmación al comprador. Llamalo o escribile al ${phone}.`,
        /** The buyer's phone, to confirm the address the email bounced from. */
        BUYER_EMAIL_BOUNCED: (phone: string) =>
          `El correo de confirmación rebotó: el email del comprador no existe o no recibe correos. Llamalo o escribile al ${phone} para confirmarlo.`,
      },
      buyer: 'Comprador',
      nationalId: (id: string) => `DNI ${id}`,
      address: 'Dirección de envío',
      /** Floor and apartment, e.g. "Piso 3, depto. B". */
      floorAndApartment: (floor: string | null, apartment: string | null) =>
        [floor === null ? null : `Piso ${floor}`, apartment === null ? null : `depto. ${apartment}`]
          .filter((part) => part !== null)
          .join(', '),
      items: 'Productos',
      /** A line's color, size, quantity and unit price, e.g. "Rosa · Talle M · 2 × $ 20.000". */
      lineDetail: (color: string, size: string, quantity: number, unitPrice: string) =>
        `${color} · Talle ${size} · ${String(quantity)} × ${unitPrice}`,
      payment: (provider: string) => `Pago con ${provider === 'MERCADOPAGO' ? 'MercadoPago' : provider}`,
      shipment: 'Número de seguimiento',
      correctShipment: 'Corregir número',
      correctedShipment: 'Número de seguimiento corregido.',
      status: 'Estado',
      changeTo: 'Pasar a',
      /** The action that moves the order to each status. */
      actions: {
        PREPARING: 'Preparar',
        SHIPPED: 'Marcar como enviado',
        DELIVERED: 'Marcar como entregado',
        CANCELLED: 'Cancelar pedido',
      },
      shipmentLabel: 'Número de seguimiento del correo',
      ship: 'Confirmar envío',
      restockQuestion: '¿Reponer el stock?',
      restockYes: 'Sí, volver a poner los productos a la venta',
      restockNo: 'No, el stock queda como está',
      confirmCancel: 'Confirmar cancelación',
      keep: 'Volver',
      changed: (status: string) => `Pedido actualizado: ${status}.`,
      stale: 'El pedido cambió mientras lo mirabas. Lo actualizamos: revisalo y probá de nuevo.',
      problems: {
        INVALID_TRANSITION: 'Este cambio de estado no está permitido.',
        MISSING_TRACKING_NUMBER: 'Ingresá el número de seguimiento.',
        INVALID_TRACKING_NUMBER: 'El número de seguimiento tiene que tener de 1 a 60 caracteres.',
        RESTOCK_DECISION_REQUIRED: 'Elegí si se repone el stock.',
        UNEXPECTED_FIELD: 'No pudimos guardar el cambio. Recargá la página y probá de nuevo.',
      },
    },
    productList: {
      title: 'Productos',
      create: 'Nuevo producto',
      empty: 'Todavía no hay productos.',
      published: 'Publicado',
      hidden: 'Oculto',
      variants: (count: number) => (count === 1 ? '1 variante' : `${String(count)} variantes`),
      noCategory: 'Sin categoría',
    },
    productForm: {
      newTitle: 'Nuevo producto',
      name: 'Nombre',
      description: 'Descripción',
      material: 'Material',
      season: 'Temporada',
      audience: 'Público',
      category: 'Categoría',
      pickCategory: 'Elegí una categoría',
      inactiveCategory: (name: string) => `${name} (inactiva)`,
      create: 'Crear producto',
      needsCategory: 'Para cargar productos, primero creá una categoría.',
      slugUsed: (slug: string) => `Ya hay un producto con ese nombre (dirección /producto/${slug}).`,
      invalid: 'Revisá los campos marcados.',
      errors: {
        CATEGORY: 'Elegí una categoría que exista.',
        NAME: 'De 1 a 120 caracteres, en una sola línea y con letras o números.',
        DESCRIPTION: 'Hasta 5000 caracteres.',
        MATERIAL: 'De 1 a 80 caracteres, en una sola línea.',
        SEASON: 'Elegí una temporada.',
        AUDIENCE: 'Elegí un público.',
      },
    },
    productEdit: {
      backToList: 'Volver a productos',
      notFound: 'No encontramos este producto.',
      details: 'Datos',
      state: 'Estado',
      published: 'Publicado: se ve en la tienda.',
      hidden: 'Oculto: no se ve en la tienda.',
      publish: 'Publicar',
      hide: 'Ocultar',
      viewInStore: 'Ver en la tienda',
      needsPricedVariant: 'Para publicarlo, necesita al menos una variante activa con precio.',
    },
    variants: {
      title: 'Variantes',
      sizes: 'Talles',
      colors: 'Colores',
      listHint: 'Separados por coma, por ejemplo: S, M, L',
      generate: 'Generar variantes',
      created: (count: number) =>
        count === 0
          ? 'Esas combinaciones ya existían: no se creó ninguna variante.'
          : count === 1
            ? 'Se creó 1 variante.'
            : `Se crearon ${String(count)} variantes.`,
      empty: 'Todavía no hay variantes. Ingresá talles y colores para generarlas.',
      sku: 'SKU',
      /** The price field of one variant, e.g. "Precio de M, Rosa ($)". */
      priceLabel: (size: string, color: string) => `Precio de ${size}, ${color} ($)`,
      noPrice: 'Sin precio',
      savePrice: 'Guardar precio',
      active: 'Activa',
      /** The active switch of one variant, e.g. "M, Rosa activa". */
      activeLabel: (size: string, color: string) => `${size}, ${color} activa`,
      stock: 'Stock',
      stockLink: (size: string, color: string) => `Stock de ${size}, ${color}`,
      priceInvalid: 'Ingresá un precio en pesos, por ejemplo 12500 o 12500,50.',
      priceOutOfRange: 'El precio tiene que ser mayor que 0 y de hasta $ 10.000.000.',
      lastActivePriced:
        'Es la última variante activa con precio de un producto publicado. Para desactivarla, primero ocultá el producto.',
      notFound: 'La variante ya no existe.',
      problems: {
        INVALID_SIZE: 'Cada talle tiene que tener de 1 a 20 caracteres.',
        INVALID_COLOR: 'Cada color tiene que tener de 1 a 30 caracteres.',
        NO_SIZES: 'Ingresá al menos un talle.',
        NO_COLORS: 'Ingresá al menos un color.',
        REPEATED_SIZES: 'Hay talles repetidos.',
        REPEATED_COLORS: 'Hay colores repetidos.',
        TOO_MANY_SIZES: 'Un producto tiene como máximo 20 talles.',
        TOO_MANY_COLORS: 'Un producto tiene como máximo 20 colores.',
      },
    },
    photos: {
      title: 'Fotos',
      empty: 'Todavía no hay fotos.',
      wholeProduct: 'Todo el producto',
      /** Alternative text of a photo in the back office, e.g. "Foto 2, Rosa". */
      alt: (position: number, color: string | null) =>
        color === null ? `Foto ${String(position)}` : `Foto ${String(position)}, ${color}`,
      moveUp: (position: number) => `Subir foto ${String(position)}`,
      moveDown: (position: number) => `Bajar foto ${String(position)}`,
      delete: (position: number) => `Eliminar foto ${String(position)}`,
      confirmDelete: (position: number) => `¿Eliminar la foto ${String(position)}? No se puede deshacer.`,
      file: 'Agregar fotos (JPG, PNG o WebP)',
      fileHint: 'Podés elegir varias o sacarlas con la cámara. Se achican en el teléfono antes de subirse.',
      color: 'Color de las fotos',
      dragHint: 'Con el mouse podés arrastrar las fotos para cambiar el orden.',
      uploads: 'Fotos que se están subiendo',
      states: {
        shrinking: 'Preparando…',
        waiting: 'En espera',
        uploading: 'Subiendo…',
        uploaded: 'Subida',
        failed: 'No se subió',
        rejected: 'Rechazada',
      },
      progress: (name: string) => `Progreso de ${name}`,
      retryOne: (name: string) => `Reintentar ${name}`,
      dismiss: (name: string) => `Quitar ${name} de la lista`,
      dismissShort: 'Quitar',
      busy: 'El servidor está procesando otras fotos. Tocá “Reintentar” en unos segundos: la foto no se va a duplicar.',
      upload: 'Subir foto',
      uploading: 'Subiendo…',
      uploaded: 'Foto subida.',
      connectionLost: 'Se cortó la conexión. Tocá “Reintentar”: la foto no se va a duplicar.',
      retry: 'Reintentar',
      tooMany: 'Un producto tiene como máximo 8 fotos.',
      fileTooLarge: 'La foto pesa más de 10 MB.',
      problems: {
        UNSUPPORTED_FORMAT: 'El archivo no es una foto JPG, PNG o WebP.',
        TOO_LARGE: 'La foto es demasiado grande: hasta 10.000 píxeles por lado y 50 megapíxeles.',
        UNREADABLE: 'No pudimos leer la foto. Probá con otra.',
        UNKNOWN_COLOR: 'El producto no tiene ese color.',
        KEY_REUSED: 'No pudimos subir la foto. Elegila de nuevo y volvé a intentar.',
      },
      notFound: 'La foto ya no existe.',
    },
    stock: {
      title: 'Stock',
      backToProduct: 'Volver al producto',
      variantNotFound: 'No encontramos esta variante.',
      /** The variant whose stock is shown, e.g. "Pijama Luna · M, Rosa". */
      variant: (product: string, size: string, color: string) => `${product} · ${size}, ${color}`,
      onHand: (count: number) => (count === 1 ? 'Hay 1 unidad.' : `Hay ${String(count)} unidades.`),
      adjust: 'Ajustar stock',
      quantity: 'Cantidad (positiva suma, negativa resta)',
      decrease: 'Restar 1',
      increase: 'Sumar 1',
      note: 'Motivo (opcional)',
      submit: 'Guardar ajuste',
      submitting: 'Guardando…',
      adjusted: (count: number) => `Stock actualizado: ${count === 1 ? '1 unidad' : `${String(count)} unidades`}.`,
      quantityInvalid: 'Ingresá un número entero, sin decimales.',
      wouldGoNegative: (count: number) => `No alcanza el stock: hay ${String(count)}.`,
      wouldExceedMaximum: (count: number) =>
        `El stock no puede superar 1.000.000 de unidades: hay ${String(count)}.`,
      /** UC-16: the units held by orders awaiting payment cannot be subtracted. */
      wouldGoBelowReserved: (reserved: number, count: number) =>
        `No se puede: ${reserved === 1 ? 'hay 1 unidad reservada' : `hay ${String(reserved)} unidades reservadas`} ` +
        `por pedidos pendientes de pago. Hay ${String(count)}.`,
      problems: {
        INVALID_QUANTITY: 'La cantidad tiene que ser de −10.000 a 10.000, distinta de 0.',
        INVALID_NOTE: 'El motivo tiene que ser una sola línea de hasta 200 caracteres.',
        KEY_REUSED: 'No pudimos guardar el ajuste. Probá de nuevo.',
      },
      connectionLost: 'Se cortó la conexión. Volvé a tocar “Guardar ajuste”: el ajuste no se va a duplicar.',
      history: 'Movimientos',
      noMovements: 'Todavía no hay movimientos.',
      unknownUser: '—',
      types: {
        INITIAL_LOAD: 'Carga inicial',
        ADJUSTMENT: 'Ajuste',
        RESERVATION: 'Reserva',
        DEDUCTION: 'Venta',
        RELEASE: 'Liberación',
        RESTOCK: 'Reposición por cancelación',
      },
      /** A signed quantity, e.g. "+3" or "−2". */
      signedQuantity: (quantity: number) => (quantity > 0 ? `+${String(quantity)}` : `−${String(-quantity)}`),
    },
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
