package com.silvia.apeiron.mixin.ae.automation;

import java.math.BigInteger;
import java.util.Iterator;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigMEInventories;

import appeng.api.config.Actionable;
import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.me.GridAccessException;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.storage.TileIOPort;
import appeng.util.ConfigManager;
import appeng.util.IterationCounter;

/** Keeps IO Port transfer budgets and storage-cell moves exact once a stack exceeds long. */
@Mixin(value = TileIOPort.class, remap = false)
public abstract class TileIOPortMixin {

    @Shadow
    @Final
    private ConfigManager manager;

    @Shadow
    @Final
    private AppEngInternalInventory cells;

    @Shadow
    @Final
    private BaseActionSource mySrc;

    @Shadow
    private boolean pendingRedstonePulse;

    @Shadow
    private int[] moveQueue;

    @Shadow
    private boolean isEnabled() {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @Shadow
    private IMEInventory<?> getInv(ItemStack stack) {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @Shadow
    private boolean moveSlot(int slot) {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @Shadow
    private boolean shouldMove(IMEInventory<?> inventory, boolean sourceEmptyAfterTransfer, boolean destinationFull,
        boolean didWork, boolean moveOnEmptyWhileFilling, OperationMode operationMode, FullnessMode fullnessMode) {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @Shadow
    public abstract int getInstalledUpgrades(Upgrades upgrade);

    private static final class ApeironTransferResult {

        private final BigInteger itemsLeftToMove;
        private final boolean sourceEmpty;
        private final boolean destinationFull;

        private ApeironTransferResult(final BigInteger itemsLeftToMove, final boolean sourceEmpty,
            final boolean destinationFull) {
            this.itemsLeftToMove = itemsLeftToMove;
            this.sourceEmpty = sourceEmpty;
            this.destinationFull = destinationFull;
        }
    }

    @Unique
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ApeironTransferResult apeiron$transferContents(final IEnergySource energy, final IMEInventory src,
        final IMEInventory destination, BigInteger itemsToMove) {
        final Iterator<? extends IAEStack<?>> iterator;
        if (src instanceof IMEMonitor monitor) {
            iterator = monitor.getAvailableItemsWithPriority(IterationCounter.fetchNewId())
                .getItems(true)
                .distinct()
                .iterator();
        } else {
            iterator = src.getAvailableItems(
                src.getStackType()
                    .createList(),
                IterationCounter.fetchNewId())
                .iterator();
        }

        boolean didStuff;
        boolean sourceHasRemainingItems = false;
        boolean destinationFull = false;
        do {
            didStuff = false;
            while (iterator.hasNext()) {
                final IAEStack<?> availableStack = iterator.next();
                final BigInteger availableBefore = BigAEStackValues.get(availableStack);
                if (availableBefore.signum() <= 0) continue;

                final BigInteger requested = availableBefore.min(itemsToMove);
                final IAEStack<?> extractRequest = BigAEStackValues.copyWithSize(availableStack, requested);
                final IAEStack<?> simulatedRemainder = appeng.util.Platform
                    .poweredInsert(energy, destination, extractRequest, this.mySrc, Actionable.SIMULATE);
                BigInteger possible = requested
                    .subtract(simulatedRemainder == null ? BigInteger.ZERO : BigAEStackValues.get(simulatedRemainder));

                if (possible.signum() > 0) {
                    final IAEStack<?> actualRequest = BigAEStackValues.copyWithSize(extractRequest, possible);
                    final IAEStack<?> extracted = BigMEInventories
                        .extractItemsBig(src, actualRequest, Actionable.MODULATE, this.mySrc);
                    if (extracted != null) {
                        possible = BigAEStackValues.get(extracted);
                        final IAEStack<?> failed = appeng.util.Platform.poweredInsert(
                            energy,
                            destination,
                            extracted.copy()
                                .setCraftable(false),
                            this.mySrc);
                        if (failed != null) {
                            final BigInteger failedAmount = BigAEStackValues.get(failed);
                            possible = possible.subtract(failedAmount)
                                .max(BigInteger.ZERO);
                            BigMEInventories.injectItemsBig(src, failed, Actionable.MODULATE, this.mySrc);
                            sourceHasRemainingItems = true;
                        }

                        if (possible.signum() > 0) {
                            itemsToMove = itemsToMove.subtract(possible);
                            didStuff = true;
                            if (availableBefore.compareTo(possible) > 0) sourceHasRemainingItems = true;
                        }
                        break;
                    }
                } else {
                    sourceHasRemainingItems = true;
                }
            }
        } while (itemsToMove.signum() > 0 && didStuff);

        if (itemsToMove.signum() > 0 && !didStuff) destinationFull = true;
        return new ApeironTransferResult(itemsToMove, !sourceHasRemainingItems && !iterator.hasNext(), destinationFull);
    }

    @Unique
    private static BigInteger apeiron$ceilDiv(final BigInteger value, final long divisor) {
        final BigInteger d = BigInteger.valueOf(divisor);
        return value.add(d)
            .subtract(BigInteger.ONE)
            .divide(d);
    }

    @Unique
    private TickRateModulation apeiron$tickingRequest(final IGridNode node) {
        if (!((TileIOPort) (Object) this).getProxy()
            .isActive()) return TickRateModulation.IDLE;

        final RedstoneMode redstoneMode = (RedstoneMode) this.manager.getSetting(Settings.REDSTONE_CONTROLLED);
        if (redstoneMode == RedstoneMode.SIGNAL_PULSE && !this.pendingRedstonePulse) {
            return TickRateModulation.IDLE;
        }

        BigInteger amountToMove = BigInteger.valueOf(256L);
        switch (this.getInstalledUpgrades(Upgrades.SPEED)) {
            case 1 -> amountToMove = amountToMove.multiply(BigInteger.valueOf(2));
            case 2 -> amountToMove = amountToMove.multiply(BigInteger.valueOf(4));
            case 3 -> amountToMove = amountToMove.multiply(BigInteger.valueOf(8));
        }
        switch (this.getInstalledUpgrades(Upgrades.SUPERSPEED)) {
            case 1 -> amountToMove = amountToMove.multiply(BigInteger.valueOf(16));
            case 2 -> amountToMove = amountToMove.multiply(BigInteger.valueOf(128));
            case 3 -> amountToMove = amountToMove.multiply(BigInteger.valueOf(1024));
        }
        switch (this.getInstalledUpgrades(Upgrades.SUPERLUMINALSPEED)) {
            case 1 -> amountToMove = amountToMove.multiply(BigInteger.valueOf(131_072L));
            case 2 -> amountToMove = amountToMove.multiply(BigInteger.valueOf(8_388_608L));
            case 3 -> amountToMove = amountToMove.multiply(BigInteger.valueOf(536_870_912L));
        }

        final FullnessMode fullnessMode = (FullnessMode) this.manager.getSetting(Settings.FULLNESS_MODE);
        final OperationMode operationMode = (OperationMode) this.manager.getSetting(Settings.OPERATION_MODE);
        final boolean moveOnEmptyWhileFilling = operationMode == OperationMode.FILL
            && fullnessMode == FullnessMode.EMPTY;

        try {
            final IEnergySource energy = ((TileIOPort) (Object) this).getProxy()
                .getEnergy();
            for (int slot = 0; slot < 6; slot++) {
                final ItemStack cell = this.cells.getStackInSlot(slot);
                if (cell == null) continue;

                if (fullnessMode != FullnessMode.HALF && this.moveQueue[slot] == 1) {
                    this.moveQueue[slot] = !this.moveSlot(slot) ? 1 : 0;
                    continue;
                }

                if (amountToMove.signum() <= 0) return TickRateModulation.URGENT;
                final IMEInventory<?> inventory = this.getInv(cell);
                if (inventory == null) continue;

                final IMEMonitor<?> monitor = ((TileIOPort) (Object) this).getProxy()
                    .getStorage()
                    .getMEMonitor(inventory.getStackType());
                if (monitor == null) continue;

                final long amountPerUnit = inventory.getStackType()
                    .getAmountPerUnit();
                final BigInteger transferBudget = amountToMove.multiply(BigInteger.valueOf(amountPerUnit));
                final ApeironTransferResult result = operationMode == OperationMode.EMPTY
                    ? this.apeiron$transferContents(energy, inventory, monitor, transferBudget)
                    : this.apeiron$transferContents(energy, monitor, inventory, transferBudget);
                amountToMove = apeiron$ceilDiv(result.itemsLeftToMove, amountPerUnit);
                final boolean didWork = result.itemsLeftToMove.compareTo(transferBudget) != 0;

                if (amountToMove.signum() > 0 || moveOnEmptyWhileFilling) {
                    if (this.shouldMove(
                        inventory,
                        result.sourceEmpty,
                        result.destinationFull,
                        didWork,
                        moveOnEmptyWhileFilling,
                        operationMode,
                        fullnessMode)) {
                        this.moveQueue[slot] = !this.moveSlot(slot) ? 1 : 0;
                        this.pendingRedstonePulse = false;
                        if (this.moveQueue[slot] == 1) return TickRateModulation.IDLE;
                    } else if (fullnessMode != FullnessMode.HALF) {
                        for (int next = slot + 1; next < 6; next++) {
                            if (this.moveQueue[next] == 1) {
                                this.moveQueue[next] = !this.moveSlot(next) ? 1 : 0;
                                this.pendingRedstonePulse = false;
                                if (this.moveQueue[next] == 1) return TickRateModulation.IDLE;
                                break;
                            }
                        }
                    }
                }
                return TickRateModulation.URGENT;
            }
        } catch (GridAccessException ignored) {
            return TickRateModulation.IDLE;
        }
        return TickRateModulation.SLEEP;
    }

    @Inject(method = "tickingRequest", at = @At("HEAD"), cancellable = true)
    private void apeiron$ticking(final IGridNode node, final int ticksSinceLastCall,
        final CallbackInfoReturnable<TickRateModulation> cir) {
        cir.setReturnValue(this.apeiron$tickingRequest(node));
    }
}
