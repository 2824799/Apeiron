package com.silvia.apeiron.common.machine.block.verification;

import java.math.BigInteger;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.block.MachineItemNbt;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputMirror;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternStackCodec;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.stocking.StockingInputHost;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.CommonMetaTileEntity;

/** Checks both drop implementations and migration of existing items in the transformed game. */
public final class MachineDropsSmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(180)
        .add(BigInteger.ONE);

    private MachineDropsSmoke() {}

    public static void verify() {
        for (int offset = 0; offset <= ApeironMachines.EYE_OF_HARMONY_ENHANCEMENT_OFFSET; offset++) {
            BaseMetaTileEntity nativeTile = tile(offset, false, null);
            NBTTagCompound item = new NBTTagCompound();
            nativeTile.getMetaTileEntity()
                .setItemNBT(item);
            check(item.hasNoTags(), "empty item writer retained state at offset " + offset);
            check(
                !nativeTile.getDrops()
                    .get(0)
                    .hasTagCompound(),
                "native drop retained state at offset " + offset);
            check(
                !tile(offset, true, null).getDrops()
                    .get(0)
                    .hasTagCompound(),
                "Apeiron drop retained state at offset " + offset);
        }
        verifyUsedEmptyMachines();
        verifyLegacyItems();
        verifyPhysicalInventory();
        verifyRefunds();
        verifyUpgrades();
        Apeiron.LOG.info(
            "Machine drop verification passed: all 12 empty machines stack; legacy tags cleaned; exact contents and upgrades preserved");
    }

    private static BaseMetaTileEntity tile(int offset, boolean own, NBTTagCompound tag) {
        BaseMetaTileEntity tile = own ? new ApeironMachineTile() : new BaseMetaTileEntity();
        tile.setInitialValuesAsNBT(tag, (short) ApeironConfig.getMachineId(offset));
        return tile;
    }

    private static void verifyUsedEmptyMachines() {
        MTEInfiniteMEOutputAssembly output = (MTEInfiniteMEOutputAssembly) tile(2, false, null).getMetaTileEntity();
        output.setConnectsToAllSides(true);
        output.getProvider()
            .setCacheMode(true);
        output.getProvider()
            .setPriority(37);
        output.getFluidProvider()
            .setCacheMode(true);
        output.getFluidProvider()
            .setPriority(-19);
        NBTTagCompound world = new NBTTagCompound();
        output.saveNBTData(world);
        MTEInfiniteMEOutputAssembly restored = (MTEInfiniteMEOutputAssembly) tile(2, false, world).getMetaTileEntity();
        check(
            restored.connectsToAllSides() && restored.getPriority() == 37
                && restored.getFluidProvider()
                    .getPriority() == -19,
            "world save lost output configuration");
        NBTTagCompound drop = (NBTTagCompound) legacyOutput().copy();
        output.setItemNBT(drop);
        check(
            drop.getBoolean("additionalConnection") && drop.getBoolean("cacheMode")
                && drop.getInteger("myPriority") == 37
                && drop.getCompoundTag("ApeironAssemblyFluids")
                    .getBoolean("cacheMode")
                && drop.getCompoundTag("ApeironAssemblyFluids")
                    .getInteger("myPriority") == -19,
            "non-default output settings were lost from an empty machine");
        output.getProvider()
            .addToCacheBig(BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), HUGE));
        output.getFluidProvider()
            .addToCacheBig(
                BigAEStackValues.copyWithSize(AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)), HUGE));
        output.setItemNBT(drop);
        MachineItemNbt.normalize(item(2, drop));
        check(
            drop.hasKey("cache", 9) && drop.getCompoundTag("ApeironAssemblyFluids")
                .hasKey("cache", 9),
            "normalization removed populated output channels");
        restored.loadNBTData(drop);
        check(
            restored.getProvider()
                .getCachedAmountBig()
                .equals(HUGE)
                && restored.getFluidProvider()
                    .getCachedAmountBig()
                    .equals(HUGE),
            "drop lost exact output amounts");
        output.loadNBTData(new NBTTagCompound());
        output.setItemNBT(drop);
        check(drop.hasNoTags(), "drained output retained a stale cache");

        MTEInfinitePatternInputAssembly input = (MTEInfinitePatternInputAssembly) tile(4, false, null)
            .getMetaTileEntity();
        input.saveNBTData(world);
        NBTTagCompound state = world.getCompoundTag("ApeironPatternInput");
        state.setLong("savedPushCalls", 123);
        input.loadNBTData(world);
        input.setItemNBT(drop);
        check(drop.hasNoTags(), "empty pattern input retained history or empty buffers");
        MTEInfinitePatternInputMirror mirror = (MTEInfinitePatternInputMirror) tile(5, false, null).getMetaTileEntity();
        mirror.setLink(0, 10, 20, 30);
        mirror.setItemNBT(drop);
        check(
            drop.getBoolean("ApeironInputLinked") && drop.getInteger("ApeironInputX") == 10
                && drop.getInteger("ApeironInputY") == 20
                && drop.getInteger("ApeironInputZ") == 30,
            "linked mirror lost its link settings");
    }

    private static NBTTagCompound legacyOutput() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("additionalConnection", false);
        tag.setBoolean("cacheMode", false);
        tag.setInteger("myPriority", 0);
        tag.setTag("cache", new NBTTagList());
        NBTTagCompound fluids = (NBTTagCompound) tag.copy();
        tag.setTag("ApeironAssemblyFluids", fluids);
        return tag;
    }

    private static ItemStack item(int offset, NBTTagCompound tag) {
        ItemStack item = new ItemStack(ApeironMachines.block, 1, ApeironConfig.getMachineId(offset));
        item.setTagCompound(tag);
        return item;
    }

    private static void verifyLegacyItems() {
        ItemStack old = item(2, legacyOutput());
        MachineItemNbt.normalize(old);
        check(!old.hasTagCompound(), "legacy empty output still cannot stack");
        NBTTagCompound tag = legacyOutput();
        NBTTagCompound wrapped = new NBTTagCompound();
        wrapped.setBoolean("cacheMode", false);
        wrapped.setInteger("myPriority", 0);
        tag.setTag("cache", wrapped);
        tag.getCompoundTag("ApeironAssemblyFluids")
            .setTag("cache", wrapped.copy());
        old = item(2, tag);
        MachineItemNbt.normalize(old);
        check(!old.hasTagCompound(), "legacy empty cache compounds still cannot stack");
        tag = legacyOutput();
        NBTTagCompound nativeTags = new NBTTagCompound();
        nativeTags.setBoolean("mLockUpgrade", true);
        nativeTags.setByte("mOtherUpgrades", (byte) 2);
        nativeTags.setByte("mColor", (byte) 5);
        nativeTags.setString("nativeData", "keep");
        NBTTagList covers = new NBTTagList();
        NBTTagCompound cover = new NBTTagCompound();
        cover.setInteger("coverId", 42);
        covers.appendTag(cover);
        nativeTags.setTag("covers", covers);
        for (String key : nativeTags.func_150296_c()) tag.setTag(key, nativeTags.getTag(key));
        old = item(2, tag);
        MachineItemNbt.normalize(old);
        check(nativeTags.equals(old.getTagCompound()), "legacy cleanup removed unrelated native metadata");

        for (int offset : new int[] { 4, 5, 7, 8, 9, ApeironMachines.EYE_OF_HARMONY_ENHANCEMENT_OFFSET }) {
            BaseMetaTileEntity source = tile(offset, false, null);
            NBTTagCompound saved = new NBTTagCompound();
            source.getMetaTileEntity()
                .saveNBTData(saved);
            NBTTagCompound legacy = new NBTTagCompound();
            if (offset == 4) legacy.setTag("ApeironPatternInput", saved.getCompoundTag("ApeironPatternInput"));
            else if (offset == 5) {
                for (String key : new String[] { "ApeironInputLinked", "ApeironInputDimension", "ApeironInputX",
                    "ApeironInputY", "ApeironInputZ" }) legacy.setTag(key, saved.getTag(key));
            } else if (offset == ApeironMachines.EYE_OF_HARMONY_ENHANCEMENT_OFFSET) legacy.setTag(
                com.silvia.apeiron.common.machine.tectech.MTEEyeOfHarmonyEnhancementModule.ROOT_TAG,
                saved.getCompoundTag(
                    com.silvia.apeiron.common.machine.tectech.MTEEyeOfHarmonyEnhancementModule.ROOT_TAG));
            else legacy.setTag("ApeironStockingInput", saved.getCompoundTag("ApeironStockingInput"));
            NBTTagCompound contents = new NBTTagCompound();
            contents.setString("items", "0");
            legacy.setTag("ApeironContents", contents);
            old = item(offset, legacy);
            MachineItemNbt.normalize(old);
            check(!old.hasTagCompound(), "legacy empty input still cannot stack at offset " + offset);
        }
    }

    private static void verifyPhysicalInventory() {
        for (int offset : new int[] { 0, 1, 2, 6 }) {
            BaseMetaTileEntity source = tile(offset, true, null);
            CommonMetaTileEntity machine = (CommonMetaTileEntity) source.getMetaTileEntity();
            ItemStack sample = new ItemStack(Items.diamond, 193);
            NBTTagCompound payload = new NBTTagCompound();
            payload.setString("sample", "keep");
            sample.setTagCompound(payload);
            machine.mInventory[0] = sample;
            ItemStack drop = source.getDrops()
                .get(0);
            MachineItemNbt.normalize(drop);
            IMetaTileEntity restored = tile(offset, true, drop.getTagCompound()).getMetaTileEntity();
            check(
                ItemStack.areItemStacksEqual(sample, restored.getRealInventory()[0]),
                "drop lost physical inventory at offset " + offset);
            machine.mInventory[0] = new ItemStack(Items.diamond, 0);
            NBTTagCompound empty = new NBTTagCompound();
            machine.setItemNBT(empty);
            check(empty.hasNoTags(), "zero-count item retained drop NBT at offset " + offset);
        }
    }

    private static void verifyRefunds() {
        for (int offset : new int[] { 7, 8, 9 }) {
            boolean fluid = offset == 8;
            NBTTagList refunds = new NBTTagList();
            refunds.appendTag(
                BigPatternStackCodec.write(
                    BigAEStackValues.copyWithSize(
                        fluid ? AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1))
                            : AEItemStack.create(new ItemStack(Items.diamond)),
                        HUGE)));
            NBTTagCompound state = new NBTTagCompound();
            state.setTag("refunds", refunds);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag("ApeironStockingInput", state);
            BaseMetaTileEntity source = tile(offset, true, tag);
            ItemStack drop = source.getDrops()
                .get(0);
            MachineItemNbt.normalize(drop);
            StockingInputHost restored = (StockingInputHost) tile(offset, true, drop.getTagCompound())
                .getMetaTileEntity();
            check(
                restored.getStockingInput()
                    .getRefundAmount(fluid)
                    .equals(HUGE),
                "drop lost exact refunds at offset " + offset);
            check(
                drop.getTagCompound()
                    .getCompoundTag("ApeironStockingInput")
                    .func_150296_c()
                    .size() == 1,
                "refund-only drop retained ghost configuration");
        }
    }

    private static void verifyUpgrades() {
        NBTTagCompound upgrades = new NBTTagCompound();
        upgrades.setBoolean("mLockUpgrade", true);
        upgrades.setByte("mOtherUpgrades", (byte) 2);
        ItemStack drop = tile(2, true, upgrades).getDrops()
            .get(0);
        MachineItemNbt.normalize(drop);
        check(upgrades.equals(drop.getTagCompound()), "empty machine lost installed upgrades");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Machine drop verification: " + message);
    }
}
