package com.silvia.apeiron.mixin.ae.terminal.core;

import java.math.BigInteger;

import net.minecraft.entity.player.InventoryPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.storage.BigStorageGrid;
import com.silvia.apeiron.ae.sync.BigIntegerSyncCodec;
import com.silvia.apeiron.ae.sync.BigSyncRegistrarAccess;
import com.silvia.apeiron.ae.terminal.BigNetworkStatus;

import appeng.api.implementations.guiobjects.INetworkTool;
import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.container.implementations.ContainerNetworkStatus;
import appeng.container.sync.handlers.ObjectSyncHandler;

/** Synchronizes Network Status capacity counters with their exact BigInteger values. */
@Mixin(value = ContainerNetworkStatus.class, remap = false)
public abstract class ContainerNetworkStatusBigMixin implements BigNetworkStatus {

    @Shadow
    private IGrid network;

    @Unique
    private ObjectSyncHandler<BigInteger> apeiron$itemBytesTotal;
    @Unique
    private ObjectSyncHandler<BigInteger> apeiron$itemBytesUsed;
    @Unique
    private ObjectSyncHandler<BigInteger> apeiron$itemTypesTotal;
    @Unique
    private ObjectSyncHandler<BigInteger> apeiron$itemTypesUsed;
    @Unique
    private ObjectSyncHandler<BigInteger> apeiron$itemCellCount;
    @Unique
    private ObjectSyncHandler<BigInteger> apeiron$fluidBytesTotal;
    @Unique
    private ObjectSyncHandler<BigInteger> apeiron$fluidBytesUsed;
    @Unique
    private ObjectSyncHandler<BigInteger> apeiron$fluidTypesTotal;
    @Unique
    private ObjectSyncHandler<BigInteger> apeiron$fluidTypesUsed;
    @Unique
    private ObjectSyncHandler<BigInteger> apeiron$fluidCellCount;
    @Unique
    private ObjectSyncHandler<BigInteger> apeiron$essentiaBytesTotal;
    @Unique
    private ObjectSyncHandler<BigInteger> apeiron$essentiaBytesUsed;
    @Unique
    private ObjectSyncHandler<BigInteger> apeiron$essentiaTypesTotal;
    @Unique
    private ObjectSyncHandler<BigInteger> apeiron$essentiaTypesUsed;
    @Unique
    private ObjectSyncHandler<BigInteger> apeiron$essentiaCellCount;

    @Inject(
        method = "<init>(Lnet/minecraft/entity/player/InventoryPlayer;Lappeng/api/implementations/guiobjects/INetworkTool;)V",
        at = @At("TAIL"))
    private void apeiron$registerExactSync(final InventoryPlayer inventory, final INetworkTool tool,
        final CallbackInfo ci) {
        final BigSyncRegistrarAccess registrar = (BigSyncRegistrarAccess) (Object) this;
        this.apeiron$itemBytesTotal = registrar.apeiron$syncRegistrar()
            .objectS2C("apeironItemBytesTotal", BigIntegerSyncCodec.INSTANCE, BigInteger.ZERO);
        this.apeiron$itemBytesUsed = registrar.apeiron$syncRegistrar()
            .objectS2C("apeironItemBytesUsed", BigIntegerSyncCodec.INSTANCE, BigInteger.ZERO);
        this.apeiron$itemTypesTotal = registrar.apeiron$syncRegistrar()
            .objectS2C("apeironItemTypesTotal", BigIntegerSyncCodec.INSTANCE, BigInteger.ZERO);
        this.apeiron$itemTypesUsed = registrar.apeiron$syncRegistrar()
            .objectS2C("apeironItemTypesUsed", BigIntegerSyncCodec.INSTANCE, BigInteger.ZERO);
        this.apeiron$itemCellCount = registrar.apeiron$syncRegistrar()
            .objectS2C("apeironItemCellCount", BigIntegerSyncCodec.INSTANCE, BigInteger.ZERO);
        this.apeiron$fluidBytesTotal = registrar.apeiron$syncRegistrar()
            .objectS2C("apeironFluidBytesTotal", BigIntegerSyncCodec.INSTANCE, BigInteger.ZERO);
        this.apeiron$fluidBytesUsed = registrar.apeiron$syncRegistrar()
            .objectS2C("apeironFluidBytesUsed", BigIntegerSyncCodec.INSTANCE, BigInteger.ZERO);
        this.apeiron$fluidTypesTotal = registrar.apeiron$syncRegistrar()
            .objectS2C("apeironFluidTypesTotal", BigIntegerSyncCodec.INSTANCE, BigInteger.ZERO);
        this.apeiron$fluidTypesUsed = registrar.apeiron$syncRegistrar()
            .objectS2C("apeironFluidTypesUsed", BigIntegerSyncCodec.INSTANCE, BigInteger.ZERO);
        this.apeiron$fluidCellCount = registrar.apeiron$syncRegistrar()
            .objectS2C("apeironFluidCellCount", BigIntegerSyncCodec.INSTANCE, BigInteger.ZERO);
        this.apeiron$essentiaBytesTotal = registrar.apeiron$syncRegistrar()
            .objectS2C("apeironEssentiaBytesTotal", BigIntegerSyncCodec.INSTANCE, BigInteger.ZERO);
        this.apeiron$essentiaBytesUsed = registrar.apeiron$syncRegistrar()
            .objectS2C("apeironEssentiaBytesUsed", BigIntegerSyncCodec.INSTANCE, BigInteger.ZERO);
        this.apeiron$essentiaTypesTotal = registrar.apeiron$syncRegistrar()
            .objectS2C("apeironEssentiaTypesTotal", BigIntegerSyncCodec.INSTANCE, BigInteger.ZERO);
        this.apeiron$essentiaTypesUsed = registrar.apeiron$syncRegistrar()
            .objectS2C("apeironEssentiaTypesUsed", BigIntegerSyncCodec.INSTANCE, BigInteger.ZERO);
        this.apeiron$essentiaCellCount = registrar.apeiron$syncRegistrar()
            .objectS2C("apeironEssentiaCellCount", BigIntegerSyncCodec.INSTANCE, BigInteger.ZERO);
    }

    @Inject(method = { "detectAndSendChanges", "func_75142_b" }, at = @At("HEAD"))
    private void apeiron$sendExactSync(final CallbackInfo ci) {
        if (this.network == null || this.apeiron$itemBytesTotal == null) return;
        final Object storage = this.network.getCache(IStorageGrid.class);
        if (!(storage instanceof BigStorageGrid)) return;
        final BigStorageGrid exact = (BigStorageGrid) storage;
        this.apeiron$itemBytesTotal.set(exact.getItemBytesTotalBig());
        this.apeiron$itemBytesUsed.set(exact.getItemBytesUsedBig());
        this.apeiron$itemTypesTotal.set(exact.getItemTypesTotalBig());
        this.apeiron$itemTypesUsed.set(exact.getItemTypesUsedBig());
        this.apeiron$itemCellCount.set(exact.getItemCellCountBig());
        this.apeiron$fluidBytesTotal.set(exact.getFluidBytesTotalBig());
        this.apeiron$fluidBytesUsed.set(exact.getFluidBytesUsedBig());
        this.apeiron$fluidTypesTotal.set(exact.getFluidTypesTotalBig());
        this.apeiron$fluidTypesUsed.set(exact.getFluidTypesUsedBig());
        this.apeiron$fluidCellCount.set(exact.getFluidCellCountBig());
        this.apeiron$essentiaBytesTotal.set(exact.getEssentiaBytesTotalBig());
        this.apeiron$essentiaBytesUsed.set(exact.getEssentiaBytesUsedBig());
        this.apeiron$essentiaTypesTotal.set(exact.getEssentiaTypesTotalBig());
        this.apeiron$essentiaTypesUsed.set(exact.getEssentiaTypesUsedBig());
        this.apeiron$essentiaCellCount.set(exact.getEssentiaCellCountBig());
    }

    @Unique
    private BigStorageGrid apeiron$gridExact() {
        if (this.network == null) return null;
        Object storage = this.network.getCache(IStorageGrid.class);
        return storage instanceof BigStorageGrid ? (BigStorageGrid) storage : null;
    }

    @Unique
    private BigInteger apeiron$value(final ObjectSyncHandler<BigInteger> handler, BigInteger fallback) {
        return handler == null ? fallback : handler.get();
    }

    @Override
    public BigInteger getItemBytesTotalBig() {
        return apeiron$value(
            apeiron$itemBytesTotal,
            apeiron$gridExact() == null ? BigInteger.ZERO : apeiron$gridExact().getItemBytesTotalBig());
    }

    @Override
    public BigInteger getItemBytesUsedBig() {
        return apeiron$value(
            apeiron$itemBytesUsed,
            apeiron$gridExact() == null ? BigInteger.ZERO : apeiron$gridExact().getItemBytesUsedBig());
    }

    @Override
    public BigInteger getItemTypesTotalBig() {
        return apeiron$value(
            apeiron$itemTypesTotal,
            apeiron$gridExact() == null ? BigInteger.ZERO : apeiron$gridExact().getItemTypesTotalBig());
    }

    @Override
    public BigInteger getItemTypesUsedBig() {
        return apeiron$value(
            apeiron$itemTypesUsed,
            apeiron$gridExact() == null ? BigInteger.ZERO : apeiron$gridExact().getItemTypesUsedBig());
    }

    @Override
    public BigInteger getItemCellCountBig() {
        return apeiron$value(
            apeiron$itemCellCount,
            apeiron$gridExact() == null ? BigInteger.ZERO : apeiron$gridExact().getItemCellCountBig());
    }

    @Override
    public BigInteger getFluidBytesTotalBig() {
        return apeiron$value(
            apeiron$fluidBytesTotal,
            apeiron$gridExact() == null ? BigInteger.ZERO : apeiron$gridExact().getFluidBytesTotalBig());
    }

    @Override
    public BigInteger getFluidBytesUsedBig() {
        return apeiron$value(
            apeiron$fluidBytesUsed,
            apeiron$gridExact() == null ? BigInteger.ZERO : apeiron$gridExact().getFluidBytesUsedBig());
    }

    @Override
    public BigInteger getFluidTypesTotalBig() {
        return apeiron$value(
            apeiron$fluidTypesTotal,
            apeiron$gridExact() == null ? BigInteger.ZERO : apeiron$gridExact().getFluidTypesTotalBig());
    }

    @Override
    public BigInteger getFluidTypesUsedBig() {
        return apeiron$value(
            apeiron$fluidTypesUsed,
            apeiron$gridExact() == null ? BigInteger.ZERO : apeiron$gridExact().getFluidTypesUsedBig());
    }

    @Override
    public BigInteger getFluidCellCountBig() {
        return apeiron$value(
            apeiron$fluidCellCount,
            apeiron$gridExact() == null ? BigInteger.ZERO : apeiron$gridExact().getFluidCellCountBig());
    }

    @Override
    public BigInteger getEssentiaBytesTotalBig() {
        return apeiron$value(
            apeiron$essentiaBytesTotal,
            apeiron$gridExact() == null ? BigInteger.ZERO : apeiron$gridExact().getEssentiaBytesTotalBig());
    }

    @Override
    public BigInteger getEssentiaBytesUsedBig() {
        return apeiron$value(
            apeiron$essentiaBytesUsed,
            apeiron$gridExact() == null ? BigInteger.ZERO : apeiron$gridExact().getEssentiaBytesUsedBig());
    }

    @Override
    public BigInteger getEssentiaTypesTotalBig() {
        return apeiron$value(
            apeiron$essentiaTypesTotal,
            apeiron$gridExact() == null ? BigInteger.ZERO : apeiron$gridExact().getEssentiaTypesTotalBig());
    }

    @Override
    public BigInteger getEssentiaTypesUsedBig() {
        return apeiron$value(
            apeiron$essentiaTypesUsed,
            apeiron$gridExact() == null ? BigInteger.ZERO : apeiron$gridExact().getEssentiaTypesUsedBig());
    }

    @Override
    public BigInteger getEssentiaCellCountBig() {
        return apeiron$value(
            apeiron$essentiaCellCount,
            apeiron$gridExact() == null ? BigInteger.ZERO : apeiron$gridExact().getEssentiaCellCountBig());
    }
}
