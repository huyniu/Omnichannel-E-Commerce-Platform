package com.omni.identity.domain;

public enum Role {
    /** Buys products, manages own profile, addresses and orders. */
    CUSTOMER,
    /** Operates the shop: catalog, stock, order fulfilment. */
    STAFF,
    /** Everything STAFF can do plus user management. */
    ADMIN
}
