package com.silvia.apeiron.mixin.ae.compat;

import java.math.BigDecimal;
import java.math.RoundingMode;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.compat.BigCondenserAccess;

import appeng.tile.misc.TileCondenser;

/** Retains condenser input power exactly when AE receives an oversized virtual stack. */
@Mixin(value = TileCondenser.class, remap = false)
public abstract class TileCondenserBigMixin implements BigCondenserAccess {

    @Shadow
    private double storedPower;

    @Shadow
    public abstract double getStorage();

    @Shadow
    public abstract double getRequiredPower();

    @Shadow
    private ItemStack getOutput() {
        throw new AssertionError();
    }

    @Shadow
    private boolean canAddOutput(ItemStack output) {
        throw new AssertionError();
    }

    @Shadow
    private void addOutput(ItemStack output) {}

    @Unique
    private BigDecimal apeiron$storedPowerBig;

    @Unique
    private BigDecimal apeiron$getPower() {
        if (this.apeiron$storedPowerBig == null) {
            this.apeiron$storedPowerBig = BigDecimal.valueOf(this.storedPower);
        }
        return this.apeiron$storedPowerBig;
    }

    @Override
    public void apeiron$addPowerBig(final BigDecimal rawPower) {
        BigDecimal value = this.apeiron$getPower()
            .add(rawPower);
        final BigDecimal capacity = BigDecimal.valueOf(this.getStorage());
        if (value.signum() < 0) value = BigDecimal.ZERO;
        if (capacity.signum() >= 0 && value.compareTo(capacity) > 0) value = capacity;

        final BigDecimal required = BigDecimal.valueOf(this.getRequiredPower());
        final ItemStack output = this.getOutput();
        while (required.signum() > 0 && value.compareTo(required) >= 0 && output != null) {
            if (!this.canAddOutput(output)) break;
            value = value.subtract(required);
            this.addOutput(output);
        }

        this.apeiron$storedPowerBig = value;
        this.storedPower = value.doubleValue();
    }

    @Inject(method = "addPower", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyPower(final double rawPower, final CallbackInfo ci) {
        this.apeiron$addPowerBig(BigDecimal.valueOf(rawPower));
        ci.cancel();
    }

    @Inject(method = "readFromNBT_TileCondenser", at = @At("TAIL"))
    private void apeiron$readExact(final NBTTagCompound data, final CallbackInfo ci) {
        if (data.hasKey("ApeironStoredPower", 8)) {
            try {
                this.apeiron$storedPowerBig = new BigDecimal(data.getString("ApeironStoredPower"));
                this.storedPower = this.apeiron$storedPowerBig.doubleValue();
            } catch (NumberFormatException ignored) {
                this.apeiron$storedPowerBig = BigDecimal.valueOf(this.storedPower);
            }
        }
    }

    @Inject(method = "writeToNBT_TileCondenser", at = @At("TAIL"))
    private void apeiron$writeExact(final NBTTagCompound data, final CallbackInfo ci) {
        data.setString(
            "ApeironStoredPower",
            this.apeiron$getPower()
                .setScale(12, RoundingMode.HALF_UP)
                .toPlainString());
    }
}
