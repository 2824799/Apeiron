package com.silvia.apeiron.mixin.ae.automation;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.automation.BigAdvancedLevelEmitterAccess;
import com.silvia.apeiron.ae.automation.BigAdvancedLevelEmitterContainerAccess;
import com.silvia.apeiron.ae.sync.BigIntegerSyncCodec;

import appeng.api.parts.IAdvancedLevelEmitter;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerAdvancedLevelEmitter;
import appeng.container.sync.handlers.ObjectSyncHandler;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Adds exact threshold channels beside AE's six legacy long channels. */
@Mixin(value = ContainerAdvancedLevelEmitter.class, remap = false)
public abstract class ContainerAdvancedLevelEmitterMixin implements BigAdvancedLevelEmitterContainerAccess {

    @Shadow
    @Final
    private IAdvancedLevelEmitter lvlEmitter;

    @Shadow
    @SideOnly(Side.CLIENT)
    private MEGuiTextField[] textFields;

    @Unique
    private ObjectSyncHandler<BigInteger>[] apeiron$levelBigSync;

    @Inject(
        method = "<init>(Lnet/minecraft/entity/player/InventoryPlayer;Lappeng/api/parts/IAdvancedLevelEmitter;)V",
        at = @At("TAIL"))
    @SuppressWarnings("unchecked")
    private void apeiron$registerBigSync(final CallbackInfo ci) {
        this.apeiron$levelBigSync = new ObjectSyncHandler[IAdvancedLevelEmitter.SLOT_COUNT];
        for (int slot = 0; slot < IAdvancedLevelEmitter.SLOT_COUNT; slot++) {
            final int index = slot;
            final BigInteger initial = apeiron$getEmitter() instanceof BigAdvancedLevelEmitterAccess access
                ? access.getReportingValueBig(index)
                : BigInteger.valueOf(apeiron$getEmitter().getReportingValue(index));
            this.apeiron$levelBigSync[index] = ((ContainerAdvancedLevelEmitter) (Object) this).getSyncManager()
                .root()
                .object("apeironAmountBig" + index, BigIntegerSyncCodec.INSTANCE, initial)
                .onClientChange((oldValue, newValue) -> {
                    if (this.textFields != null && this.textFields[index] != null && newValue != null) {
                        this.textFields[index].setText(newValue.toString());
                        this.textFields[index].setCursorPositionEnd();
                    }
                })
                .onServerChange((oldValue, newValue) -> {
                    if (newValue != null && apeiron$getEmitter() instanceof BigAdvancedLevelEmitterAccess access) {
                        access.setReportingValueBig(index, newValue.max(BigInteger.ZERO));
                    }
                });
        }
    }

    @Inject(method = { "detectAndSendChanges", "func_75142_b" }, at = @At("HEAD"))
    private void apeiron$syncBigValues(final CallbackInfo ci) {
        if (!Platform.isServer() || this.apeiron$levelBigSync == null) return;
        if (apeiron$getEmitter() instanceof BigAdvancedLevelEmitterAccess access) {
            for (int slot = 0; slot < IAdvancedLevelEmitter.SLOT_COUNT; slot++) {
                this.apeiron$levelBigSync[slot].set(access.getReportingValueBig(slot));
            }
        }
    }

    @Override
    public BigInteger getLevelBig(final int slot) {
        if (this.apeiron$levelBigSync != null && this.apeiron$levelBigSync[slot].get() != null) {
            return this.apeiron$levelBigSync[slot].get();
        }
        return apeiron$getEmitter() instanceof BigAdvancedLevelEmitterAccess access ? access.getReportingValueBig(slot)
            : BigInteger.valueOf(apeiron$getEmitter().getReportingValue(slot));
    }

    @Override
    public void setLevelBig(final int slot, final BigInteger value) {
        final BigInteger sanitized = value == null ? BigInteger.ZERO : value.max(BigInteger.ZERO);
        if (this.apeiron$levelBigSync != null) this.apeiron$levelBigSync[slot].set(sanitized);
        if (Platform.isServer() && apeiron$getEmitter() instanceof BigAdvancedLevelEmitterAccess access) {
            access.setReportingValueBig(slot, sanitized);
        }
    }

    @Unique
    private IAdvancedLevelEmitter apeiron$getEmitter() {
        return this.lvlEmitter;
    }
}
