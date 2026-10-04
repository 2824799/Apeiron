package com.silvia.apeiron.api.machine.me.output;

/** Exact transaction lifecycle shared by all supported GregTech versions. */
public interface BigOutputTransaction {

    void commit();
}
