package com.silvia.apeiron.mixin.tst.output;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.Nxer.TwistSpaceTechnology.common.api.giver.ItemStacksGiver;
import com.Nxer.TwistSpaceTechnology.util.rewrites.TST_ItemID;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.tst.BigTstItemGiver;

/** Preserve the public long cache while keeping overflowing native collection merges exact. */
@Pseudo
@Mixin(targets = "com.Nxer.TwistSpaceTechnology.common.api.giver.ItemStacksGiver", remap = false)
public abstract class ItemStacksGiverBigMixin implements BigTstItemGiver {

    @Shadow
    public Map<TST_ItemID, Long> cache;
    @Unique
    private final Map<TST_ItemID, BigInteger> apeiron$promoted = new HashMap<>();

    @Override
    public Map<TST_ItemID, BigInteger> getItemAmountsBig() {
        Map<TST_ItemID, BigInteger> result = new HashMap<>();
        for (Map.Entry<TST_ItemID, Long> entry : cache.entrySet())
            result.put(entry.getKey(), apeiron$amount(entry.getKey()));
        return result;
    }

    @Unique
    private BigInteger apeiron$amount(TST_ItemID type) {
        long projection = cache.getOrDefault(type, 0L);
        BigInteger exact = apeiron$promoted.get(type);
        return exact != null && BigAEStackValues.saturatedLong(exact) == projection ? exact
            : BigInteger.valueOf(projection);
    }

    @Unique
    private void apeiron$add(TST_ItemID type, BigInteger amount) {
        BigInteger total = apeiron$amount(type).add(amount);
        cache.put(type, BigAEStackValues.saturatedLong(total));
        if (total.bitLength() > 63) apeiron$promoted.put(type, total);
        else apeiron$promoted.remove(type);
    }

    @Inject(
        method = "merge(Lcom/Nxer/TwistSpaceTechnology/util/rewrites/TST_ItemID;J)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$mergeOne(TST_ItemID type, long amount, CallbackInfo ci) {
        apeiron$add(type, BigInteger.valueOf(amount));
        ci.cancel();
    }

    @Inject(
        method = "merge(Lcom/Nxer/TwistSpaceTechnology/common/api/giver/ItemStacksGiver;I)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$mergeScaled(ItemStacksGiver giver, int times, CallbackInfo ci) {
        ((BigTstItemGiver) giver).getItemAmountsBig()
            .forEach((type, amount) -> apeiron$add(type, amount.multiply(BigInteger.valueOf(times))));
        ci.cancel();
    }

    @Inject(
        method = "merge(Lcom/Nxer/TwistSpaceTechnology/common/api/giver/ItemStacksGiver;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1)
    private void apeiron$mergeAll(ItemStacksGiver giver, CallbackInfo ci) {
        ((BigTstItemGiver) giver).getItemAmountsBig()
            .forEach(this::apeiron$add);
        ci.cancel();
    }
}
