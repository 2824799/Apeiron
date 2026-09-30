package com.silvia.apeiron.mixin.ae.automation;

import java.math.BigInteger;
import java.util.Collection;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.automation.BigLevelEmitterAccess;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.me.GridAccessException;
import appeng.parts.automation.PartLevelEmitter;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.AEStackTypeFilter;
import appeng.util.Platform;
import net.minecraft.nbt.NBTTagCompound;

/** Makes level emitters compare exact network counts and persist exact thresholds. */
@Mixin(value = PartLevelEmitter.class, remap = false)
public abstract class PartLevelEmitterMixin implements BigLevelEmitterAccess {

    @Shadow
    @Final
    private IAEStackInventory config;

    @Shadow
    @Final
    private AEStackTypeFilter typeFilters;

    @Shadow
    private long lastReportedValue;

    @Shadow
    private long reportingValue;

    @Shadow
    private void updateState() {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @Unique
    private BigInteger apeiron$lastReported;

    @Unique
    private BigInteger apeiron$reporting;

    @Override
    public BigInteger getReportingValueBig() {
        return apeiron$reporting == null ? BigInteger.valueOf(reportingValue) : apeiron$reporting;
    }

    @Override
    public void setReportingValueBig(final BigInteger value) {
        apeiron$reporting = AdaptiveInteger.fitsLong(value) ? null : value;
        reportingValue = BigAEStackValues.saturatedLong(value);
        ((PartLevelEmitter) (Object) this).getHost().markForSave();
        ((PartLevelEmitter) (Object) this).getConfigManager().getSetting(Settings.LEVEL_TYPE);
        updateState();
    }

    @Inject(method = "setReportingValue(J)V", at = @At("HEAD"))
    private void apeiron$clearExactThreshold(final long value, final CallbackInfo ci) {
        apeiron$reporting = null;
    }

    @Inject(method = "isLevelEmitterOn", at = @At("HEAD"), cancellable = true)
    private void apeiron$compareExact(final CallbackInfoReturnable<Boolean> cir) {
        if (Platform.isClient() || (apeiron$reporting == null && apeiron$lastReported == null)) return;
        final BigInteger threshold = getReportingValueBig();
        final BigInteger current = apeiron$lastReported == null ? BigInteger.valueOf(lastReportedValue)
            : apeiron$lastReported;
        final boolean flip = ((PartLevelEmitter) (Object) this).getConfigManager()
            .getSetting(Settings.REDSTONE_EMITTER) == appeng.api.config.RedstoneMode.LOW_SIGNAL;
        cir.setReturnValue(flip == current.compareTo(threshold.add(BigInteger.ONE)) < 0);
    }

    @Inject(method = "updateReportingValue", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void apeiron$updateExact(final IMEMonitor monitor, final CallbackInfo ci) {
        final PartLevelEmitter self = (PartLevelEmitter) (Object) this;
        final IAEStack<?> configured = config.getAEStackInSlot(0);
        BigInteger value = BigInteger.ZERO;
        try {
            if (configured == null || monitor == null) {
                for (IAEStackType<?> type : typeFilters.getEnabledTypes()) {
                    final IMEMonitor<?> valid = self.getProxy().getStorage().getMEMonitor(type);
                    if (valid == null) continue;
                    for (IAEStack<?> stack : valid.getStorageList()) value = value.add(BigAEStackValues.get(stack));
                }
            } else if (configured.getStackType() != monitor.getStackType()) {
                return;
            } else if (configured instanceof IAEItemStack
                && self.getInstalledUpgrades(Upgrades.FUZZY) > 0) {
                final FuzzyMode fuzzy = (FuzzyMode) self.getConfigManager().getSetting(Settings.FUZZY_MODE);
                final Collection<IAEItemStack> matches = ((IMEMonitor<IAEItemStack>) monitor).getStorageList()
                    .findFuzzy((IAEItemStack) configured, fuzzy);
                for (IAEItemStack stack : matches) value = value.add(BigAEStackValues.get(stack));
            } else {
                final IAEStack<?> found = monitor.getStorageList().findPrecise(configured);
                if (found != null) value = BigAEStackValues.get(found);
            }
        } catch (final GridAccessException e) {
            return;
        }

        apeiron$lastReported = AdaptiveInteger.fitsLong(value) ? null : value;
        lastReportedValue = BigAEStackValues.saturatedLong(value);
        updateState();
        ci.cancel();
    }

    @Inject(method = "onStackChange", at = @At("HEAD"), cancellable = true)
    private void apeiron$stackChangeExact(final IItemList list, final IAEStack fullStack, final IAEStack diffStack,
        final BaseActionSource source, final appeng.api.storage.StorageChannel channel, final CallbackInfo ci) {
        final IAEStack<?> configured = config.getAEStackInSlot(0);
        if (configured == null || !fullStack.equals(configured) || !BigAEStackValues.isBig(fullStack)) return;
        apeiron$lastReported = BigAEStackValues.get(fullStack);
        lastReportedValue = BigAEStackValues.saturatedLong(apeiron$lastReported);
        updateState();
        ci.cancel();
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void apeiron$readExact(final NBTTagCompound data, final CallbackInfo ci) {
        AdaptiveInteger last = BigValueCodec.readNBT(data, "lastReportedValue", "ApeironLastReportedValue");
        AdaptiveInteger threshold = BigValueCodec.readNBT(data, "reportingValue", "ApeironReportingValue");
        apeiron$lastReported = last.isBig() ? last.toBigInteger() : null;
        apeiron$reporting = threshold.isBig() ? threshold.toBigInteger() : null;
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void apeiron$writeExact(final NBTTagCompound data, final CallbackInfo ci) {
        BigValueCodec.writeNBT(data, "lastReportedValue", "ApeironLastReportedValue",
            new AdaptiveInteger(apeiron$lastReported == null ? BigInteger.valueOf(lastReportedValue) : apeiron$lastReported));
        BigValueCodec.writeNBT(data, "reportingValue", "ApeironReportingValue",
            new AdaptiveInteger(getReportingValueBig()));
    }
}
