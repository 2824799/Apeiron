package com.silvia.apeiron.common.machine.me.output.verification;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.output.storage.BigMEOutputProvider;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.config.ApeironConfig;
import com.silvia.apeiron.math.BigNumberFormatter;
import com.silvia.apeiron.math.BigValueCodec;

import gregtech.api.enums.HatchElement;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Checks both exact channels, their shared node, simulation, save/reload and dropped-item state. */
public final class InfiniteMEOutputAssemblySmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(180)
        .add(BigInteger.valueOf(17));

    private InfiniteMEOutputAssemblySmoke() {}

    public static MTEInfiniteMEOutputAssembly assembly() {
        final ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(2));
        return (MTEInfiniteMEOutputAssembly) tile.getMetaTileEntity();
    }

    public static void verify() {
        verifyGenericRegistration();
        final MTEInfiniteMEOutputAssembly assembly = assembly();
        check(
            com.silvia.apeiron.common.machine.registration.ApeironMachines.mixedOutputAssembly != null,
            "registered mixed output prototype");
        check(assembly.getSizeInventory() == 2, "two real storage cell slots");
        check(
            assembly.getBaseMetaTileEntity()
                .getMetaTileEntity() == assembly,
            "fluid view replaced placed tile");
        check(
            assembly.getFluidProvider()
                .getProxy() == assembly.getProxy(),
            "shared AE proxy");
        assembly.getProvider()
            .setCheckMode(true);
        assembly.getFluidProvider()
            .setCheckMode(true);
        final ItemStack diamond = new ItemStack(Items.diamond);
        final FluidStack water = new FluidStack(FluidRegistry.WATER, 1);
        check(
            assembly.storeAmountBig(diamond, HUGE, true)
                .equals(HUGE),
            "item simulation");
        check(
            assembly.fillBig(water, HUGE, false)
                .equals(HUGE),
            "fluid simulation");
        check(
            assembly.getProvider()
                .getCachedAmountBig()
                .signum() == 0
                && assembly.getFluidProvider()
                    .getCachedAmountBig()
                    .signum() == 0,
            "simulation mutated caches");
        final BigMachineOutputQueue outputs = new BigMachineOutputQueue();
        outputs.addItem(diamond, HUGE);
        outputs.addFluid(water, HUGE.multiply(BigInteger.valueOf(3)));
        check(
            outputs.flush(
                Collections.singletonList(assembly),
                Collections.singletonList(assembly.getFluidOutput()),
                true,
                true) && outputs.isEmpty(),
            "both big transaction channels");
        verifyWaila(assembly);
        final NBTTagCompound saved = new NBTTagCompound();
        assembly.saveNBTData(saved);
        saved.setLong("baseCapacity", 1L);
        saved.setBoolean("checkMode", true);
        saved.getCompoundTag("ApeironAssemblyFluids")
            .setLong("baseCapacity", 1L);
        saved.getCompoundTag("ApeironAssemblyFluids")
            .setBoolean("checkMode", true);
        final MTEInfiniteMEOutputAssembly restored = assembly();
        restored.loadNBTData(saved);
        restored.loadNBTData(saved);
        checkCounts(restored, HUGE, HUGE.multiply(BigInteger.valueOf(3)));
        check(
            restored.storeAmountBig(diamond, HUGE.pow(3), true)
                .equals(HUGE.pow(3))
                && restored.fillBig(water, HUGE.pow(3), false)
                    .equals(HUGE.pow(3)),
            "old assembly capacities limited either channel");
        final NBTTagCompound dropped = new NBTTagCompound();
        assembly.setItemNBT(dropped);
        final MTEInfiniteMEOutputAssembly replaced = assembly();
        replaced.loadNBTData(dropped);
        checkCounts(replaced, HUGE, HUGE.multiply(BigInteger.valueOf(3)));
        final ByteBuf packet = Unpooled.buffer();
        assembly.writeToStream(packet);
        final MTEInfiniteMEOutputAssembly client = assembly();
        client.readFromStream(packet);
        check(
            client.getProvider()
                .isCacheUnlimited()
                && client.getFluidProvider()
                    .isCacheUnlimited(),
            "both unlimited channels after client sync");
        check(packet.readableBytes() == 0, "assembly packet not fully read");
        packet.release();
        Apeiron.LOG.info("Infinite ME Output Assembly dual-channel, persistence and shared-node verification passed");
    }

    private static void verifyGenericRegistration() {
        final MTEElectricBlastFurnace ordinary = new MTEElectricBlastFurnace("apeiron.verification.generic_output");
        ordinary.setBaseMetaTileEntity(new BaseMetaTileEntity());
        assertRegistration(ordinary);
        if (cpw.mods.fml.common.Loader.isModLoaded("TwistSpaceTechnology")) {
            try {
                final MTEMultiBlockBase tst = (MTEMultiBlockBase) Class
                    .forName("com.Nxer.TwistSpaceTechnology.common.machine.GT_TileEntity_PhysicalFormSwitcher")
                    .getConstructor(String.class)
                    .newInstance("apeiron.verification.physical_form_output");
                tst.setBaseMetaTileEntity(new BaseMetaTileEntity());
                assertRegistration(tst);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Physical form switcher registration verification failed", e);
            }
        }
        Apeiron.LOG.info(
            "Mixed output generic registration verification passed: GT/TST, both hatch counts, repeat scan and rebuild");
    }

    private static void assertRegistration(MTEMultiBlockBase controller) {
        final MTEInfiniteMEOutputAssembly assembly = assembly();
        check(
            HatchElement.OutputHatch.matchesHatch(assembly) && HatchElement.OutputBus.matchesHatch(assembly),
            "hybrid does not match both structure element types");
        check(!controller.addToMachineList(null, 0), "null hatch was accepted");
        for (int scan = 0; scan < 2; scan++) {
            controller.mOutputBusses.clear();
            controller.mOutputHatches.clear();
            for (int repeat = 0; repeat < 3; repeat++) {
                check(
                    controller.addToMachineList(assembly.getBaseMetaTileEntity(), 0),
                    "generic hatch adder rejected assembly");
            }
            check(
                controller.mOutputBusses.size() == 1 && controller.mOutputHatches.size() == 1,
                "generic scan must register both output types exactly once");
            check(controller.mOutputHatches.get(0) == assembly.getFluidOutput(), "wrong fluid view registered");
            check(
                HatchElement.OutputHatch.count(controller) == 1 && HatchElement.OutputBus.count(controller) == 1,
                "structure reports no output hatch or bus");
            check(
                controller.addOutputBusToMachineList(assembly.getBaseMetaTileEntity(), 0)
                    && controller.addOutputHatchToMachineList(assembly.getBaseMetaTileEntity(), 0),
                "specialized adders regressed");
            check(
                controller.mOutputBusses.size() == 1 && controller.mOutputHatches.size() == 1,
                "specialized adder duplicated generic registration");
            check(
                assembly.getBaseMetaTileEntity()
                    .getMetaTileEntity() == assembly,
                "fluid view replaced the physical assembly");
        }
    }

    private static void verifyWaila(MTEInfiniteMEOutputAssembly assembly) {
        final NBTTagCompound tag = new NBTTagCompound();
        assembly.getWailaNBTData(null, (TileEntity) assembly.getBaseMetaTileEntity(), tag, null, 0, 0, 0);
        final NBTTagCompound fluids = tag.getCompoundTag("ApeironAssemblyFluids");
        check(
            tag.getBoolean("ApeironUnlimitedCache") && fluids.getBoolean("ApeironUnlimitedCache"),
            "both Waila channels marked unlimited");
        check(
            BigValueCodec.readNBT(
                tag.getTagList("stacks", 10)
                    .getCompoundTagAt(0),
                "Amount",
                "ApeironAmount")
                .toBigInteger()
                .equals(HUGE),
            "Waila item amount");
        check(
            BigValueCodec.readNBT(
                fluids.getTagList("stacks", 10)
                    .getCompoundTagAt(0),
                "Amount",
                "ApeironAmount")
                .toBigInteger()
                .equals(HUGE.multiply(BigInteger.valueOf(3))),
            "Waila fluid amount");
        if (!cpw.mods.fml.common.FMLCommonHandler.instance()
            .getSide()
            .isClient()) return;
        final List<String> lines = new ArrayList<>();
        BigMEOutputProvider.WailaHelper.getWailaAdvancedBody("item", lines, tag);
        BigMEOutputProvider.WailaHelper.getWailaAdvancedBody("fluid", lines, fluids);
        check(
            lines.stream()
                .anyMatch(line -> line.contains(BigNumberFormatter.formatExact(HUGE)))
                && lines.stream()
                    .anyMatch(
                        line -> line.contains(BigNumberFormatter.formatExact(HUGE.multiply(BigInteger.valueOf(3))))),
            "Waila must render both complete counts");
    }

    private static void checkCounts(MTEInfiniteMEOutputAssembly assembly, BigInteger items, BigInteger fluids) {
        check(
            assembly.getProvider()
                .getCachedAmountBig()
                .equals(items),
            "exact item cache");
        check(
            assembly.getFluidProvider()
                .getCachedAmountBig()
                .equals(fluids),
            "exact fluid cache");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Assembly verification failed: " + message);
    }
}
