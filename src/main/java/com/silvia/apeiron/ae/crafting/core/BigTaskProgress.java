package com.silvia.apeiron.ae.crafting.core;

import java.math.BigInteger;

import appeng.me.diagnostics.CraftingDiagnosticSessionId;

/** Exact crafting-task progress attached to AE2's legacy TaskProgress object. */
public interface BigTaskProgress {

    BigInteger getValueBig();

    void setValueBig(BigInteger value);

    void decrementValueBig();

    boolean isValueBig();

    long getValueLong();

    void addCraftsToSessionBig(CraftingDiagnosticSessionId sessionId, BigInteger crafts);

    void clearDiagnosticSessionsBig();

    void forEachDiagnosticSessionBig(SessionConsumer consumer);

    interface SessionConsumer {

        void accept(CraftingDiagnosticSessionId sessionId, BigInteger remaining);
    }
}
