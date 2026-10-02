package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.silvia.apeiron.ae.crafting.core.BigTaskProgress;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.diagnostics.CraftingDiagnosticSessionId;

/** Stores exact crafting progress beside AE2's legacy long field. */
@Mixin(value = CraftingCPUCluster.TaskProgress.class, remap = false)
public abstract class CraftingTaskProgressMixin implements BigTaskProgress {

    @Shadow
    protected long value;

    @Unique
    private AdaptiveInteger apeiron$valueBig;

    @Unique
    private final LinkedList<ApeironSessionCraftCount> apeiron$diagnosticSessions = new LinkedList<>();

    @Override
    public BigInteger getValueBig() {
        return this.apeiron$valueBig == null ? BigInteger.valueOf(this.value) : this.apeiron$valueBig.toBigInteger();
    }

    @Override
    public void setValueBig(final BigInteger value) {
        if (AdaptiveInteger.fitsLong(value)) {
            this.value = value.longValue();
            this.apeiron$valueBig = null;
        } else {
            this.apeiron$valueBig = new AdaptiveInteger(value);
            this.value = this.apeiron$valueBig.longValueSaturated();
        }
    }

    @Override
    public void decrementValueBig() {
        this.setValueBig(
            this.getValueBig()
                .subtract(BigInteger.ONE));
    }

    @Override
    public boolean isValueBig() {
        return this.apeiron$valueBig != null;
    }

    @Override
    public long getValueLong() {
        return this.apeiron$valueBig == null ? this.value : this.apeiron$valueBig.longValueSaturated();
    }

    @Overwrite
    protected void addCraftsToSession(final CraftingDiagnosticSessionId sessionId, final long crafts) {
        this.addCraftsToSessionBig(sessionId, BigInteger.valueOf(crafts));
    }

    @Override
    public void addCraftsToSessionBig(final CraftingDiagnosticSessionId sessionId, final BigInteger crafts) {
        if (sessionId == null || crafts == null || crafts.signum() <= 0) return;
        final ApeironSessionCraftCount last = this.apeiron$diagnosticSessions.peekLast();
        if (last != null && last.sessionId.equals(sessionId)) {
            last.remaining = last.remaining.add(crafts);
        } else {
            this.apeiron$diagnosticSessions.addLast(new ApeironSessionCraftCount(sessionId, crafts));
        }
    }

    @Overwrite
    protected CraftingDiagnosticSessionId consumeCraftSession() {
        final ApeironSessionCraftCount first = this.apeiron$diagnosticSessions.peekFirst();
        if (first == null) return null;
        final CraftingDiagnosticSessionId result = first.sessionId;
        first.remaining = first.remaining.subtract(BigInteger.ONE);
        if (first.remaining.signum() <= 0) this.apeiron$diagnosticSessions.removeFirst();
        return result;
    }

    @Overwrite
    protected boolean hasDiagnosticSession(final CraftingDiagnosticSessionId sessionId) {
        if (sessionId == null) return false;
        for (final ApeironSessionCraftCount entry : this.apeiron$diagnosticSessions) {
            if (entry.sessionId.equals(sessionId) && entry.remaining.signum() > 0) return true;
        }
        return false;
    }

    @Overwrite
    protected void addDiagnosticSessionIdsTo(final Set<CraftingDiagnosticSessionId> sessionIds) {
        for (final ApeironSessionCraftCount entry : this.apeiron$diagnosticSessions) {
            if (entry.remaining.signum() > 0) sessionIds.add(entry.sessionId);
        }
    }

    @Overwrite
    protected void copyDiagnosticSessionsFrom(final CraftingCPUCluster.TaskProgress other) {
        this.apeiron$diagnosticSessions.clear();
        if (other instanceof BigTaskProgress) {
            ((BigTaskProgress) other).forEachDiagnosticSessionBig(
                (sessionId, remaining) -> this.addCraftsToSessionBig(sessionId, remaining));
        }
    }

    @Override
    public void clearDiagnosticSessionsBig() {
        this.apeiron$diagnosticSessions.clear();
    }

    @Override
    public void forEachDiagnosticSessionBig(final BigTaskProgress.SessionConsumer consumer) {
        for (final ApeironSessionCraftCount entry : this.apeiron$diagnosticSessions) {
            consumer.accept(entry.sessionId, entry.remaining);
        }
    }

    @Overwrite
    protected NBTTagList writeDiagnosticSessionsToNBT() {
        final NBTTagList result = new NBTTagList();
        for (final ApeironSessionCraftCount entry : this.apeiron$diagnosticSessions) {
            final NBTTagCompound tag = new NBTTagCompound();
            entry.sessionId.writeToNBT(tag, "id");
            BigValueCodec.writeNBT(tag, "remaining", "ApeironRemaining", new AdaptiveInteger(entry.remaining));
            result.appendTag(tag);
        }
        return result;
    }

    @Overwrite
    protected void readDiagnosticSessionsFromNBT(final NBTTagList source) {
        this.apeiron$diagnosticSessions.clear();
        for (int index = 0; index < source.tagCount(); index++) {
            final NBTTagCompound tag = source.getCompoundTagAt(index);
            final BigInteger remaining = tag.hasKey("ApeironRemaining", NBT.TAG_BYTE_ARRAY)
                ? BigValueCodec.readNBT(tag, "remaining", "ApeironRemaining")
                    .toBigInteger()
                : BigInteger.valueOf(tag.hasKey("remaining", NBT.TAG_LONG) ? tag.getLong("remaining") : 1L);
            this.addCraftsToSessionBig(CraftingDiagnosticSessionId.fromNBT(tag, "id"), remaining);
        }
    }

    private static final class ApeironSessionCraftCount {

        private final CraftingDiagnosticSessionId sessionId;
        private BigInteger remaining;

        private ApeironSessionCraftCount(final CraftingDiagnosticSessionId sessionId, final BigInteger remaining) {
            this.sessionId = sessionId;
            this.remaining = remaining;
        }
    }
}
