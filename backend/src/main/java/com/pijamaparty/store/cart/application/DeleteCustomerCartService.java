package com.pijamaparty.store.cart.application;

import com.pijamaparty.store.cart.domain.Cart;
import com.pijamaparty.store.cart.domain.CartOwner;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Joins the account deletion's transaction, as the customer itself, which owns its cart (V12). */
@Service
class DeleteCustomerCartService implements DeleteCustomerCart {

    private final LoadCart loadCart;
    private final DeleteCart deleteCart;

    DeleteCustomerCartService(LoadCart loadCart, DeleteCart deleteCart) {
        this.loadCart = loadCart;
        this.deleteCart = deleteCart;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result execute(Command command) {
        Optional<Cart> cart = loadCart.lockByOwner(new CartOwner.Customer(command.customer()));
        if (cart.isEmpty() || !deleteCart.delete(cart.get().id())) {
            return new Result.NoCart();
        }
        return new Result.Deleted();
    }
}
