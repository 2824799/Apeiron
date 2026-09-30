package com.silvia.apeiron.mixin.ae.automation;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.automation.BigAdvancedLevelEmitterAccess;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.config.BooleanOperation;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.parts.IAdvancedLevelEmitter;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.GridAccessException;
import appeng.parts.automation.PartAdvancedLevelEmitter;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

/** Makes the six-slot level emitter keep exact thresholds and exact observed counts. */
@Mixin(value = PartAdvancedLevelEmitter.class, remap = false)
public abstract class PartAdvancedLevelEmitterMixin implements BigAdvancedLevelEmitterAccess {

    @Shadow
    @Final
    private IAEStackInventory config;

    @Shadow
    @Final
    private boolean[] slotActive;

    @Shadow
    @Final
    private boolean[] slotInverted;

    @Shadow
    @Final
    private long[] amount;

    @Shadow
    @Final
    private long[] lastReportedValue;

    @Shadow
    private void updateState() {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @Unique
    private final BigInteger[] apeiron$amountBig = new BigInteger[IAdvancedLevelEmitter.SLOT_COUNT];

    @Unique
    private final BigInteger[] apeiron$lastReportedBig = new BigInteger[IAdvancedLevelEmitter.SLOT_COUNT];

    @Override
    public BigInteger getReportingValueBig(final int slot) {
        return apeiron$amountBig[slot] == null ? BigInteger.valueOf(amount[slot]) : apeiron$amountBig[slot];
    }

    @Override
    public void setReportingValueBig(final int slot, final BigInteger value) {
        apeiron$amountBig[slot] = AdaptiveInteger.fitsLong(value) ? null : value;
        amount[slot] = BigAEStackValues.saturatedLong(value);
        ((PartAdvancedLevelEmitter) (Object) this).getHost().markForSave();
        updateState();
    }

    @Inject(method = "setReportingValue(IJ)V", at = @At("HEAD"), remap = false)
    private void apeiron$clearExactThreshold(final int slot, final long value, final CallbackInfo ci) {
        apeiron$amountBig[slot] = null;
    }

    @Inject(method = "isLevelEmitterOn", at = @At("HEAD"), cancellable = true)
    private void apeiron$compareExact(final CallbackInfoReturnable<Boolean> cir) {
        if (Platform.isClient()) return;
        boolean hasExact = false;
        for (int slot = 0; slot < IAdvancedLevelEmitter.SLOT_COUNT; slot++) {
            hasExact |= apeiron$amountBig[slot] != null || apeiron$lastReportedBig[slot] != null;
        }
        if (!hasExact) return;

        final BooleanOperation mode = ((PartAdvancedLevelEmitter) (Object) this).getLogicMode();
        boolean result = mode == BooleanOperation.AND;
        boolean sawActive = false;
        for (int slot = 0; slot < IAdvancedLevelEmitter.SLOT_COUNT; slot++) {
            if (!slotActive[slot]) continue;
            final IAEStack<?> stack = config.getAEStackInSlot(slot);
            if (stack == null) continue;
            sawActive = true;
            BigInteger current = apeiron$lastReportedBig[slot] == null
                ? BigInteger.valueOf(lastReportedValue[slot])
                : apeiron$lastReportedBig[slot];
            BigInteger threshold = getReportingValueBig(slot);
            boolean slotState = slotInverted[slot]
                ? current.compareTo(threshold) < 0
                : current.compareTo(threshold) >= 0;
            result = mode == BooleanOperation.AND ? result && slotState : result || slotState;
        }
        cir.setReturnValue(sawActive && result);
    }

    @Inject(method = "updateReportingValueForSlot", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void apeiron$updateExact(final int slot, final CallbackInfo ci) {
        final IAEStack<?> configured = config.getAEStackInSlot(slot);
        if (configured == null) {
            apeiron$setLast(slot, BigInteger.ZERO);
            ci.cancel();
            return;
        }
        try {
            final IMEMonitor monitor = ((PartAdvancedLevelEmitter) (Object) this).getProxy().getStorage()
                .getMEMonitor(configured.getStackType());
            if (monitor == null) {
                apeiron$setLast(slot, BigInteger.ZERO);
            } else {
                final IAEStack<?> found = monitor.getStorageList().findPrecise(configured);
                apeiron$setLast(slot, found == null ? BigInteger.ZERO : BigAEStackValues.get(found));
            }
        } catch (final GridAccessException e) {
            return;
        }
        ci.cancel();
    }

    @Unique
    private void apeiron$setLast(final int slot, final BigInteger value) {
        apeiron$lastReportedBig[slot] = AdaptiveInteger.fitsLong(value) ? null : value;
        lastReportedValue[slot] = BigAEStackValues.saturatedLong(value);
    }

    @Inject(method = "onStackChange", at = @At("HEAD"), cancellable = true)
    private void apeiron$stackChangeExact(final IItemList<?> list, final IAEStack fullStack,
        final IAEStack diffStack, final BaseActionSource source, final StorageChannel channel, final CallbackInfo ci) {
        if (!BigAEStackValues.isBig(fullStack)) return;
        boolean changed = false;
        for (int slot = 0; slot < IAdvancedLevelEmitter.SLOT_COUNT; slot++) {
            final IAEStack<?> configured = config.getAEStackInSlot(slot);
            if (configured != null && fullStack.equals(configured)) {
                apeiron$setLast(slot, BigAEStackValues.get(fullStack));
                changed = true;
            }
        }
        if (changed) {
            updateState();
            ci.cancel();
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void apeiron$readExact(final NBTTagCompound data, final CallbackInfo ci) {
        final NBTTagList list = data.getTagList("slots", NBT.TAG_COMPOUND);
        for (int slot = 0; slot < Math.min(IAdvancedLevelEmitter.SLOT_COUNT, list.tagCount()); slot++) {
            final NBTTagCompound tag = list.getCompoundTagAt(slot);
            AdaptiveInteger threshold = BigValueCodec.readNBT(tag, "amount", "ApeironAmount");
            AdaptiveInteger observed = BigValueCodec.readNBT(tag, "lastReportedValue", "ApeironLastReportedValue");
            apeiron$amountBig[slot] = threshold.isBig() ? threshold.toBigInteger() : null;
            apeiron$lastReportedBig[slot] = observed.isBig() ? observed.toBigInteger() : null;
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void apeiron$writeExact(final NBTTagCompound data, final CallbackInfo ci) {
        final NBTTagList list = data.getTagList("slots", NBT.TAG_COMPOUND);
        final NBTTagList exactList = new NBTTagList();
        for (int slot = 0; slot < Math.min(IAdvancedLevelEmitter.SLOT_COUNT, list.tagCount()); slot++) {
            final NBTTagCompound tag = list.getCompoundTagAt(slot);
            BigValueCodec.writeNBT(tag, "amount", "ApeironAmount", new AdaptiveInteger(getReportingValueBig(slot)));
            BigValueCodec.writeNBT(tag, "lastReportedValue", "ApeironLastReportedValue",
                new AdaptiveInteger(apeiron$lastReportedBig[slot] == null
                    ? BigInteger.valueOf(lastReportedValue[slot])
                    : apeiron$lastReportedBig[slot]));
            exactList.appendTag(tag);
        }
        data.setTag("slots", exactList);
    }
}
