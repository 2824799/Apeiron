package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import com.silvia.apeiron.ae.crafting.diagnostics.BigCraftingNetworkDiagnostics;
import com.silvia.apeiron.ae.crafting.diagnostics.BigDiagnosticRowValues;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.config.DiagnosticSortMode;
import appeng.api.storage.data.IAEStack;
import appeng.me.diagnostics.CraftingDiagnosticSessionId;
import appeng.me.diagnostics.CraftingNetworkDiagnostics;
import appeng.me.diagnostics.DiagnosticRowView;
import appeng.util.Platform;

/** Stores crafting-diagnostics production totals exactly and exposes saturated legacy rows. */
@Mixin(value = CraftingNetworkDiagnostics.class, remap = false)
public abstract class CraftingNetworkDiagnosticsMixin implements BigCraftingNetworkDiagnostics {

    @Unique
    private final Map<IAEStack<?>, ApeironDiagnosticStats> apeiron$diagnostics = new HashMap<>();
    @Unique
    private long apeiron$revision;

    @Override
    public void recordSampleBig(final IAEStack<?> output, final CraftingDiagnosticSessionId sessionId,
        final BigInteger producedAmount, final long observedStartTick, final long observedEndTick) {
        if (output == null || producedAmount == null
            || producedAmount.signum() <= 0
            || sessionId == null
            || observedStartTick <= 0
            || observedEndTick < observedStartTick) {
            return;
        }

        final IAEStack<?> key = apeiron$normalize(output);
        if (key == null) return;
        this.apeiron$diagnostics.computeIfAbsent(key, ignored -> new ApeironDiagnosticStats())
            .recordSample(sessionId, producedAmount, observedStartTick, observedEndTick);
        this.apeiron$revision++;
    }

    @Overwrite
    public void recordSample(final IAEStack<?> output, final CraftingDiagnosticSessionId sessionId,
        final long producedAmount, final long observedStartTick, final long observedEndTick) {
        this.recordSampleBig(output, sessionId, BigInteger.valueOf(producedAmount), observedStartTick, observedEndTick);
    }

    @Overwrite
    public void completeSession(final CraftingDiagnosticSessionId sessionId) {
        if (sessionId == null) return;
        boolean changed = false;
        for (final ApeironDiagnosticStats stats : this.apeiron$diagnostics.values()) {
            changed |= stats.compactSession(sessionId);
        }
        if (changed) this.apeiron$revision++;
    }

    @Overwrite
    public void clear() {
        if (this.apeiron$diagnostics.isEmpty()) return;
        this.apeiron$diagnostics.clear();
        this.apeiron$revision++;
    }

    @Overwrite
    public void clear(final IAEStack<?> output) {
        final IAEStack<?> key = apeiron$normalize(output);
        if (key != null && this.apeiron$diagnostics.remove(key) != null) this.apeiron$revision++;
    }

    @Overwrite
    public boolean isEmpty() {
        return this.apeiron$diagnostics.isEmpty();
    }

    @Overwrite
    public long getRevision() {
        return this.apeiron$revision;
    }

    @Overwrite
    public List<DiagnosticRowView> createRows(final String search, final DiagnosticSortMode sortMode,
        final boolean ascending) {
        final String normalizedSearch = search == null ? ""
            : search.trim()
                .toLowerCase();
        final List<Map.Entry<IAEStack<?>, ApeironDiagnosticStats>> rows = new ArrayList<>();
        for (final Map.Entry<IAEStack<?>, ApeironDiagnosticStats> entry : this.apeiron$diagnostics.entrySet()) {
            if (normalizedSearch.isEmpty() || entry.getKey()
                .getDisplayName()
                .toLowerCase()
                .contains(normalizedSearch)) {
                rows.add(entry);
            }
        }

        rows.sort((left, right) -> {
            int comparison = apeiron$compareRows(left, right, sortMode);
            if (!ascending) comparison = -comparison;
            if (comparison == 0) comparison = left.getKey()
                .getDisplayName()
                .compareToIgnoreCase(
                    right.getKey()
                        .getDisplayName());
            if (comparison == 0) comparison = Integer.compare(
                left.getKey()
                    .hashCode(),
                right.getKey()
                    .hashCode());
            return comparison;
        });

        final List<DiagnosticRowView> result = new ArrayList<>(rows.size());
        for (final Map.Entry<IAEStack<?>, ApeironDiagnosticStats> row : rows) {
            final ApeironDiagnosticStats stats = row.getValue();
            final BigInteger total = stats.getTotalProduced();
            final BigInteger elapsed = BigInteger.valueOf(stats.getElapsedObservedTicks());
            final BigInteger samples = BigInteger.valueOf(stats.getSampleCount());
            BigDiagnosticRowValues.capture(total, elapsed, samples);
            result.add(
                new DiagnosticRowView(
                    row.getKey()
                        .copy(),
                    BigAEStackValues.saturatedLong(total),
                    BigAEStackValues.saturatedLong(elapsed),
                    BigAEStackValues.saturatedLong(samples)));
        }
        return result;
    }

    @Overwrite
    public NBTTagList writeToNBT() {
        final NBTTagList list = new NBTTagList();
        for (final Map.Entry<IAEStack<?>, ApeironDiagnosticStats> entry : this.apeiron$diagnostics.entrySet()) {
            final NBTTagCompound tag = new NBTTagCompound();
            tag.setTag(
                "Stack",
                entry.getKey()
                    .toNBTGeneric());
            entry.getValue()
                .writeToNBT(tag);
            list.appendTag(tag);
        }
        return list;
    }

    @Overwrite
    public void readFromNBT(final NBTTagList list, final boolean merge) {
        if (!merge) this.apeiron$diagnostics.clear();
        if (list != null) {
            for (int i = 0; i < list.tagCount(); i++) {
                final NBTTagCompound tag = list.getCompoundTagAt(i);
                final IAEStack<?> stack = Platform.readStackNBT(tag.getCompoundTag("Stack"));
                final IAEStack<?> key = apeiron$normalize(stack);
                final ApeironDiagnosticStats loaded = ApeironDiagnosticStats.fromNBT(tag);
                if (key != null && loaded != null) {
                    this.apeiron$diagnostics.computeIfAbsent(key, ignored -> new ApeironDiagnosticStats())
                        .mergeFrom(loaded);
                }
            }
        }
        this.apeiron$revision++;
    }

    @Unique
    private static IAEStack<?> apeiron$normalize(final IAEStack<?> stack) {
        if (stack == null) return null;
        final IAEStack<?> normalized = stack.copy();
        normalized.reset();
        normalized.setStackSize(1);
        return normalized;
    }

    @Unique
    private static int apeiron$compareRows(final Map.Entry<IAEStack<?>, ApeironDiagnosticStats> left,
        final Map.Entry<IAEStack<?>, ApeironDiagnosticStats> right, final DiagnosticSortMode sortMode) {
        final ApeironDiagnosticStats a = left.getValue();
        final ApeironDiagnosticStats b = right.getValue();
        return switch (sortMode) {
            case CRAFTED -> a.getTotalProduced()
                .compareTo(b.getTotalProduced());
            case AVG_PER_SECOND -> a.compareItemsPerSecond(b);
            case SAMPLES -> Long.compare(a.getSampleCount(), b.getSampleCount());
            case NAME -> left.getKey()
                .getDisplayName()
                .compareToIgnoreCase(
                    right.getKey()
                        .getDisplayName());
            case CUMULATIVE_TIME -> Long.compare(a.getElapsedObservedTicks(), b.getElapsedObservedTicks());
        };
    }

    @Unique
    private static final class ApeironDiagnosticStats {

        private BigInteger completedTotalProduced = BigInteger.ZERO;
        private long completedElapsedTimeTicks;
        private long completedSampleCount;
        private final Map<CraftingDiagnosticSessionId, ApeironDiagnosticSessionStats> sessions = new HashMap<>();

        private void recordSample(final CraftingDiagnosticSessionId sessionId, final BigInteger producedAmount,
            final long observedStartTick, final long observedEndTick) {
            this.sessions.computeIfAbsent(sessionId, ignored -> new ApeironDiagnosticSessionStats())
                .recordSample(producedAmount, observedStartTick, observedEndTick);
        }

        private BigInteger getTotalProduced() {
            BigInteger result = this.completedTotalProduced;
            for (final ApeironDiagnosticSessionStats session : this.sessions.values())
                result = result.add(session.totalProduced);
            return result;
        }

        private long getElapsedObservedTicks() {
            long result = this.completedElapsedTimeTicks;
            for (final ApeironDiagnosticSessionStats session : this.sessions.values())
                result += session.getElapsedObservedTicks();
            return result;
        }

        private long getSampleCount() {
            long result = this.completedSampleCount;
            for (final ApeironDiagnosticSessionStats session : this.sessions.values()) result += session.sampleCount;
            return result;
        }

        private boolean compactSession(final CraftingDiagnosticSessionId sessionId) {
            final ApeironDiagnosticSessionStats session = this.sessions.remove(sessionId);
            if (session == null) return false;
            this.completedTotalProduced = this.completedTotalProduced.add(session.totalProduced);
            this.completedElapsedTimeTicks += session.getElapsedObservedTicks();
            this.completedSampleCount += session.sampleCount;
            return true;
        }

        private void mergeFrom(final ApeironDiagnosticStats loaded) {
            this.completedTotalProduced = this.completedTotalProduced.add(loaded.completedTotalProduced);
            this.completedElapsedTimeTicks += loaded.completedElapsedTimeTicks;
            this.completedSampleCount += loaded.completedSampleCount;
            for (final Map.Entry<CraftingDiagnosticSessionId, ApeironDiagnosticSessionStats> entry : loaded.sessions
                .entrySet()) {
                this.sessions.computeIfAbsent(entry.getKey(), ignored -> new ApeironDiagnosticSessionStats())
                    .mergeFrom(entry.getValue());
            }
        }

        private int compareItemsPerSecond(final ApeironDiagnosticStats other) {
            final long leftTicks = this.getElapsedObservedTicks();
            final long rightTicks = other.getElapsedObservedTicks();
            if (leftTicks <= 0 || rightTicks <= 0) return Long.compare(leftTicks, rightTicks);
            return this.getTotalProduced()
                .multiply(BigInteger.valueOf(rightTicks))
                .compareTo(
                    other.getTotalProduced()
                        .multiply(BigInteger.valueOf(leftTicks)));
        }

        private void writeToNBT(final NBTTagCompound tag) {
            final BigInteger total = this.getTotalProduced();
            BigValueCodec.writeNBT(tag, "TotalProduced", "ApeironTotalProduced", new AdaptiveInteger(total));
            BigValueCodec.writeNBT(
                tag,
                "CompletedTotalProduced",
                "ApeironCompletedTotalProduced",
                new AdaptiveInteger(this.completedTotalProduced));
            tag.setLong("CompletedElapsedTimeTicks", this.completedElapsedTimeTicks);
            tag.setLong("CompletedSampleCount", this.completedSampleCount);
            tag.setLong("SampleCount", this.getSampleCount());
            final NBTTagList sessionsTag = new NBTTagList();
            for (final Map.Entry<CraftingDiagnosticSessionId, ApeironDiagnosticSessionStats> entry : this.sessions
                .entrySet()) {
                final NBTTagCompound sessionTag = new NBTTagCompound();
                entry.getKey()
                    .writeToNBT(sessionTag, "SessionId");
                entry.getValue()
                    .writeToNBT(sessionTag);
                sessionsTag.appendTag(sessionTag);
            }
            tag.setTag("Sessions", sessionsTag);
        }

        private static ApeironDiagnosticStats fromNBT(final NBTTagCompound tag) {
            if (tag == null || !tag.hasKey("CompletedElapsedTimeTicks", Constants.NBT.TAG_LONG)) return null;
            final ApeironDiagnosticStats stats = new ApeironDiagnosticStats();
            stats.completedTotalProduced = tag.hasKey("ApeironCompletedTotalProduced", Constants.NBT.TAG_BYTE_ARRAY)
                ? BigValueCodec.readNBT(tag, "CompletedTotalProduced", "ApeironCompletedTotalProduced")
                    .toBigInteger()
                : BigInteger.valueOf(tag.getLong("CompletedTotalProduced"));
            stats.completedElapsedTimeTicks = tag.getLong("CompletedElapsedTimeTicks");
            stats.completedSampleCount = tag.getLong("CompletedSampleCount");
            if (tag.hasKey("Sessions", Constants.NBT.TAG_LIST)) {
                final NBTTagList sessionsTag = tag.getTagList("Sessions", Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < sessionsTag.tagCount(); i++) {
                    final NBTTagCompound sessionTag = sessionsTag.getCompoundTagAt(i);
                    final CraftingDiagnosticSessionId id = CraftingDiagnosticSessionId.fromNBT(sessionTag, "SessionId");
                    final ApeironDiagnosticSessionStats session = ApeironDiagnosticSessionStats.fromNBT(sessionTag);
                    if (id != null && session != null) stats.sessions.put(id, session);
                }
            }
            return stats;
        }
    }

    @Unique
    private static final class ApeironDiagnosticSessionStats {

        private BigInteger totalProduced = BigInteger.ZERO;
        private long elapsedObservedTicks;
        private long firstObservedTick;
        private long lastObservedTick;
        private long sampleCount;

        private void recordSample(final BigInteger producedAmount, final long observedStartTick,
            final long observedEndTick) {
            this.totalProduced = this.totalProduced.add(producedAmount);
            this.sampleCount++;
            if (this.firstObservedTick == 0L || observedStartTick < this.firstObservedTick)
                this.firstObservedTick = observedStartTick;
            if (observedEndTick > this.lastObservedTick) this.lastObservedTick = observedEndTick;
        }

        private long getElapsedObservedTicks() {
            long result = this.elapsedObservedTicks;
            if (this.firstObservedTick > 0L && this.sampleCount > 0L) {
                result += Math.max(1L, this.lastObservedTick - this.firstObservedTick);
            }
            return result;
        }

        private void mergeFrom(final ApeironDiagnosticSessionStats loaded) {
            this.totalProduced = this.totalProduced.add(loaded.totalProduced);
            this.sampleCount += loaded.sampleCount;
            this.elapsedObservedTicks += loaded.getElapsedObservedTicks();
        }

        private void writeToNBT(final NBTTagCompound tag) {
            BigValueCodec
                .writeNBT(tag, "TotalProduced", "ApeironTotalProduced", new AdaptiveInteger(this.totalProduced));
            tag.setLong("ElapsedObservedTicks", this.getElapsedObservedTicks());
            tag.setLong("SampleCount", this.sampleCount);
        }

        private static ApeironDiagnosticSessionStats fromNBT(final NBTTagCompound tag) {
            if (tag == null || !tag.hasKey("ElapsedObservedTicks", Constants.NBT.TAG_LONG)) return null;
            final ApeironDiagnosticSessionStats stats = new ApeironDiagnosticSessionStats();
            stats.totalProduced = tag.hasKey("ApeironTotalProduced", Constants.NBT.TAG_BYTE_ARRAY)
                ? BigValueCodec.readNBT(tag, "TotalProduced", "ApeironTotalProduced")
                    .toBigInteger()
                : BigInteger.valueOf(tag.getLong("TotalProduced"));
            stats.sampleCount = tag.getLong("SampleCount");
            stats.elapsedObservedTicks = tag.getLong("ElapsedObservedTicks");
            return stats;
        }
    }
}
