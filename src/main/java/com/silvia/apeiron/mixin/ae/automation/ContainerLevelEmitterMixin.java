package com.silvia.apeiron.mixin.ae.automation;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.automation.BigLevelEmitterAccess;
import com.silvia.apeiron.ae.automation.BigLevelEmitterContainerAccess;
import com.silvia.apeiron.ae.sync.BigIntegerSyncCodec;

import appeng.api.parts.ILevelEmitter;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerLevelEmitter;
import appeng.container.sync.handlers.ObjectSyncHandler;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Adds an exact threshold channel beside AE's legacy long channel. */
@Mixin(value = ContainerLevelEmitter.class, remap = false)
public abstract class ContainerLevelEmitterMixin implements BigLevelEmitterContainerAccess {

    @Shadow
    @Final
    private ILevelEmitter lvlEmitter;

    @Shadow
    @SideOnly(Side.CLIENT)
    private MEGuiTextField textField;

    @Unique
    private ObjectSyncHandler<BigInteger> apeiron$levelBigSync;

    @Inject(
        method = "<init>(Lnet/minecraft/entity/player/InventoryPlayer;Lappeng/api/parts/ILevelEmitter;)V",
        at = @At("TAIL"))
    private void apeiron$registerBigSync(final CallbackInfo ci) {
        final BigInteger initial = BigInteger.valueOf(apeiron$getEmitter().getReportingValue());
        this.apeiron$levelBigSync = ((ContainerLevelEmitter) (Object) this).getSyncManager()
            .root()
            .object("apeironEmitterValueBig", BigIntegerSyncCodec.INSTANCE, initial)
            .onClientChange((oldValue, newValue) -> {
                if (this.textField != null && newValue != null) {
                    this.textField.setText(newValue.toString());
                    this.textField.setCursorPositionEnd();
                }
            })
            .onServerChange((oldValue, newValue) -> {
                if (newValue != null && apeiron$getEmitter() instanceof BigLevelEmitterAccess access) {
                    access.setReportingValueBig(newValue.max(BigInteger.ZERO));
                }
            });
    }

    @Inject(method = { "detectAndSendChanges", "func_75142_b" }, at = @At("HEAD"))
    private void apeiron$syncBigValue(final CallbackInfo ci) {
        if (Platform.isServer() && this.apeiron$levelBigSync != null
            && apeiron$getEmitter() instanceof BigLevelEmitterAccess access) {
            this.apeiron$levelBigSync.set(access.getReportingValueBig());
        }
    }

    @Override
    public BigInteger getLevelBig() {
        if (this.apeiron$levelBigSync != null && this.apeiron$levelBigSync.get() != null) {
            return this.apeiron$levelBigSync.get();
        }
        return apeiron$getEmitter() instanceof BigLevelEmitterAccess access ? access.getReportingValueBig()
            : BigInteger.valueOf(apeiron$getEmitter().getReportingValue());
    }

    @Override
    public void setLevelBig(final BigInteger value) {
        final BigInteger sanitized = value == null ? BigInteger.ZERO : value.max(BigInteger.ZERO);
        if (this.apeiron$levelBigSync != null) {
            this.apeiron$levelBigSync.set(sanitized);
        }
        if (Platform.isServer() && apeiron$getEmitter() instanceof BigLevelEmitterAccess access) {
            access.setReportingValueBig(sanitized);
        }
    }

    @Unique
    private ILevelEmitter apeiron$getEmitter() {
        return this.lvlEmitter;
    }
}
