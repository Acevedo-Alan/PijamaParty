package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.application.CheckoutCartLock.CartState;
import com.pijamaparty.store.ordering.application.ReserveOrderStock.Shortage;
import com.pijamaparty.store.ordering.application.SaveOrders.AppliedCoupon;
import com.pijamaparty.store.ordering.application.SaveOrders.NewOrder;
import com.pijamaparty.store.ordering.domain.CheckoutForm;
import com.pijamaparty.store.ordering.domain.OrderAmounts;
import com.pijamaparty.store.ordering.domain.OrderId;
import com.pijamaparty.store.ordering.domain.StockHold;
import com.pijamaparty.store.platform.application.IdGenerator;
import com.pijamaparty.store.platform.domain.AccessToken;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PlaceOrderService implements PlaceOrder {

    private final CheckoutCartLock carts;
    private final ShippingQuotes shippingQuotes;
    private final ReserveOrderStock stock;
    private final OrderNumbers orderNumbers;
    private final SaveOrders saveOrders;
    private final ClearCart clearCart;
    private final CurrentTerms terms;
    private final IdGenerator ids;
    private final Clock clock;

    PlaceOrderService(CheckoutCartLock carts, ShippingQuotes shippingQuotes, ReserveOrderStock stock,
                      OrderNumbers orderNumbers, SaveOrders saveOrders, ClearCart clearCart, CurrentTerms terms,
                      IdGenerator ids, Clock clock) {
        this.carts = carts;
        this.shippingQuotes = shippingQuotes;
        this.stock = stock;
        this.orderNumbers = orderNumbers;
        this.saveOrders = saveOrders;
        this.clearCart = clearCart;
        this.terms = terms;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * As the buyer: Row Level Security lets it lock only its own cart and create only the order of its tracking token
     * (RNF-27). The stock is reserved before anything is written, so a short variant leaves nothing behind.
     */
    @Override
    @Transactional
    public Result execute(Command command) {
        CheckoutForm.Outcome form = command.form().validate();
        if (form instanceof CheckoutForm.Outcome.Invalid invalid) {
            return new Result.InvalidBuyerData(invalid.fields());
        }
        if (!command.acceptedTerms()) {
            return new Result.TermsNotAccepted();
        }
        CheckoutForm.Outcome.Valid valid = (CheckoutForm.Outcome.Valid) form;
        Actor actor = command.actor();
        CartState.Ready cart;
        switch (carts.lock(actor)) {
            case CartState.Empty empty -> {
                return new Result.EmptyCart();
            }
            case CartState.NotReady notReady -> {
                return new Result.CartNotReady();
            }
            case CartState.Ready ready -> cart = ready;
        }
        // Only a guest or a customer has a cart, and the checkout binds it to the new order's tracking token.
        AccessToken.TrackingToken trackingToken = actor.trackingToken().orElseThrow(() ->
            new IllegalStateException("the checkout binds the new order's tracking token before it begins"));
        if (cart.coupon().isPresent() && !cart.coupon().get().applies()) {
            return new Result.CouponNotApplicable(cart.coupon().get().status());
        }
        Money afterDiscount = cart.subtotal().minus(cart.discount());
        Optional<Money> shipping = shippingQuotes.costTo(command.province(), afterDiscount);
        if (shipping.isEmpty()) {
            return new Result.NoShipping(command.province());
        }
        OrderId orderId = OrderId.of(ids.newId());
        Optional<Shortage> shortage = stock.reserve(orderId, cart.lines());
        if (shortage.isPresent()) {
            return new Result.InsufficientStock(shortage.get().variantId(), shortage.get().available());
        }
        Instant now = clock.instant();
        OrderAmounts amounts = OrderAmounts.of(cart.subtotal(), cart.discount(), shipping.get());
        long number = orderNumbers.next();
        Instant expiresAt = StockHold.expiryOf(now);
        saveOrders.insert(new NewOrder(orderId, number, trackingToken.hash(), actor.userId(), valid.buyer(),
            valid.address(), command.province(), cart.lines(), amounts,
            cart.coupon().map(coupon -> new AppliedCoupon(coupon.couponId(), coupon.code())),
            command.paymentProvider(), terms.version(), now, expiresAt));
        clearCart.clear(actor);
        return new Result.Placed(orderId, number, amounts.total(), expiresAt);
    }
}
