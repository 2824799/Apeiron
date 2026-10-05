package com.silvia.apeiron.common.integration.waila.verification;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.common.integration.waila.MachineWailaAliases;
import com.silvia.apeiron.common.machine.energy.MachineWailaSnapshot;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Same-class failures are checked on detached stock machines, not only on an Apeiron machine fixture. */
public final class MachineWailaCompatibilitySmoke {

    private MachineWailaCompatibilitySmoke() {}

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void verify() {
        try {
            IMetaTileEntity heater = machine(9408);
            set(heater, "currentTemperature", 9876L);
            set(heater, "cyclesCompleted", 2);
            NBTTagCompound tag = snapshot(heater);
            check(
                tag.getString("id")
                    .equals("BaseMetaTileEntity"),
                "OmniOcular lost the machine script identifier");
            check(
                tag.getLong("mCurrentTemperature") == 9876L && tag.getInteger("mCyclesCompleted") == 2,
                "plasma heater lost its aliased temperature/cycles");
            check(
                !tag.getString("mCycleState")
                    .isEmpty(),
                "plasma heater lost its named cycle state");

            IMetaTileEntity flocculation = machine(9405);
            set(flocculation, "inputFluidConsumed", 123456L);
            check(
                snapshot(flocculation).getLong("mInputFluidConsumed") == 123456L,
                "flocculation lost its fluid-consumption alias");

            IMetaTileEntity degasser = machine(9412);
            Object signal = get(degasser, "controlSignal");
            set(signal, "signal", (byte) 11);
            Map<Fluid, FluidStack> fluids = (Map<Fluid, FluidStack>) get(degasser, "insertedStuffThisCycle");
            fluids.put(Materials.Helium.mGas, Materials.Helium.getGas(123));
            tag = snapshot(degasser);
            check(tag.getByte("controlSignal") == 11, "degasser lost its control signal");
            check(
                tag.getCompoundTag("insertedFluidMap")
                    .getCompoundTag(Materials.Helium.mGas.getName())
                    .getInteger("Amount") == 123,
                "degasser lost its inserted fluid identity/amount");
            check(fluids.get(Materials.Helium.mGas).amount == 123, "HUD consumed degasser inputs");

            IMetaTileEntity baryonic = machine(9414);
            List<ItemStack> catalysts = (List<ItemStack>) get(baryonic, "insertedCatalysts");
            catalysts.add(new ItemStack(Items.diamond));
            tag = snapshot(baryonic);
            check(
                tag.getCompoundTag("insertedItems")
                    .hasKey("0"),
                "baryonic unit lost its inserted catalyst preview");
            check(catalysts.size() == 1 && catalysts.get(0).stackSize == 1, "HUD consumed baryonic catalysts");

            IMetaTileEntity slave = machine(2716);
            set(slave, "masterSet", true);
            set(slave, "masterX", 12);
            set(slave, "masterY", 34);
            set(slave, "masterZ", 56);
            tag = snapshot(slave);
            check(
                tag.getCompoundTag("master")
                    .getInteger("x") == 12
                    && tag.getCompoundTag("master")
                        .getInteger("z") == 56,
                "linked native hatch lost its source coordinates");

            IMetaTileEntity yotta = machine(32014);
            set(yotta, "mFluid", Materials.Helium.getGas(1));
            set(yotta, "mStorage", java.math.BigInteger.TEN.pow(30));
            set(yotta, "mStorageCurrent", java.math.BigInteger.valueOf(123456789));
            tag = snapshot(yotta);
            check(
                tag.getString("mStorage")
                    .equals(
                        java.math.BigInteger.TEN.pow(30)
                            .toString()),
                "Yotta capacity has the wrong NBT representation");
            check(
                tag.getString("mFluidName")
                    .equals(Materials.Helium.mGas.getName()),
                "Yotta fluid name disappeared");

            IMetaTileEntity bus = machine(2710);
            Object provider = get(bus, "provider");
            // The provider serializer uses the same public configuration key across supported GT versions.
            Field connection = MachineWailaAliases.field(provider.getClass(), "additionalConnection");
            if (connection != null) connection.setBoolean(provider, true);
            tag = snapshot(bus);
            check(tag.hasKey("additionalConnection"), "delegated ME output connection settings disappeared");

            // Inventory previews cover a full stock battery buffer, and never include a storage cell ledger.
            IMetaTileEntity battery = new gregtech.api.metatileentity.implementations.MTEHatchInputBus(
                "apeiron.verify.hud_inventory",
                1,
                16,
                new String[0],
                null);
            battery.setBaseMetaTileEntity(new BaseMetaTileEntity());
            for (int i = 0; i < 16; i++) {
                ItemStack item = new ItemStack(Items.diamond);
                NBTTagCompound data = new NBTTagCompound();
                data.setLong("GT.ItemCharge", 100L + i);
                data.setByteArray("privateInventory", new byte[65536]);
                item.setTagCompound(data);
                battery.getRealInventory()[i] = item;
            }
            tag = snapshot(battery);
            check(
                tag.getTagList("Inventory", 10)
                    .tagCount() == 16,
                "stock battery-size inventory was cut to five entries");
            NBTTagCompound charge = tag.getTagList("Inventory", 10)
                .getCompoundTagAt(15)
                .getCompoundTag("tag");
            check(
                charge.getLong("GT.ItemCharge") == 115 && !charge.hasKey("privateInventory"),
                "charge preview retained a complete private inventory");
            check(
                battery.getRealInventory()[15].getTagCompound()
                    .getByteArray("privateInventory").length == 65536,
                "HUD changed live item NBT");
            Apeiron.LOG.info(
                "Common machine HUD verification passed: purification aliases/state/fluids/catalysts, hatch links, Yotta capacity, delegated ME settings and 16-slot charge preview");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Common machine Waila verification failed", failure);
        }
    }

    private static IMetaTileEntity machine(int id) {
        IMetaTileEntity template = GregTechAPI.METATILEENTITIES[id];
        check(template != null, "stock HUD fixture missing: " + id);
        BaseMetaTileEntity tile = new BaseMetaTileEntity();
        IMetaTileEntity machine = template.newMetaEntity(tile);
        machine.setBaseMetaTileEntity(tile);
        return machine;
    }

    private static NBTTagCompound snapshot(IMetaTileEntity machine) {
        NBTTagCompound tag = new NBTTagCompound();
        check(
            MachineWailaSnapshot.write(viewer(), (TileEntity) machine.getBaseMetaTileEntity(), tag),
            "missing machine snapshot");
        return tag;
    }

    public static net.minecraft.entity.player.EntityPlayerMP viewer() {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field singleton = unsafeClass.getDeclaredField("theUnsafe");
            singleton.setAccessible(true);
            net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) unsafeClass
                .getMethod("allocateInstance", Class.class)
                .invoke(singleton.get(null), net.minecraft.entity.player.EntityPlayerMP.class);
            for (Field field : net.minecraft.entity.Entity.class.getDeclaredFields()) {
                if (field.getType() != java.util.UUID.class
                    || java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                field.set(player, java.util.UUID.fromString("01000000-0000-0000-0000-000000000001"));
            }
            return player;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot create detached Waila viewer", failure);
        }
    }

    private static Object get(Object object, String name) throws IllegalAccessException {
        Field field = MachineWailaAliases.field(object.getClass(), name);
        check(field != null, "HUD fixture field missing: " + name);
        return field.get(object);
    }

    private static void set(Object object, String name, Object value) throws IllegalAccessException {
        Field field = MachineWailaAliases.field(object.getClass(), name);
        check(field != null, "HUD fixture field missing: " + name);
        field.set(object, value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
