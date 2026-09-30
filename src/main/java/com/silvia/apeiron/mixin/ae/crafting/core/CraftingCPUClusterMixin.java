package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.crafting.diagnostics.BigCompletedDiagnosticRecord;
import com.silvia.apeiron.ae.crafting.packets.BigCraftNotificationValues;
import com.silvia.apeiron.ae.crafting.diagnostics.BigCraftingGridDiagnostics;
import com.silvia.apeiron.ae.crafting.core.BigFinalOutput;
import com.silvia.apeiron.ae.crafting.core.BigCraftingCPU;
import com.silvia.apeiron.ae.crafting.core.BigCraftingCPUStorage;
import com.silvia.apeiron.ae.crafting.core.BigCraftingJob;
import com.silvia.apeiron.ae.crafting.core.BigTaskProgress;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;
import com.silvia.apeiron.ae.terminal.BigGuiNumberCapture;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.cache.CraftingGridCache;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.tile.crafting.TileCraftingMonitorTile;
import appeng.tile.crafting.TileCraftingTile;
import appeng.me.diagnostics.CraftingDiagnosticSessionId;
import appeng.api.networking.security.MachineSource;
import appeng.util.Platform;

/** Keeps CPU task counts and pending-output multiplication exact. */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class CraftingCPUClusterMixin implements BigCraftingCPU, BigCraftingCPUStorage {

    @Unique
    private static BigInteger apeiron$jobBytes(final ICraftingJob job) {
        return job instanceof BigCraftingJob
            ? ((BigCraftingJob) job).getByteTotalBig()
            : BigInteger.valueOf(job.getByteTotal());
    }

    @Redirect(
        method = "completeJob",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster$finalOutput;getOriginalCount()J"))
    private long apeiron$captureCompletionCount(final CraftingCPUCluster.finalOutput output) {
        final BigInteger exact = ((BigFinalOutput) (Object) output).getOriginalCountBig();
        BigCraftNotificationValues.capture(exact);
        return BigAEStackValues.saturatedLong(exact);
    }

    @Redirect(
        method = "handleCraftBranchFailure",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getStackSize()J"))
    private long apeiron$captureMissingCount(final IAEStack<?> stack) {
        return BigGuiNumberCapture.captureAmount(BigAEStackValues.get(stack));
    }

    @Redirect(
        method = "handleCraftBranchFailure",
        at = @At(value = "INVOKE", target = "Ljava/text/NumberFormat;format(J)Ljava/lang/String;"))
    private String apeiron$formatMissingCount(final java.text.NumberFormat formatter, final long value) {
        return BigGuiNumberCapture.formatExactAmount(value);
    }

    @Unique
    private BigInteger apeiron$availableStorage;
    @Unique
    private BigInteger apeiron$usedStorage;
    @Unique
    private boolean apeiron$insideSubmitJob;

    @Shadow
    @Final
    protected Map<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> tasks;

    @Shadow
    @Final
    protected IItemList<IAEStack<?>> waitingFor;

    @Shadow
    @Final
    protected MECraftingInventory inventory;

    @Shadow
    protected abstract void postCraftingStatusChange(IAEStack<?> diff);

    @Shadow
    protected abstract void completeJob();

    @Shadow
    public abstract appeng.api.networking.IGrid getGrid();

    @Shadow
    protected CraftingDiagnosticSessionId currentPlanningDiagnosticSessionId;

    @Shadow
    @Final
    protected LinkedList<TileCraftingTile> tiles;

    @Shadow
    @Final
    protected LinkedList<TileCraftingMonitorTile> status;

    @Shadow
    protected MachineSource machineSrc;

    @Shadow
    protected int accelerator;

    @Shadow
    protected long availableStorage;

    @Shadow
    protected long usedStorage;

    @Override
    public Map<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> getTaskEntriesBig() {
        return this.tasks;
    }

    @Override
    public void postCraftingStatusChangeBig(final IAEStack<?> stack) {
        this.postCraftingStatusChange(stack);
    }

    @Override
    public void completeJobBig() {
        this.completeJob();
    }

    @Overwrite
    protected void addTile(final TileCraftingTile tile) {
        if (this.machineSrc == null || tile.isCoreBlock()) {
            this.machineSrc = new MachineSource(tile);
        }

        tile.setCoreBlock(false);
        tile.markDirty();
        this.tiles.push(tile);

        if (tile.isStorage()) {
            this.apeiron$availableStorage = this.getAvailableStorageBig()
                    .add(BigInteger.valueOf(tile.getStorageBytes()));
            this.availableStorage = BigAEStackValues.saturatedLong(this.apeiron$availableStorage);
        } else if (tile.isStatus()) {
            this.status.add((TileCraftingMonitorTile) tile);
        } else if (tile.isAccelerator()) {
            this.accelerator += tile.acceleratorValue();
        }
    }

    @Redirect(
        method = { "executeCrafting", "mergeJob", "writeToNBT", "readFromNBT" },
        at = @At(
            value = "FIELD",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster$TaskProgress;value:J",
            opcode = Opcodes.GETFIELD))
    private long apeiron$getTaskProgress(final CraftingCPUCluster.TaskProgress progress) {
        return ((BigTaskProgress) progress).getValueLong();
    }

    @Redirect(
        method = { "executeCrafting", "mergeJob", "writeToNBT", "readFromNBT" },
        at = @At(
            value = "FIELD",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster$TaskProgress;value:J",
            opcode = Opcodes.PUTFIELD))
    private void apeiron$setTaskProgress(final CraftingCPUCluster.TaskProgress progress, final long value) {
        final BigTaskProgress exact = (BigTaskProgress) progress;
        if (exact.isValueBig() && value == Long.MAX_VALUE - 1L) {
            exact.decrementValueBig();
        } else if (exact.isValueBig() && value == Long.MAX_VALUE) {
            // A legacy assignment copied the saturated view. Keep the exact sidecar.
        } else {
            exact.setValueBig(BigInteger.valueOf(value));
        }
    }

    @Overwrite
    protected boolean hasRemainingTasks() {
        this.tasks.entrySet().removeIf(entry -> valueOf(entry.getValue()).signum() <= 0);
        return !this.tasks.isEmpty();
    }

    @Overwrite
    public void getListOfItem(final IItemList<IAEItemStack> list, final CraftingItemList whichList) {
        switch (whichList) {
            case ACTIVE -> {
                for (final IAEStack<?> stack : this.waitingFor) list.add(Platform.stackConvert(stack));
            }
            case PENDING -> {
                for (final Map.Entry<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> entry : this.tasks
                        .entrySet()) {
                    for (IAEItemStack stack : entry.getKey().getCondensedOutputs()) {
                        final IAEItemStack copy = stack.copy();
                        BigAEStackValues.set(copy, BigAEStackValues.get(copy).multiply(valueOf(entry.getValue())));
                        list.add(copy);
                    }
                }
            }
            case STORAGE -> this.inventory.getAvailableItems(list);
            default -> {
                this.inventory.getAvailableItems(list);
                for (final IAEStack<?> stack : this.waitingFor) list.add(Platform.stackConvert(stack));
                for (final Map.Entry<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> entry : this.tasks
                        .entrySet()) {
                    for (IAEItemStack stack : entry.getKey().getCondensedOutputs()) {
                        final IAEItemStack copy = stack.copy();
                        BigAEStackValues.set(copy, BigAEStackValues.get(copy).multiply(valueOf(entry.getValue())));
                        list.add(copy);
                    }
                }
            }
        }
    }

    @Overwrite
    public void getModernListOfItem(final IItemList<IAEStack<?>> list, final CraftingItemList whichList) {
        switch (whichList) {
            case ACTIVE -> {
                for (final IAEStack<?> stack : this.waitingFor) list.add(stack);
            }
            case PENDING -> addPendingModern(list);
            case STORAGE -> this.inventory.getAvailableItems(list);
            default -> {
                this.inventory.getAvailableItems(list);
                for (final IAEStack<?> stack : this.waitingFor) list.add(stack);
                addPendingModern(list);
            }
        }
    }

    @Unique
    private void addPendingModern(final IItemList<IAEStack<?>> list) {
        for (final Map.Entry<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> entry : this.tasks.entrySet()) {
            for (IAEStack<?> stack : entry.getKey().getCondensedAEOutputs()) {
                final IAEStack<?> copy = stack.copy();
                BigAEStackValues.set(copy, BigAEStackValues.get(copy).multiply(valueOf(entry.getValue())));
                list.add(copy);
            }
        }
    }

    @Overwrite
    public void addCrafting(final ICraftingPatternDetails details, final long crafts) {
        this.addCraftingBig(details, BigInteger.valueOf(crafts));
    }

    @Override
    public void addCraftingBig(final ICraftingPatternDetails details, final BigInteger crafts) {
        CraftingCPUCluster.TaskProgress progress = this.tasks.get(details);
        if (progress == null) {
            progress = new CraftingCPUCluster.TaskProgress();
            this.tasks.put(details, progress);
        }
        ((BigTaskProgress) progress).setValueBig(valueOf(progress).add(crafts));
        ((BigTaskProgress) progress).addCraftsToSessionBig(
                this.currentPlanningDiagnosticSessionId,
                crafts);
    }

    @Override
    public BigInteger getStackAmountBig(final IAEStack<?> what, final CraftingItemList list) {
        switch (list) {
            case STORAGE: {
                @SuppressWarnings("rawtypes")
                final IAEStack<?> stack = (IAEStack) this.inventory.findPrecise((IAEStack) what);
                return stack == null ? BigInteger.ZERO : BigAEStackValues.get(stack);
            }
            case ACTIVE: {
                final IAEStack<?> stack = this.waitingFor.findPrecise(what);
                return stack == null ? BigInteger.ZERO : BigAEStackValues.get(stack);
            }
            case PENDING: {
                BigInteger amount = BigInteger.ZERO;
                for (final Map.Entry<ICraftingPatternDetails, CraftingCPUCluster.TaskProgress> entry : this.tasks
                        .entrySet()) {
                    for (final IAEStack<?> stack : entry.getKey().getCondensedAEOutputs()) {
                        if (Objects.equals(stack, what)) {
                            amount = amount.add(BigAEStackValues.get(stack).multiply(valueOf(entry.getValue())));
                        }
                    }
                }
                return amount;
            }
            default:
                throw new IllegalStateException("Invalid Operation");
        }
    }

    @Overwrite
    public long getStackAmount(final IAEStack what, final CraftingItemList list) {
        return BigAEStackValues.saturatedLong(getStackAmountBig(what, list));
    }

    @Override
    public BigInteger getAvailableStorageBig() {
        if (this.apeiron$availableStorage == null) {
            this.apeiron$availableStorage = BigInteger.valueOf(this.availableStorage);
        }
        return this.apeiron$availableStorage;
    }

    @Override
    public BigInteger getUsedStorageBig() {
        if (this.apeiron$usedStorage == null) {
            this.apeiron$usedStorage = BigInteger.valueOf(this.usedStorage);
        }
        return this.apeiron$usedStorage;
    }

    @Overwrite
    public long getAvailableStorage() {
        return BigAEStackValues.saturatedLong(this.getAvailableStorageBig());
    }

    @Overwrite
    public long getUsedStorage() {
        return BigAEStackValues.saturatedLong(this.getUsedStorageBig());
    }

    @Inject(method = "submitJob", at = @At("HEAD"))
    private void apeiron$beginSubmit(final CallbackInfo ci) {
        if (this.apeiron$usedStorage == null) {
            this.apeiron$usedStorage = BigInteger.valueOf(this.usedStorage);
        }
        this.apeiron$insideSubmitJob = true;
    }

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true)
    private void apeiron$checkExactJobStorage(final appeng.api.networking.IGrid grid, final ICraftingJob job,
        final BaseActionSource source, final ICraftingRequester requestingMachine,
        final CallbackInfoReturnable<ICraftingLink> cir) {
        if (!(job instanceof BigCraftingJob)) return;
        final BigInteger free = this.getAvailableStorageBig().subtract(this.getUsedStorageBig());
        if (free.compareTo(apeiron$jobBytes(job)) < 0) {
            this.apeiron$insideSubmitJob = false;
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "submitJob", at = @At("RETURN"))
    private void apeiron$finishSubmit(final appeng.api.networking.IGrid grid, final ICraftingJob job,
        final BaseActionSource source, final ICraftingRequester requestingMachine,
        final CallbackInfoReturnable<ICraftingLink> cir) {
        if (cir.getReturnValue() != null) {
            final BigInteger bytes = apeiron$jobBytes(job);
            this.apeiron$usedStorage = this.getUsedStorageBig().add(bytes);
            this.usedStorage = BigAEStackValues.saturatedLong(this.apeiron$usedStorage);
        }
        this.apeiron$insideSubmitJob = false;
    }

    @Inject(method = "mergeJob", at = @At("HEAD"))
    private void apeiron$beginDirectMerge(final appeng.api.networking.IGrid grid, final ICraftingJob job,
        final BaseActionSource source, final ICraftingRequester requestingMachine, final CallbackInfo ci) {
        if (this.apeiron$usedStorage == null) {
            this.apeiron$usedStorage = BigInteger.valueOf(this.usedStorage);
        }
    }

    @Inject(method = "mergeJob", at = @At("RETURN"))
    private void apeiron$finishDirectMerge(final appeng.api.networking.IGrid grid, final ICraftingJob job,
        final BaseActionSource source, final ICraftingRequester requestingMachine,
        final CallbackInfoReturnable<ICraftingLink> cir) {
        if (!this.apeiron$insideSubmitJob && cir.getReturnValue() != null) {
            final BigInteger bytes = apeiron$jobBytes(job);
            this.apeiron$usedStorage = this.getUsedStorageBig().add(bytes);
            this.usedStorage = BigAEStackValues.saturatedLong(this.apeiron$usedStorage);
        }
    }

    @Inject(method = "completeJob", at = @At("TAIL"))
    private void apeiron$clearUsedStorage(final CallbackInfo ci) {
        this.apeiron$usedStorage = BigInteger.ZERO;
    }

    @Inject(method = "cancel", at = @At("TAIL"))
    private void apeiron$clearCanceledStorage(final CallbackInfo ci) {
        this.apeiron$usedStorage = BigInteger.ZERO;
    }

    @Overwrite
    protected void pushDiagnosticSample(final appeng.me.cluster.implementations.CraftingCpuDiagnostics.CompletedDiagnosticRecord record) {
        if (record.getElapsedTicks() <= 0L || this.getGrid() == null) return;

        final ICraftingGrid craftingGrid = this.getGrid().getCache(ICraftingGrid.class);
        if (craftingGrid instanceof CraftingGridCache cache && cache.isDiagnosticsEnabled()
            && cache instanceof BigCraftingGridDiagnostics exact) {
            exact.recordDiagnosticSampleBig(
                record.getOutput(),
                record.getDiagnosticSessionId(),
                BigCompletedDiagnosticRecord.produced(record),
                record.getStartTick(),
                record.getEndTick());
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void apeiron$writeExactTaskProgress(final NBTTagCompound data, final CallbackInfo ci) {
        BigValueCodec.writeNBT(
                data,
                "usedStorage",
                "ApeironUsedStorage",
                new AdaptiveInteger(this.getUsedStorageBig()));
        BigValueCodec.writeNBT(
                data,
                "availableStorage",
                "ApeironAvailableStorage",
                new AdaptiveInteger(this.getAvailableStorageBig()));
        final NBTTagList serialized = data.getTagList("tasks", 10);
        int index = 0;
        for (final CraftingCPUCluster.TaskProgress progress : this.tasks.values()) {
            if (index >= serialized.tagCount()) break;
            final NBTTagCompound tag = serialized.getCompoundTagAt(index++);
            final BigTaskProgress exact = (BigTaskProgress) progress;
            if (exact.isValueBig()) tag.setByteArray("ApeironCraftingProgress", exact.getValueBig().toByteArray());
            else tag.removeTag("ApeironCraftingProgress");
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void apeiron$readExactTaskProgress(final NBTTagCompound data, final CallbackInfo ci) {
        this.apeiron$usedStorage = data.hasKey("ApeironUsedStorage", 7)
                ? BigValueCodec.readNBT(data, "usedStorage", "ApeironUsedStorage").toBigInteger()
                : BigInteger.valueOf(this.usedStorage);
        if (data.hasKey("ApeironAvailableStorage", 7)) {
            this.apeiron$availableStorage = BigValueCodec
                    .readNBT(data, "availableStorage", "ApeironAvailableStorage")
                    .toBigInteger();
        } else if (this.apeiron$availableStorage == null) {
            this.apeiron$availableStorage = BigInteger.valueOf(this.availableStorage);
        }
        final NBTTagList serialized = data.getTagList("tasks", 10);
        int index = 0;
        for (final CraftingCPUCluster.TaskProgress progress : this.tasks.values()) {
            if (index >= serialized.tagCount()) break;
            final NBTTagCompound tag = serialized.getCompoundTagAt(index++);
            if (tag.hasKey("ApeironCraftingProgress", 7)) {
                ((BigTaskProgress) progress).setValueBig(new BigInteger(tag.getByteArray("ApeironCraftingProgress")));
            }
        }
    }

    @Unique
    private static BigInteger valueOf(final CraftingCPUCluster.TaskProgress progress) {
        return ((BigTaskProgress) progress).getValueBig();
    }
}
