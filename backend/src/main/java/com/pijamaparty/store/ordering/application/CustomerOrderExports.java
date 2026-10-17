package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.platform.domain.UserId;
import java.util.List;

/** Outbound read port (UC-28): every order of a customer, for its data download, within the caller's transaction. */
public interface CustomerOrderExports {

    /** Only the orders of {@code customer}, newest first (then the highest number). */
    List<ExportedOrder> all(UserId customer);
}
