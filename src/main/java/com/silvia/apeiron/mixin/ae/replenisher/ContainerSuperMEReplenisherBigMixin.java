package com.silvia.apeiron.mixin.ae.replenisher;

import java.math.BigInteger;

import net.minecraft.entity.player.InventoryPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.sync.BigIntegerSyncCodec;
import com.silvia.apeiron.ae.automation.BigSuperMEReplenisher;
import com.silvia.apeiron.ae.sync.BigSyncRegistrarAccess;

import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerSuperMEReplenisher;
import appeng.container.sync.handlers.ObjectSyncHandler;
import appeng.tile.misc.TileSuperMEReplenisher;

/** Synchronizes Super ME Replenisher counters without truncating them to long. */
@Mixin(value = ContainerSuperMEReplenisher.class, remap = false)
public abstract class ContainerSuperMEReplenisherBigMixin implements BigSuperMEReplenisher {

    @Shadow private TileSuperMEReplenisher tile;

    @Unique private ObjectSyncHandler<BigInteger> apeiron$totalBig;
    @Unique private ObjectSyncHandler<BigInteger> apeiron$usedBig;

    @Inject(method = "<init>(Lnet/minecraft/entity/player/InventoryPlayer;Lappeng/tile/misc/TileSuperMEReplenisher;)V", at = @At("TAIL"))
    private void apeiron$registerExactSync(final InventoryPlayer inventory, final TileSuperMEReplenisher tile,
        final CallbackInfo ci) {
        final BigSuperMEReplenisher exact = (BigSuperMEReplenisher) tile;
        final BigSyncRegistrarAccess registrarAccess = (BigSyncRegistrarAccess) (Object) this;
        this.apeiron$totalBig = registrarAccess.apeiron$syncRegistrar().objectS2C(
                "apeironTotalBytes", BigIntegerSyncCodec.INSTANCE, exact.getTotalBytesBig());
        this.apeiron$usedBig = registrarAccess.apeiron$syncRegistrar().objectS2C(
                "apeironUsedBytes", BigIntegerSyncCodec.INSTANCE, exact.getUsedBytesBig());
    }

    @Inject(method = "detectAndSendChanges", at = @At("HEAD"))
    private void apeiron$sendExact(final CallbackInfo ci) {
        if (this.apeiron$totalBig == null) return;
        final BigSuperMEReplenisher exact = (BigSuperMEReplenisher) this.tile;
        this.apeiron$totalBig.set(exact.getTotalBytesBig());
        this.apeiron$usedBig.set(exact.getUsedBytesBig());
    }

    @Override
    public BigInteger getTotalBytesBig() {
        if (this.apeiron$totalBig != null) return this.apeiron$totalBig.get();
        return ((BigSuperMEReplenisher) this.tile).getTotalBytesBig();
    }

    @Override
    public BigInteger getUsedBytesBig() {
        if (this.apeiron$usedBig != null) return this.apeiron$usedBig.get();
        return ((BigSuperMEReplenisher) this.tile).getUsedBytesBig();
    }
}
