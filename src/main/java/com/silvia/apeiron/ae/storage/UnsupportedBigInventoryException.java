package com.silvia.apeiron.ae.storage;

/** Raised before a request reaches a backend without an exact-count implementation. */
public final class UnsupportedBigInventoryException extends UnsupportedOperationException {

    public UnsupportedBigInventoryException(Object backend) {
        super(
            "inventory has no exact-count implementation: " + backend.getClass()
                .getName());
    }
}
