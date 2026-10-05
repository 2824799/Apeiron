package com.silvia.apeiron.common.machine.spaceelevator.verification;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.api.machine.parallel.BigWirelessController;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.energy.InfiniteEnergyHatches;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputBus;
import com.silvia.apeiron.common.machine.me.output.MTEBoundlessMEOutputHatch;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.common.machine.spaceelevator.ModuleParallelParameter;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gtnhintergalactic.recipe.IGRecipeMaps;
import gtnhintergalactic.recipe.SpaceMiningData;
import gtnhintergalactic.recipe.SpaceMiningRecipes.WeightedAsteroidList;
import gtnhintergalactic.recipe.SpacePumpingRecipes;
import gtnhintergalactic.tile.multi.elevator.TileEntitySpaceElevator;
import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleAssembler.TileEntityModuleAssemblerT1;
import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleBase;
import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleMiner.TileEntityModuleMinerT1;
import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModulePump;
import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModulePump.TileEntityModulePumpT1;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;
import tectech.thing.metaTileEntity.multi.base.parameter.IntegerParameter;

/** Runs transformed module recipes against one detached parent wireless account. */
public final class SpaceElevatorIntegrationSmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(30);

    private SpaceElevatorIntegrationSmoke() {}

    public static void verify() {
        HashMap<UUID, BigInteger> energy = GlobalVariableStorage.GlobalEnergy;
        Map<UUID, UUID> teams = SpaceProjectManager.spaceTeams;
        GlobalEnergyWorldSavedData save = GlobalEnergyWorldSavedData.INSTANCE;
        try {
            GlobalVariableStorage.GlobalEnergy = new HashMap<>();
            SpaceProjectManager.spaceTeams = new HashMap<>();
            GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
            UUID owner = UUID.randomUUID();
            TileEntitySpaceElevator parent = new Elevator();
            parent.setBaseMetaTileEntity(new BaseMetaTileEntity());
            verifyParallelSettings(parent, owner);
            ((BigWirelessController) parent).getWirelessRecipeState()
                .setTargetDuration(1);
            verifyPump(parent, owner);
            verifyMiner(parent, owner);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Space elevator verification failed", error);
        } finally {
            GlobalVariableStorage.GlobalEnergy = energy;
            SpaceProjectManager.spaceTeams = teams;
            GlobalEnergyWorldSavedData.INSTANCE = save;
        }
        Apeiron.LOG.info(
            "Space elevator verification passed: parent account, native caps, exact parameter reload, pump/miner budgets, input debits and outputs");
    }

    private static void verifyParallelSettings(TileEntitySpaceElevator parent, UUID owner)
        throws ReflectiveOperationException {
        TileEntityModuleAssemblerT1 module = new TileEntityModuleAssemblerT1("apeiron.verify.space_assembler");
        module.setBaseMetaTileEntity(new BaseMetaTileEntity());
        module.initParameters();
        connect(module, parent);
        ModuleParallelParameter parameter = (ModuleParallelParameter) field(module, "parallelParameter");
        int nativeCap = parameter.getValue();
        parent.mEnergyHatches.add(
            (com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch) tile(
                ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET,
                owner).getMetaTileEntity());
        parameter.setBig(HUGE);
        check(
            parameter.getBig()
                .equals(BigInteger.valueOf(nativeCap)),
            "ordinary hatch removed native cap");
        parent.mEnergyHatches.clear();
        parent.mEnergyHatches.add(
            (com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch) tile(
                ApeironMachines.ULTIMATE_ENERGY_HATCH_OFFSET,
                owner).getMetaTileEntity());
        parameter.setBig(HUGE);
        check(
            parameter.getBig()
                .equals(HUGE),
            "ultimate parallel parameter truncated");
        NBTTagCompound saved = new NBTTagCompound();
        parameter.saveNBT(saved);
        parameter.setBig(BigInteger.ONE);
        parameter.loadNBT(saved);
        check(
            parameter.getBig()
                .equals(HUGE),
            "exact parallel setting lost on reload");
        check(
            InfiniteEnergyHatches.find(module) == InfiniteEnergyHatches.find(parent),
            "module did not use parent hatch");
        verifyParameterPackets(parameter);
        if (cpw.mods.fml.common.FMLCommonHandler.instance()
            .getSide()
            .isClient()) {
            check(
                parameter.createExactEditor()
                    .isName("apeiron_module_parallel"),
                "module exact editor missing");
            if (com.silvia.apeiron.compat.DependencyCapabilities.hasMethod(
                "gregtech.common.gui.modularui.multiblock.base.TTMultiblockBaseGui",
                "createInputWidget",
                null)) {
                Method factory = gregtech.common.gui.modularui.multiblock.base.TTMultiblockBaseGui.class
                    .getDeclaredMethod(
                        "createInputWidget",
                        com.cleanroommc.modularui.screen.ModularPanel.class,
                        com.cleanroommc.modularui.value.sync.PanelSyncManager.class,
                        tectech.thing.metaTileEntity.multi.base.parameter.Parameter.class);
                factory.setAccessible(true);
                Object widget = factory.invoke(
                    new gregtech.common.gui.modularui.multiblock.base.TTMultiblockBaseGui<>(module),
                    new com.cleanroommc.modularui.screen.ModularPanel("apeiron.verify.space_editor"),
                    new com.cleanroommc.modularui.value.sync.PanelSyncManager(
                        new com.cleanroommc.modularui.value.sync.ModularSyncManager(false),
                        true),
                    parameter);
                check(
                    ((com.cleanroommc.modularui.api.widget.IWidget) widget).isName("apeiron_module_parallel"),
                    "legacy module GUI kept its int-only editor");
            }
        }
    }

    private static void verifyParameterPackets(ModuleParallelParameter server) {
        TileEntityModuleAssemblerT1 client = new TileEntityModuleAssemblerT1("apeiron.verify.space_client");
        client.setBaseMetaTileEntity(new BaseMetaTileEntity());
        client.initParameters();
        try {
            ModuleParallelParameter clientParameter = (ModuleParallelParameter) field(client, "parallelParameter");
            com.cleanroommc.modularui.value.sync.StringSyncValue sent = server.createExactSyncValue();
            com.cleanroommc.modularui.value.sync.StringSyncValue received = clientParameter.createExactSyncValue();
            net.minecraft.network.PacketBuffer packet = new net.minecraft.network.PacketBuffer(
                io.netty.buffer.Unpooled.buffer());
            try {
                sent.write(packet);
                received.readOnClient(0, packet);
                check(
                    com.silvia.apeiron.math.ScientificInteger.nonNegative(received.getStringValue())
                        .equals(HUGE),
                    "client without parent hatch truncated synced parallels");
                packet.clear();
                received.setStringValue("1e31", false, false);
                received.write(packet);
                sent.readOnServer(0, packet);
                check(
                    server.getBig()
                        .equals(BigInteger.TEN.pow(31)),
                    "module packet edit was ignored");
                server.setBig(HUGE);
            } finally {
                packet.release();
            }
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw new IllegalStateException("Module parameter packet verification failed", error);
        }
    }

    private static void verifyPump(TileEntitySpaceElevator parent, UUID owner) throws ReflectiveOperationException {
        TileEntityModulePumpT1 module = new TileEntityModulePumpT1("apeiron.verify.space_pump");
        module.setBaseMetaTileEntity(new BaseMetaTileEntity());
        module.initParameters();
        connect(module, parent);
        MTEBoundlessMEOutputHatch output = (MTEBoundlessMEOutputHatch) tile(1, owner).getMetaTileEntity();
        module.mOutputHatches.add(output);
        IntegerParameter[] parallels = (IntegerParameter[]) field(module, "parallelParameters");
        for (IntegerParameter parallel : parallels) ((ModuleParallelParameter) parallel).setBig(BigInteger.ZERO);
        ((ModuleParallelParameter) parallels[0]).setBig(HUGE);
        IntegerParameter[] planets = (IntegerParameter[]) field(module, "planetTypeParameters");
        IntegerParameter[] gases = (IntegerParameter[]) field(module, "gasTypeParameters");
        Pair<Integer, Integer> key = Pair.of(planets[0].getValue(), gases[0].getValue());
        FluidStack old = SpacePumpingRecipes.RECIPES.put(key, new FluidStack(FluidRegistry.WATER, 100));
        try {
            BigInteger totalEnergy = BigInteger.valueOf(TileEntityModulePump.ENERGY_CONSUMPTION)
                .multiply(BigInteger.valueOf(60));
            WirelessNetworkManager.setUserEU(owner, totalEnergy);
            check(
                module.checkProcessing_EM()
                    .wasSuccessful(),
                "pump rejected parent power");
            check(
                state(module).getParallelsBig()
                    .equals(BigInteger.valueOf(3)),
                "pump ignored batch energy budget");
            check(
                state(module).getTotalEUBig()
                    .equals(totalEnergy),
                "pump EU total changed");
            complete(module, owner);
            check(
                output.getProvider()
                    .getCachedAmountBig()
                    .equals(BigInteger.valueOf(300)),
                "pump lost fluid output");
            WirelessNetworkManager.setUserEU(
                owner,
                BigInteger.valueOf(TileEntityModulePump.ENERGY_CONSUMPTION)
                    .multiply(BigInteger.valueOf(20))
                    .multiply(HUGE));
            check(
                module.checkProcessing_EM()
                    .wasSuccessful(),
                "pump rejected exact huge batch");
            check(
                state(module).getParallelsBig()
                    .equals(HUGE),
                "pump huge parallels truncated");
            complete(module, owner);
            check(
                output.getProvider()
                    .getCachedAmountBig()
                    .equals(
                        HUGE.multiply(BigInteger.valueOf(100))
                            .add(BigInteger.valueOf(300))),
                "pump huge output truncated");
            gregtech.api.metatileentity.implementations.MTEHatchEnergy previous = parent.mEnergyHatches.get(0);
            parent.mEnergyHatches.set(
                0,
                (com.silvia.apeiron.common.machine.energy.MTEInfiniteEnergyHatch) tile(
                    ApeironMachines.INFINITE_ENERGY_HATCH_OFFSET,
                    owner).getMetaTileEntity());
            try {
                module.setEUVar(13);
                check(
                    module.checkProcessing_EM()
                        .wasSuccessful(),
                    "ordinary parent startup power was rejected");
                check(module.getEUVar() == 13, "startup power seed remained in the local EU buffer");
            } finally {
                parent.mEnergyHatches.set(0, previous);
            }
        } finally {
            if (old == null) SpacePumpingRecipes.RECIPES.remove(key);
            else SpacePumpingRecipes.RECIPES.put(key, old);
        }
    }

    private static void verifyMiner(TileEntitySpaceElevator parent, UUID owner) throws ReflectiveOperationException {
        Miner module = new Miner();
        module.setBaseMetaTileEntity(new BaseMetaTileEntity());
        module.initParameters();
        connect(module, parent);
        ((ModuleParallelParameter) field(module, "parallelParameter")).setBig(HUGE);
        MTEBoundlessMEOutputBus output = (MTEBoundlessMEOutputBus) tile(0, owner).getMetaTileEntity();
        module.mOutputBusses.add(output);
        MTEInfinitePatternInputAssembly input = (MTEInfinitePatternInputAssembly) tile(4, owner).getMetaTileEntity();
        check(
            input.addToBufferBig(
                0,
                Arrays.asList(
                    BigAEStackValues.copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), HUGE),
                    BigAEStackValues.copyWithSize(
                        AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1)),
                        HUGE.multiply(BigInteger.valueOf(825))))),
            "miner input setup failed");
        module.mDualInputHatches.add(input);
        GTRecipe recipe = GTRecipeBuilder.builder()
            .itemInputs(new ItemStack(Items.diamond))
            .itemOutputs(new ItemStack(Items.emerald))
            .duration(20)
            .eut(8)
            .metadata(IGRecipeMaps.SPACE_MINING_DATA, new SpaceMiningData("apeiron.verify", 0, 100, 1, 1, 10, 1))
            .build()
            .get();
        set(module, "prevRecipes", new WeightedAsteroidList(Stream.of(recipe)));
        set(module, "prevDistance", 0);
        set(module, "prevAvailDroneMask", 0);
        WirelessNetworkManager.setUserEU(owner, BigInteger.valueOf(480));
        input.beginRecipeProcessing();
        try {
            check(
                module.process(
                    input.inventories()
                        .next()
                        .getItemInputs(),
                    input.inventories()
                        .next()
                        .getFluidInputs(),
                    1,
                    new FluidStack(FluidRegistry.WATER, 825),
                    Integer.MAX_VALUE)
                    .wasSuccessful(),
                "miner rejected recipe");
        } finally {
            input.endRecipeProcessing();
        }
        check(
            state(module).getParallelsBig()
                .equals(BigInteger.valueOf(3)),
            "miner ignored batch energy budget");
        check(module.requiredData() == 30, "miner lost computation demand");
        check(
            input.getBuffers()
                .get(0)
                .getItemAmountBig()
                .equals(HUGE.subtract(BigInteger.valueOf(3))),
            "miner item debit changed");
        check(
            input.getBuffers()
                .get(0)
                .getFluidAmountBig()
                .equals(
                    HUGE.subtract(BigInteger.valueOf(3))
                        .multiply(BigInteger.valueOf(825))),
            "miner plasma debit changed");
        complete(module, owner);
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(BigInteger.valueOf(3)),
            "miner lost item output");
        GTRecipe unlimitedRecipe = GTRecipeBuilder.builder()
            .itemInputs(new ItemStack(Items.diamond))
            .itemOutputs(new ItemStack(Items.emerald))
            .duration(20)
            .eut(8)
            .metadata(IGRecipeMaps.SPACE_MINING_DATA, new SpaceMiningData("apeiron.verify", 0, 100, 1, 1, 0, 1))
            .build()
            .get();
        set(module, "prevRecipes", new WeightedAsteroidList(Stream.of(unlimitedRecipe)));
        BigInteger remaining = HUGE.subtract(BigInteger.valueOf(3));
        WirelessNetworkManager.setUserEU(owner, remaining.multiply(BigInteger.valueOf(160)));
        input.beginRecipeProcessing();
        try {
            check(
                module.process(
                    input.inventories()
                        .next()
                        .getItemInputs(),
                    input.inventories()
                        .next()
                        .getFluidInputs(),
                    1,
                    new FluidStack(FluidRegistry.WATER, 825),
                    Integer.MAX_VALUE)
                    .wasSuccessful(),
                "miner rejected exact huge batch");
        } finally {
            input.endRecipeProcessing();
        }
        check(
            state(module).getParallelsBig()
                .equals(remaining),
            "miner huge parallels truncated");
        check(
            input.getBuffers()
                .get(0)
                .getItemAmountBig()
                .signum() == 0
                && input.getBuffers()
                    .get(0)
                    .getFluidAmountBig()
                    .signum() == 0,
            "miner huge input debit truncated");
        complete(module, owner);
        check(
            output.getProvider()
                .getCachedAmountBig()
                .equals(HUGE),
            "miner huge output truncated");
    }

    private static void complete(TileEntityModuleBase module, UUID owner) throws ReflectiveOperationException {
        check(module.mMaxProgresstime == 1, "module ignored parent target duration");
        check(module.onRunningTick(null), "module wireless tick failed");
        check(
            WirelessNetworkManager.getUserEU(owner)
                .signum() == 0,
            "module did not charge exact parent account");
        Method finish = TTMultiblockBase.class.getDeclaredMethod("addClassicOutputs_EM");
        finish.setAccessible(true);
        finish.invoke(module);
        check(!state(module).isRunning(), "module did not finish output lifecycle");
    }

    private static WirelessRecipeState state(MTEMultiBlockBase module) {
        return ((BigWirelessController) module).getWirelessRecipeState();
    }

    private static void connect(TileEntityModuleBase module, TileEntitySpaceElevator parent)
        throws ReflectiveOperationException {
        set(module, "parent", parent);
        set(module, "isConnected", true);
    }

    private static ApeironMachineTile tile(int offset, UUID owner) {
        ApeironMachineTile tile = new ApeironMachineTile();
        tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
        tile.setOwnerUuid(owner);
        return tile;
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        return findField(object, name).get(object);
    }

    private static void set(Object object, String name, Object value) throws ReflectiveOperationException {
        findField(object, name).set(object, value);
    }

    private static Field findField(Object object, String name) throws ReflectiveOperationException {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static final class Elevator extends TileEntitySpaceElevator {

        Elevator() {
            super("apeiron.verify.elevator");
        }
    }

    private static final class Miner extends TileEntityModuleMinerT1 {

        Miner() {
            super("apeiron.verify.space_miner");
        }

        @Override
        protected int getAvailDroneMask(ItemStack[] inputs) {
            return 0;
        }

        @Override
        protected long getAvailableData_EM() {
            return 100;
        }

        long requiredData() {
            return eRequiredData;
        }
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new IllegalStateException("Space elevator verification: " + message);
    }
}
