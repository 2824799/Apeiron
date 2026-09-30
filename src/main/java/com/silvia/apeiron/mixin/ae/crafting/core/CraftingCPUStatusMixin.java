package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.crafting.core.BigCraftingCPUState;
import com.silvia.apeiron.ae.crafting.core.BigCraftingCPUStorage;
import com.silvia.apeiron.ae.crafting.core.BigCraftingCpuStatus;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigNumberFormatter;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.container.implementations.CraftingCPUStatus;
import appeng.util.ItemSorters;
import appeng.util.Platform;

/** Keeps CPU selector records exact through NBT, packets, sorting and display formatting. */
@Mixin(value = CraftingCPUStatus.class, remap = false)
public abstract class CraftingCPUStatusMixin implements BigCraftingCpuStatus {

    @Unique
    private BigInteger apeiron$storage;
    @Unique
    private BigInteger apeiron$usedStorage;
    @Unique
    private BigInteger apeiron$totalItems;
    @Unique
    private BigInteger apeiron$remainingItems;

    @Inject(method = "<init>(Lappeng/api/networking/crafting/ICraftingCPU;I)V", at = @At("TAIL"))
    private void apeiron$captureCluster(final ICraftingCPU cluster, final int serial, final CallbackInfo ci) {
        this.apeiron$storage = cluster instanceof BigCraftingCPUStorage
                ? ((BigCraftingCPUStorage) cluster).getAvailableStorageBig()
                : BigInteger.valueOf(((CraftingCPUStatus) (Object) this).getStorage());
        this.apeiron$usedStorage = cluster instanceof BigCraftingCPUStorage
                ? ((BigCraftingCPUStorage) cluster).getUsedStorageBig()
                : BigInteger.valueOf(((CraftingCPUStatus) (Object) this).getUsedStorage());
        this.apeiron$totalItems = cluster instanceof BigCraftingCPUState
                ? ((BigCraftingCPUState) cluster).getStartItemCountBig()
                : BigInteger.valueOf(((CraftingCPUStatus) (Object) this).getTotalItems());
        this.apeiron$remainingItems = cluster instanceof BigCraftingCPUState
                ? ((BigCraftingCPUState) cluster).getRemainingItemCountBig()
                : BigInteger.valueOf(((CraftingCPUStatus) (Object) this).getRemainingItems());
    }

    @Inject(method = "<init>(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("TAIL"))
    private void apeiron$readExact(final NBTTagCompound tag, final CallbackInfo ci) {
        this.apeiron$storage = read(tag, "storage", "ApeironStorage");
        this.apeiron$usedStorage = read(tag, "usedStorage", "ApeironUsedStorage");
        this.apeiron$totalItems = read(tag, "totalItems", "ApeironTotalItems");
        this.apeiron$remainingItems = read(tag, "remainingItems", "ApeironRemainingItems");
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void apeiron$writeExact(final NBTTagCompound tag, final CallbackInfo ci) {
        BigValueCodec.writeNBT(tag, "storage", "ApeironStorage", new AdaptiveInteger(this.getStorageBig()));
        BigValueCodec.writeNBT(
                tag,
                "usedStorage",
                "ApeironUsedStorage",
                new AdaptiveInteger(this.getUsedStorageBig()));
        BigValueCodec.writeNBT(tag, "totalItems", "ApeironTotalItems", new AdaptiveInteger(this.getTotalItemsBig()));
        BigValueCodec.writeNBT(
                tag,
                "remainingItems",
                "ApeironRemainingItems",
                new AdaptiveInteger(this.getRemainingItemsBig()));
    }

    private static BigInteger read(final NBTTagCompound tag, final String legacyKey, final String bigKey) {
        return tag.hasKey(bigKey, 7)
                ? BigValueCodec.readNBT(tag, legacyKey, bigKey).toBigInteger()
                : BigInteger.valueOf(tag.getLong(legacyKey));
    }

    @Override
    public BigInteger getStorageBig() {
        return this.apeiron$storage == null
                ? BigInteger.valueOf(((CraftingCPUStatus) (Object) this).getStorage())
                : this.apeiron$storage;
    }

    @Override
    public BigInteger getUsedStorageBig() {
        return this.apeiron$usedStorage == null
                ? BigInteger.valueOf(((CraftingCPUStatus) (Object) this).getUsedStorage())
                : this.apeiron$usedStorage;
    }

    @Override
    public BigInteger getTotalItemsBig() {
        return this.apeiron$totalItems == null
                ? BigInteger.valueOf(((CraftingCPUStatus) (Object) this).getTotalItems())
                : this.apeiron$totalItems;
    }

    @Override
    public BigInteger getRemainingItemsBig() {
        return this.apeiron$remainingItems == null
                ? BigInteger.valueOf(((CraftingCPUStatus) (Object) this).getRemainingItems())
                : this.apeiron$remainingItems;
    }

    @Overwrite
    public int compareTo(final CraftingCPUStatus other) {
        final int coprocessors = ItemSorters.compareLong(other.getCoprocessors(),
                ((CraftingCPUStatus) (Object) this).getCoprocessors());
        if (coprocessors != 0) {
            return coprocessors;
        }
        return BigCraftingCpuStatus.storage(other).compareTo(this.getStorageBig());
    }

    @Overwrite
    public String formatStorage() {
        return formatBytes(this.getStorageBig());
    }

    @Overwrite
    public String formatUsedStorage() {
        return formatBytes(this.getUsedStorageBig());
    }

    private static String formatBytes(final BigInteger value) {
        return BigAEStackValues.fitsLong(value)
                ? Platform.formatByteDouble(value.longValue())
                : BigNumberFormatter.formatCompact(value);
    }
}
