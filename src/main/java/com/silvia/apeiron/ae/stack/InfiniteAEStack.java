package com.silvia.apeiron.ae.stack;

/** A renewable creative source has no finite stored count. Numeric legacy views are projections only. */
public interface InfiniteAEStack {

    boolean isInfinite();

    void setInfinite(boolean infinite);
}
