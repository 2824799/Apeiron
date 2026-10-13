package com.silvia.apeiron.common.machine.tectech.verification;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch;
import com.silvia.apeiron.common.machine.input.MutablePatternItemInputs;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputMirror;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternBuffer;
import com.silvia.apeiron.common.machine.me.output.MTEInfiniteMEOutputAssembly;
import com.silvia.apeiron.common.machine.me.output.verification.InfiniteMEOutputAssemblySmoke;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import cofh.api.energy.IEnergyContainerItem;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItem;
import tectech.thing.metaTileEntity.multi.MTEEnergyInfuser;

/** Real native RF/EU charging and output callbacks with detached, persistent, exact pattern materials. */
public final class EnergyInfuserPatternSmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(60)
        .add(BigInteger.valueOf(17));

    private EnergyInfuserPatternSmoke() {}

    private static final class Tile extends BaseMetaTileEntity {

        boolean allowed = true;

        @Override
        public boolean isAllowedToWork() {
            return allowed;
        }

        @Override
        public boolean hasWorkJustBeenEnabled() {
            return false;
        }

        @Override
        public void markDirty() {}
    }

    private static final class Controller extends MTEEnergyInfuser {

        final Tile tile = new Tile();
        final List<ItemStack> outputs = new ArrayList<>();
        boolean blocked;
        boolean nativeOutput;
        boolean rejectRepairDrain;

        Controller() {
            super("apeiron.verify.energy_infuser");
            try {
                Class<?> unsafe = Class.forName("sun.misc.Unsafe");
                java.lang.reflect.Field singleton = unsafe.getDeclaredField("theUnsafe");
                singleton.setAccessible(true);
                tile.setWorldObj(
                    (net.minecraft.world.World) unsafe.getMethod("allocateInstance", Class.class)
                        .invoke(singleton.get(null), net.minecraft.world.WorldServer.class));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
            setBaseMetaTileEntity(tile);
            mStartUpCheck = 1000;
            mUpdate = 1000;
        }

        CheckRecipeResult attempt() {
            return checkProcessing_EM();
        }

        void charge() {
            // Hatch notifications schedule a structural rescan; these detached tests keep registered hatches in place.
            mStartUpCheck = 1000;
            mUpdate = 1000;
            onPostTick(tile, 1);
        }

        @Override
        public long getAverageInputVoltage() {
            return 128;
        }

        @Override
        public long getMaxInputAmps() {
            return 1;
        }

        @Override
        public boolean depleteInput(net.minecraftforge.fluids.FluidStack fluid, boolean simulate) {
            if (rejectRepairDrain && !simulate) return false;
            return super.depleteInput(fluid, simulate);
        }

        @Override
        public boolean addOutputAtomic(ItemStack item) {
            if (nativeOutput) return super.addOutputAtomic(item);
            if (blocked) return false;
            outputs.add(item.copy());
            return true;
        }
    }

    private static final class Mirror extends MTEInfinitePatternInputMirror {

        final MTEInfinitePatternInputAssembly source;

        Mirror(MTEInfinitePatternInputAssembly source) {
            super("apeiron.verify.infuser_mirror", 10, new String[0], null);
            this.source = source;
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        @Override
        public MTEInfinitePatternInputAssembly getInputSource() {
            return source;
        }
    }

    private static final class Fixture {

        final Controller machine = new Controller();
        final MTEInfinitePatternInputAssembly input;

        Fixture(ItemStack item, BigInteger count, boolean mirrors) {
            ApeironMachineTile tile = new ApeironMachineTile();
            tile.setInitialValuesAsNBT(
                null,
                (short) ApeironConfig.getMachineId(ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET));
            input = (MTEInfinitePatternInputAssembly) tile.getMetaTileEntity();
            check(machine.addToMachineList(tile, 0), "real pattern input registration failed");
            if (mirrors) {
                machine.mDualInputHatches.add(new Mirror(input));
                machine.mDualInputHatches.add(new Mirror(input));
            }
            check(
                input.addToBufferBig(
                    0,
                    Collections.singletonList(BigAEStackValues.copyWithSize(AEItemStack.create(item), count))),
                "buffer setup failed");
        }

        BigPatternBuffer buffer() {
            return input.getBuffers()
                .get(0);
        }

        ItemStack single() {
            List<IAEStack<?>> contents = buffer().getStacksBig();
            for (int i = contents.size() - 1; i >= 0; i--) if (contents.get(i) instanceof IAEItemStack) {
                IAEStack<?> stack = contents.get(i);
                ItemStack item = ((IAEItemStack) stack).getItemStack();
                item.stackSize = 1;
                return item;
            }
            throw new AssertionError("No buffered charging item");
        }
    }

    public static void verify() {
        if (cpw.mods.fml.common.Loader.isModLoaded("EnderIO")) {
            Item rf = GameRegistry.findItem("EnderIO", "blockCapBank");
            check(rf instanceof IEnergyContainerItem, "Ender IO capacitor bank is unavailable");
            ItemStack capacitor = new ItemStack(rf, 1, 1);
            check(((IEnergyContainerItem) rf).getMaxEnergyStored(capacitor) > 0, "invalid capacitor metadata");
            verifyRF(capacitor);
            verifyBigStack(capacitor);
            verifyOrdinaryBus(capacitor);
            verifyExactOutput(capacitor);
            verifyCapacityAndCleanup(capacitor);
        } else {
            Apeiron.LOG.info(
                "Ender IO is absent in the minimal compatibility profile; RF examples run in the installed-mod profiles");
            verifyCapacityAndCleanup(new ItemStack(GameRegistry.findItem("IC2", "itemBatRE")));
        }
        verifyEU();
        verifyRepair();
        verifyWireless();
        Apeiron.LOG.info(
            "Energy infuser verification passed: real IC2 charging and repair; installed Ender IO RF, exact batches, mirrors, blocked output, power loss and NBT reload");
    }

    private static void verifyRF(ItemStack capacitor) {
        IEnergyContainerItem rf = (IEnergyContainerItem) capacitor.getItem();
        Fixture fixture = new Fixture(capacitor, BigInteger.ONE, true);
        fixture.buffer()
            .setRecipeInputs(Collections.singletonList(AEItemStack.create(capacitor)));
        fixture.buffer()
            .setLocked(true);
        fixture.input.getPatterns()
            .setInventorySlotContents(0, new ItemStack(Items.paper));
        fixture.machine.setEUVar(1000);
        check(
            fixture.machine.attempt()
                .wasSuccessful(),
            "buffered capacitor not recognized as charging");
        fixture.machine.charge();
        int charged = rf.getEnergyStored(fixture.single());
        check(
            charged > 0 && rf.getEnergyStored(capacitor) == 0,
            "charge did not commit or mutated original shared NBT: buffered=" + charged
                + ", original="
                + rf.getEnergyStored(capacitor)
                + ", EU="
                + fixture.machine.getEUVar()
                + ", sources="
                + fixture.machine.mDualInputHatches.size()
                + ", conversion="
                + GregTechAPI.mEUtoRF);
        check(fixture.machine.getEUVar() == 1000 - charged * 100L / GregTechAPI.mEUtoRF, "native RF debit changed");
        check(
            fixture.buffer()
                .getItemAmountBig()
                .equals(BigInteger.ONE),
            "mirrors duplicated charging material");
        check(
            fixture.input.getPatterns()
                .getStackInSlot(0)
                .getItem() == Items.paper,
            "pattern inventory was processed as material");

        NBTTagCompound saved = fixture.buffer()
            .writeNBT();
        BigPatternBuffer loaded = new BigPatternBuffer();
        loaded.readNBT(saved);
        check(
            rf.getEnergyStored(
                ((IAEItemStack) loaded.getStacksBig()
                    .get(0)).getItemStack())
                == charged,
            "reload lost partial charge");
        fixture.machine.setEUVar(0);
        check(
            !fixture.machine.attempt()
                .wasSuccessful(),
            "empty native EU accepted a charging task");
        fixture.machine.charge();
        check(rf.getEnergyStored(fixture.single()) == charged, "power loss changed stored charge");
        fixture.machine.tile.allowed = false;
        fixture.machine.setEUVar(1000);
        fixture.machine.charge();
        check(rf.getEnergyStored(fixture.single()) == charged, "disabled machine charged buffered items");
        fixture.machine.tile.allowed = true;
        finishCharge(fixture, rf);
        check(
            rf.getEnergyStored(fixture.single()) == rf.getMaxEnergyStored(capacitor),
            "capacitor failed to finish charging");
        fixture.machine.blocked = true;
        fixture.machine.outputAfterRecipe_EM();
        check(
            fixture.buffer()
                .getItemAmountBig()
                .equals(BigInteger.ONE),
            "blocked output lost material");
        fixture.machine.blocked = false;
        fixture.machine.outputAfterRecipe_EM();
        fixture.machine.outputAfterRecipe_EM();
        check(
            fixture.machine.outputs.size() == 1 && fixture.buffer()
                .isEmpty(),
            "charged output duplicated or input not consumed");
        check(
            rf.getEnergyStored(fixture.machine.outputs.get(0)) == rf.getMaxEnergyStored(capacitor),
            "output lost charge");
    }

    private static void verifyBigStack(ItemStack capacitor) {
        IEnergyContainerItem rf = (IEnergyContainerItem) capacitor.getItem();
        Fixture fixture = new Fixture(capacitor, HUGE, true);
        fixture.machine.setEUVar(1000);
        fixture.machine.charge();
        check(
            fixture.buffer()
                .getAmountBig(AEItemStack.create(capacitor))
                .equals(HUGE.subtract(BigInteger.ONE)),
            "whole big batch charged instead of one item");
        finishCharge(fixture, rf);
        fixture.machine.outputAfterRecipe_EM();
        check(
            fixture.machine.outputs.size() == 1
                && rf.getEnergyStored(fixture.machine.outputs.get(0)) == rf.getMaxEnergyStored(capacitor),
            "partially charged item was not prioritized");
        check(
            fixture.buffer()
                .getItemAmountBig()
                .equals(HUGE.subtract(BigInteger.ONE)),
            "big batch changed or truncated");
    }

    private static void verifyOrdinaryBus(ItemStack capacitor) {
        Controller machine = new Controller();
        MTEHatchInputBus bus = new MTEHatchInputBus("apeiron.verify.infuser_bus", 1, new String[0], null);
        bus.setBaseMetaTileEntity(new BaseMetaTileEntity());
        bus.mInventory[0] = capacitor.copy();
        machine.mInputBusses.add(bus);
        machine.setEUVar(1000);
        check(
            machine.attempt()
                .wasSuccessful(),
            "ordinary bus check changed");
        machine.charge();
        check(
            ((IEnergyContainerItem) capacitor.getItem()).getEnergyStored(bus.mInventory[0]) > 0,
            "ordinary native charging changed");
    }

    private static void verifyExactOutput(ItemStack capacitor) {
        Fixture fixture = new Fixture(capacitor, BigInteger.ONE, false);
        MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
        check(fixture.machine.addToMachineList(output.getBaseMetaTileEntity(), 0), "ME output registration failed");
        fixture.machine.nativeOutput = true;
        finishCharge(fixture, (IEnergyContainerItem) capacitor.getItem());
        fixture.machine.outputAfterRecipe_EM();
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(BigInteger.ONE),
            "native charged output did not reach ME assembly");
        check(
            fixture.buffer()
                .isEmpty(),
            "ME output did not consume original material");
    }

    private static void verifyEU() {
        Item item = GameRegistry.findItem("IC2", "itemBatRE");
        check(item instanceof IElectricItem, "IC2 RE battery unavailable");
        ItemStack battery = new ItemStack(item);
        Fixture fixture = new Fixture(battery, BigInteger.ONE, false);
        fixture.machine.setEUVar(1000);
        fixture.machine.charge();
        double charged = ElectricItem.manager.getCharge(fixture.single());
        check(
            charged > 0 && charged <= 128 && fixture.machine.getEUVar() == 1000 - (long) Math.ceil(charged),
            "native IC2 voltage limit or EU debit changed");
        check(ElectricItem.manager.getCharge(battery) == 0, "shared original IC2 item changed");
    }

    private static void finishCharge(Fixture fixture, IEnergyContainerItem rf) {
        fixture.machine.setEUVar(100_000_000);
        for (int i = 0; i < 10_000
            && rf.getEnergyStored(fixture.single()) < rf.getMaxEnergyStored(fixture.single()); i++)
            fixture.machine.charge();
        check(
            rf.getEnergyStored(fixture.single()) == rf.getMaxEnergyStored(fixture.single()),
            "native item transfer limit prevented completion");
    }

    private static void verifyRepair() {
        ItemStack tool = new ItemStack(Items.diamond_sword, 1, 25);
        Fixture fixture = new Fixture(tool, BigInteger.ONE, true);
        MTEHatchInput uum = new MTEHatchInput("apeiron.verify.infuser_uum", 1, new String[0], null);
        uum.setBaseMetaTileEntity(new BaseMetaTileEntity());
        uum.mFluid = Materials.UUMatter.getFluid(100);
        fixture.machine.mInputHatches.add(uum);
        fixture.machine.setEUVar(25_000);
        check(
            fixture.machine.attempt()
                .wasSuccessful(),
            "buffered damaged tool not recognized");
        fixture.machine.outputAfterRecipe_EM();
        check(
            fixture.single()
                .getItemDamage() == 0 && tool.getItemDamage() == 25,
            "repair did not commit detached damage");
        check(
            uum.mFluid.amount == 75 && fixture.machine.getEUVar() == 0,
            "native repair material/energy debit changed");
        fixture.machine.outputAfterRecipe_EM();
        check(
            fixture.buffer()
                .isEmpty() && fixture.machine.outputs.size() == 1
                && fixture.machine.outputs.get(0)
                    .getItemDamage() == 0,
            "repaired item output lost or duplicated");
    }

    private static void verifyCapacityAndCleanup(ItemStack capacitor) {
        Fixture fixture = new Fixture(capacitor, BigInteger.valueOf(3), false);
        try {
            try (MutablePatternItemInputs inputs = new MutablePatternItemInputs(fixture.machine)) {
                ItemStack detached = inputs.buses()
                    .get(0).mInventory[0];
                if (capacitor.getItem() instanceof IEnergyContainerItem)
                    ((IEnergyContainerItem) capacitor.getItem()).receiveEnergy(detached, 40, false);
                else ElectricItem.manager
                    .charge(detached, 40, ((IElectricItem) capacitor.getItem()).getTier(detached), true, false);
                throw new IllegalArgumentException("simulated caller failure");
            }
        } catch (IllegalArgumentException expected) {
            check("simulated caller failure".equals(expected.getMessage()), "unexpected item transaction failure");
        }
        check(
            fixture.buffer()
                .getItemAmountBig()
                .equals(BigInteger.valueOf(3)),
            "exception cleanup lost items");
        check(
            fixture.buffer()
                .getAmountBig(AEItemStack.create(capacitor))
                .equals(BigInteger.valueOf(2)),
            "exception cleanup did not separate changed NBT");
        fixture.buffer()
            .reconcile();
        BigPatternBuffer full = new BigPatternBuffer();
        List<IAEStack<?>> types = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            ItemStack item = capacitor.copy();
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("verificationType", i);
            item.setTagCompound(tag);
            types.add(BigAEStackValues.copyWithSize(AEItemStack.create(item), BigInteger.valueOf(2)));
        }
        check(full.add(types, BigInteger.ONE), "full buffer setup failed");
        check(!full.canEditOneItem((IAEItemStack) types.get(0)), "full type table allowed an overflowing changed type");
        full.removeBig(types.get(31), BigInteger.valueOf(2));
        check(full.canEditOneItem((IAEItemStack) types.get(0)), "free type slot did not allow item mutation");
    }

    private static MTEInfiniteEnergyHatch attach(Controller machine, UUID owner, boolean ultimate) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(
            null,
            (short) ApeironConfig.getMachineId(
                ultimate ? ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET
                    : ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET));
        tile.setOwnerUuid(owner);
        check(machine.addToMachineList(tile, 0), "wireless energy hatch registration failed");
        return (MTEInfiniteEnergyHatch) tile.getMetaTileEntity();
    }

    private static void verifyWireless() {
        HashMap<UUID, BigInteger> oldEnergy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> oldTeams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData oldSave = GlobalEnergyWorldSavedData.INSTANCE;
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            for (boolean ultimate : new boolean[] { false, true }) {
                verifyWirelessEU(ultimate);
                verifyWirelessRepair(ultimate);
                if (cpw.mods.fml.common.Loader.isModLoaded("EnderIO")) verifyWirelessRF(ultimate);
            }
            verifyRejectedDebit(new ItemStack(GameRegistry.findItem("IC2", "itemBatRE")));
            if (cpw.mods.fml.common.Loader.isModLoaded("EnderIO"))
                verifyRejectedDebit(new ItemStack(GameRegistry.findItem("EnderIO", "blockCapBank"), 1, 1));
        } finally {
            GlobalVariableStorage.GlobalEnergy = oldEnergy;
            SpaceProjectManager.spaceTeams = oldTeams;
            GlobalEnergyWorldSavedData.INSTANCE = oldSave;
        }
        Apeiron.LOG.info(
            "Energy infuser wireless verification passed: both real hatches, empty local EU, exact account debit, RF/EU charging, balance exhaustion, repair/refund, debit rejection and ME output");
    }

    private static void verifyWirelessEU(boolean ultimate) {
        ItemStack battery = new ItemStack(GameRegistry.findItem("IC2", "itemBatRE"));
        Fixture fixture = new Fixture(battery, HUGE, true);
        UUID owner = UUID.randomUUID();
        MTEInfiniteEnergyHatch hatch = attach(fixture.machine, owner, ultimate);
        ((BigWirelessController) (Object) fixture.machine).getWirelessRecipeState()
            .setVoltageSetting(32);
        WirelessNetworkManager.setUserEU(owner, BigInteger.ZERO);
        check(
            !fixture.machine.attempt()
                .wasSuccessful(),
            "empty wireless account accepted charging");
        fixture.machine.charge();
        check(ElectricItem.manager.getCharge(fixture.single()) == 0, "empty wireless account created item energy");
        WirelessNetworkManager.setUserEU(owner, BigInteger.valueOf(1000));
        check(
            fixture.machine.getEUVar() == 0 && hatch.getEUVar() == 0,
            "wireless fixture unexpectedly has local energy");
        check(
            fixture.machine.attempt()
                .wasSuccessful(),
            "funded wireless account rejected charging because local EU is zero");
        fixture.machine.charge();
        double accepted = ElectricItem.manager.getCharge(fixture.single());
        check(accepted == (ultimate ? 1000 : 32), "wireless IC2 charge ignored available balance/voltage");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(BigInteger.valueOf(1000 - (long) Math.ceil(accepted))),
            "wireless IC2 account debit differs from accepted charge");
        check(fixture.machine.getEUVar() == 0 && hatch.getEUVar() == 0, "wireless IC2 charging filled local buffers");
        check(
            fixture.buffer()
                .getItemAmountBig()
                .equals(HUGE),
            "wireless charge changed big item count");
        WirelessNetworkManager.setUserEU(owner, BigInteger.ZERO);
        NBTTagCompound before = fixture.buffer()
            .writeNBT();
        fixture.machine.charge();
        check(
            before.equals(
                fixture.buffer()
                    .writeNBT()),
            "starvation changed partial item charge or counts");
        WirelessNetworkManager.setUserEU(owner, BigInteger.valueOf(100_000));
        for (int i = 0; i < 1000 && ElectricItem.manager.getCharge(fixture.single())
            < ((IElectricItem) battery.getItem()).getMaxCharge(battery); i++) fixture.machine.charge();
        check(
            ElectricItem.manager.getCharge(fixture.single())
                == ((IElectricItem) battery.getItem()).getMaxCharge(battery),
            "wireless IC2 item did not complete");
        fixture.machine.outputAfterRecipe_EM();
        check(
            fixture.machine.outputs.size() == 1 && fixture.buffer()
                .getItemAmountBig()
                .equals(HUGE.subtract(BigInteger.ONE)),
            "wireless completed IC2 material duplicated or lost");
    }

    private static void verifyWirelessRF(boolean ultimate) {
        ItemStack capacitor = new ItemStack(GameRegistry.findItem("EnderIO", "blockCapBank"), 1, 1);
        IEnergyContainerItem rf = (IEnergyContainerItem) capacitor.getItem();
        Fixture fixture = new Fixture(capacitor, BigInteger.ONE, true);
        UUID owner = UUID.randomUUID();
        attach(fixture.machine, owner, ultimate);
        WirelessNetworkManager.setUserEU(owner, BigInteger.ONE);
        check(
            fixture.machine.attempt()
                .wasSuccessful(),
            "wireless RF startup rejected a positive account");
        fixture.machine.charge();
        int accepted = rf.getEnergyStored(fixture.single());
        check(
            accepted > 0 && accepted <= GregTechAPI.mEUtoRF / 100
                && WirelessNetworkManager.getUserEU(owner)
                    .signum() == 0,
            "small wireless RF budget created free energy");
        ItemStack partial = fixture.single()
            .copy();
        fixture.machine.charge();
        check(ItemStack.areItemStacksEqual(partial, fixture.single()), "empty RF account changed the item");
        WirelessNetworkManager.setUserEU(owner, HUGE);
        BigInteger paid = BigInteger.ZERO;
        for (int i = 0; i < 10_000 && rf.getEnergyStored(fixture.single()) < rf.getMaxEnergyStored(capacitor); i++) {
            int old = rf.getEnergyStored(fixture.single());
            fixture.machine.charge();
            long added = rf.getEnergyStored(fixture.single()) - old;
            paid = paid.add(
                BigInteger.valueOf(added * 100L)
                    .add(BigInteger.valueOf(GregTechAPI.mEUtoRF - 1L))
                    .divide(BigInteger.valueOf(GregTechAPI.mEUtoRF)));
        }
        check(
            rf.getEnergyStored(fixture.single()) == rf.getMaxEnergyStored(capacitor),
            "large wireless RF account overflowed or failed to charge");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(HUGE.subtract(paid)),
            "RF account was debited more than actual accepted energy");
        fixture.machine.blocked = true;
        fixture.machine.outputAfterRecipe_EM();
        check(
            fixture.buffer()
                .getItemAmountBig()
                .equals(BigInteger.ONE),
            "wireless blocked output lost the charged item");
        MTEInfiniteMEOutputAssembly output = InfiniteMEOutputAssemblySmoke.assembly();
        check(
            fixture.machine.addToMachineList(output.getBaseMetaTileEntity(), 0),
            "wireless ME output registration failed");
        fixture.machine.nativeOutput = true;
        fixture.machine.outputAfterRecipe_EM();
        check(
            fixture.buffer()
                .isEmpty()
                && output.getProvider()
                    .getCachedAmountBig()
                    .equals(BigInteger.ONE),
            "wireless charged RF item failed to reach ME output");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(HUGE.subtract(paid)) && fixture.machine.getEUVar() == 0,
            "completion debited charging energy again or filled local EU");
    }

    private static void verifyWirelessRepair(boolean ultimate) {
        ItemStack tool = new ItemStack(Items.diamond_sword, 1, 25);
        Fixture fixture = new Fixture(tool, BigInteger.ONE, true);
        UUID owner = UUID.randomUUID();
        attach(fixture.machine, owner, ultimate);
        MTEHatchInput uum = new MTEHatchInput("apeiron.verify.wireless_infuser_uum", 1, new String[0], null);
        uum.setBaseMetaTileEntity(new BaseMetaTileEntity());
        uum.mFluid = Materials.UUMatter.getFluid(10);
        fixture.machine.mInputHatches.add(uum);
        WirelessNetworkManager.setUserEU(owner, HUGE);
        fixture.machine.outputAfterRecipe_EM();
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(HUGE) && uum.mFluid.amount == 10
                && fixture.single()
                    .getItemDamage() == 25,
            "missing UU consumed wireless EU or repaired for free");
        uum.mFluid.amount = 100;
        fixture.machine.rejectRepairDrain = true;
        fixture.machine.outputAfterRecipe_EM();
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(HUGE) && uum.mFluid.amount == 100
                && fixture.single()
                    .getItemDamage() == 25,
            "rejected repair fluid transaction did not refund energy");
        fixture.machine.rejectRepairDrain = false;
        WirelessNetworkManager.setUserEU(owner, BigInteger.valueOf(24_999));
        fixture.machine.outputAfterRecipe_EM();
        check(
            WirelessNetworkManager.getUserEU(owner)
                .equals(BigInteger.valueOf(24_999)) && uum.mFluid.amount == 100,
            "insufficient repair balance consumed materials");
        WirelessNetworkManager.setUserEU(owner, HUGE);
        fixture.machine.outputAfterRecipe_EM();
        check(
            fixture.single()
                .getItemDamage() == 0 && uum.mFluid.amount == 75
                && WirelessNetworkManager.getUserEU(owner)
                    .equals(HUGE.subtract(BigInteger.valueOf(25_000))),
            "wireless repair charged the wrong account/cost or failed to repair");
        check(fixture.machine.getEUVar() == 0, "wireless repair altered local EU");
    }

    private static final class RejectingHatch extends MTEInfiniteEnergyHatch {

        int attempts;

        RejectingHatch() {
            super("apeiron.verify.rejecting_infuser", 14, new String[0], null);
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        @Override
        public BigInteger getAvailableEUBig() {
            return HUGE;
        }

        @Override
        public boolean consumeEUBig(BigInteger amount) {
            attempts++;
            return false;
        }
    }

    private static void verifyRejectedDebit(ItemStack item) {
        Fixture fixture = new Fixture(item, BigInteger.ONE, false);
        RejectingHatch hatch = new RejectingHatch();
        fixture.machine.mEnergyHatches.add(hatch);
        NBTTagCompound before = fixture.buffer()
            .writeNBT();
        fixture.machine.charge();
        check(
            hatch.attempts == 1 && before.equals(
                fixture.buffer()
                    .writeNBT()),
            "failed atomic debit published free item charge");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException("Energy infuser verification failed: " + message);
    }
}
