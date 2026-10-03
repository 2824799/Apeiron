package com.silvia.apeiron.common.machine.energy.verification;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Random;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.ValueSyncHandler;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.client.gui.sync.ChunkedNbtSyncValue;
import com.silvia.apeiron.common.machine.energy.MachineRecipeDisplay;
import com.silvia.apeiron.common.machine.energy.WirelessWailaDisplay;
import com.silvia.apeiron.common.machine.output.BigMachineOutputQueue;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;

import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace;

/** Runs against transformed GT classes, including the native callback that used to echo oversized lists. */
public final class MachineDisplaySmoke {

    private MachineDisplaySmoke() {}

    public static void verify() {
        try {
            MTEElectricBlastFurnace machine = new MTEElectricBlastFurnace("apeiron.verification.display");
            machine.setBaseMetaTileEntity(new BaseMetaTileEntity());
            machine.mOutputItems = new ItemStack[600];
            machine.mMaxProgresstime = 40;
            BigMachineOutputQueue ledger = new BigMachineOutputQueue();
            BigInteger huge = BigInteger.TEN.pow(600)
                .add(BigInteger.valueOf(17));
            Random random = new Random(71);
            for (int i = 0; i < machine.mOutputItems.length; i++) {
                ItemStack item = new ItemStack(Items.emerald, 64);
                item.setTagCompound(new NBTTagCompound());
                byte[] content = new byte[1024];
                random.nextBytes(content);
                item.getTagCompound()
                    .setByteArray("payload", content);
                machine.mOutputItems[i] = item;
                ledger.addItem(item, huge.add(BigInteger.valueOf(i)));
            }
            check(
                CompressedStreamTools.compress(MachineRecipeDisplay.snapshot(machine)).length > 32767,
                "native recipe fixture did not exceed the old packet limit");
            MTEMultiBlockBaseGui<?> gui = new MTEMultiBlockBaseGui<>(machine);
            PanelSyncManager sync = new PanelSyncManager(new ModularSyncManager(true), true);
            Method register = MTEMultiBlockBaseGui.class
                .getDeclaredMethod("registerSyncValues", PanelSyncManager.class);
            register.setAccessible(true);
            register.invoke(gui, sync);
            check(
                sync.getSyncHandlerFromMapKey("apeiron_recipe_output_display:0") instanceof ChunkedNbtSyncValue,
                "native recipes were not routed through the fragment receiver");
            Method create = MTEMultiBlockBaseGui.class
                .getDeclaredMethod("createRecipeInfoWidget", PanelSyncManager.class);
            create.setAccessible(true);
            create.invoke(gui, sync);
            for (String key : new String[] { "maxProgressTime:0", "itemOutput:0", "fluidOutput:0" }) {
                check(
                    ((ValueSyncHandler<?, ?>) sync.getSyncHandlerFromMapKey(key)).getChangeListener() == null,
                    "native recipe echo callback was still registered: " + key);
            }
            WirelessRecipeState state = new WirelessRecipeState();
            state.start(huge, huge, 40, ledger);
            NBTTagCompound hover = new NBTTagCompound();
            WirelessWailaDisplay.write(hover, state, 10000);
            check(
                hover.getTagList("ApeironWirelessOutputRows", 10)
                    .tagCount() == 3,
                "HUD did not bound the displayed rows");
            check(hover.getInteger("ApeironWirelessOutputTypes") == 600, "HUD lost the omitted type count");
            check(
                !hover.hasKey("ApeironWirelessOutputDisplay") && CompressedStreamTools.compress(hover).length < 4096,
                "HUD serialized the full output ledger or item payloads");
            check(
                ledger.getItemAmountBig()
                    .signum() > 0,
                "display consumed outputs");
            state.complete();
            BigMachineOutputQueue next = new BigMachineOutputQueue();
            next.addItem(new ItemStack(Items.diamond), BigInteger.ONE);
            state.start(BigInteger.ONE, BigInteger.ONE, 1, next);
            check(
                state.getHudOutputs()
                    .size() == 1,
                "HUD cache retained the previous recipe");
            ClassLoader loader = MachineDisplaySmoke.class.getClassLoader();
            if (loader.getResource("me/exz/omniocular/waila/TileEntityHandler.class") != null) {
                Class.forName("me.exz.omniocular.waila.TileEntityHandler", true, loader);
            }
            Apeiron.LOG.info(
                "Machine display verification passed: native echo callbacks removed, 600 output types, bounded exact HUD and recipe cache refresh");
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw new IllegalStateException("Machine display verification failed", error);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Machine display verification: " + message);
    }
}
