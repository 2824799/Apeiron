package com.silvia.apeiron.common.machine.me.input.verification;

import java.lang.reflect.Method;

import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.client.gui.machine.me.input.InfinitePatternInputGui;
import com.silvia.apeiron.client.gui.machine.me.input.InfiniteStorageInputGui;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import gregtech.api.interfaces.IConfigurationCircuitSupport;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.common.modularui2.widget.GhostCircuitSlotWidget;

/** The actual inherited GT widgets and sync handlers used by all three Apeiron input GUIs. */
public final class InputCircuitGuiSmoke {

    private InputCircuitGuiSmoke() {}

    public static void verify() {
        try {
            for (int offset : new int[] { ApeironMachines.PATTERN_INPUT_ASSEMBLY_OFFSET,
                ApeironMachines.STORAGE_INPUT_BUS_OFFSET, ApeironMachines.STORAGE_INPUT_ASSEMBLY_OFFSET }) {
                ApeironMachineTile tile = new ApeironMachineTile();
                tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(offset));
                MTEHatch machine = (MTEHatch) tile.getMetaTileEntity();
                Object gui = machine instanceof MTEInfinitePatternInputAssembly source
                    ? new InfinitePatternInputGui(source)
                    : new InfiniteStorageInputGui(machine);
                Method enabled = method(gui.getClass(), "doesAddCircuitSlot");
                if (!Boolean.TRUE.equals(enabled.invoke(gui)))
                    throw new IllegalStateException("Circuit hidden in input GUI");
                PanelSyncManager sync = new PanelSyncManager(new ModularSyncManager(true), true);
                Widget<?> widget = (Widget<?>) method(gui.getClass(), "createCircuitSlot", PanelSyncManager.class)
                    .invoke(gui, sync);
                if (!(widget instanceof GhostCircuitSlotWidget))
                    throw new IllegalStateException("Input GUI replaced native circuit widget");
                IntSyncValue index = sync.findSyncHandler("ghostCircuitIndex", IntSyncValue.class);
                index.setIntValue(15, true, false);
                IConfigurationCircuitSupport circuits = (IConfigurationCircuitSupport) machine;
                if (machine.getStackInSlot(circuits.getCircuitSlot())
                    .getItemDamage() != 16)
                    throw new IllegalStateException("Native circuit selection did not reach input machine");
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Global input circuit GUI verification failed", error);
        }
        Apeiron.LOG.info("Global input circuit GUI verification passed: three actual native widgets and selector sync");
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        for (Class<?> owner = type; owner != null; owner = owner.getSuperclass()) {
            try {
                Method method = owner.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(name);
    }
}
