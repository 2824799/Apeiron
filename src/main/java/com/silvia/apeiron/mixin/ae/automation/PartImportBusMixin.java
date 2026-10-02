package com.silvia.apeiron.mixin.ae.automation;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigInventoryAdaptors;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Upgrades;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.parts.automation.PartBaseImportBus;
import appeng.parts.automation.PartImportBus;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.IInventoryDestination;
import appeng.util.item.AEItemStack;

/** Preflights power before physical removal and returns every rejected item to the source. */
@Mixin(value = PartImportBus.class, remap = false)
public abstract class PartImportBusMixin extends PartBaseImportBus<IAEItemStack> {

    protected PartImportBusMixin(final ItemStack stack) {
        super(stack);
    }

    @Inject(
        method = "importStuff(Ljava/lang/Object;Lappeng/api/storage/data/IAEItemStack;Lappeng/api/storage/IMEMonitor;Lappeng/api/networking/energy/IEnergySource;Lappeng/api/config/FuzzyMode;)Z",
        at = @At("HEAD"),
        cancellable = true)
    private void apeiron$importExact(final Object target, final IAEItemStack filter,
        final IMEMonitor<IAEItemStack> monitor, final IEnergySource energy, final FuzzyMode fuzzyMode,
        final CallbackInfoReturnable<Boolean> cir) {
        if (!(target instanceof InventoryAdaptor) || monitor == null) {
            cir.setReturnValue(true);
            return;
        }
        final InventoryAdaptor adaptor = (InventoryAdaptor) target;
        this.destination = monitor;
        final IInventoryDestination destinationFilter = (IInventoryDestination) (Object) this;
        final boolean fuzzy = this.getInstalledUpgrades(Upgrades.FUZZY) > 0;
        final ItemStack template = filter == null ? null : filter.getItemStack();
        final int requested = Math.min(this.itemToSend, 64);
        if (requested <= 0) {
            cir.setReturnValue(true);
            return;
        }
        final ItemStack available = fuzzy
            ? adaptor.simulateSimilarRemove(requested, template, fuzzyMode, destinationFilter)
            : adaptor.simulateRemove(requested, template, destinationFilter);
        if (available == null || available.stackSize <= 0) {
            cir.setReturnValue(true);
            return;
        }
        final IAEItemStack offered = AEItemStack.create(available);
        final IAEItemStack simulatedLeftover = Platform
            .poweredInsert(energy, monitor, offered, this.mySrc, Actionable.SIMULATE);
        final BigInteger accepted = BigAEStackValues.get(offered)
            .subtract(BigAEStackValues.get(simulatedLeftover));
        if (accepted.signum() <= 0) {
            cir.setReturnValue(true);
            return;
        }
        final int amount = accepted.intValueExact();
        final ItemStack removed = fuzzy ? adaptor.removeSimilarItems(amount, available, fuzzyMode, destinationFilter)
            : adaptor.removeItems(amount, available, destinationFilter);
        if (removed == null || removed.stackSize <= 0) {
            cir.setReturnValue(true);
            return;
        }
        final IAEItemStack actual = AEItemStack.create(removed);
        final IAEItemStack leftover = Platform.poweredInsert(energy, monitor, actual, this.mySrc);
        final BigInteger inserted = BigAEStackValues.get(actual)
            .subtract(BigAEStackValues.get(leftover));
        if (leftover != null) {
            final appeng.api.storage.data.IAEStack<?> remaining = BigInventoryAdaptors
                .addStackBig(adaptor, leftover, appeng.api.config.InsertionMode.DEFAULT, false);
            if (remaining != null) Platform.spawnDrops(
                this.getHost()
                    .getTile()
                    .getWorldObj(),
                this.getHost()
                    .getTile().xCoord,
                this.getHost()
                    .getTile().yCoord,
                this.getHost()
                    .getTile().zCoord,
                java.util.Collections.singletonList(((IAEItemStack) remaining).getItemStack()));
        }
        this.itemToSend -= inserted.intValueExact();
        this.worked |= inserted.signum() > 0;
        this.lastItemChecked = actual;
        cir.setReturnValue(leftover != null || inserted.signum() == 0);
    }

}
