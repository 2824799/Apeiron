package com.silvia.apeiron.common.integration.waila.verification;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.common.integration.waila.WailaNBTBudget;
import com.silvia.apeiron.common.machine.block.ApeironMachineTile;
import com.silvia.apeiron.common.machine.energy.MachineWailaSnapshot;
import com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly;
import com.silvia.apeiron.common.machine.me.input.storage.BigPatternBuffer;
import com.silvia.apeiron.config.ApeironConfig;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.network.WailaPacketHandler;

/** Exercises actual transformed OmniOcular and Waila entry points without a user world. */
public final class WailaSnapshotSmoke {

    private WailaSnapshotSmoke() {}

    private static final class Assembly extends MTEInfinitePatternInputAssembly {

        private boolean rejectSaves;

        private Assembly() {
            super("apeiron.verification.waila_input", 10, new String[0], null);
            ApeironMachineTile tile = new ApeironMachineTile();
            tile.setInitialValuesAsNBT(null, (short) ApeironConfig.getMachineId(4));
            setBaseMetaTileEntity(tile);
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void saveNBTData(NBTTagCompound tag) {
            check(!rejectSaves, "Waila invoked the complete pattern assembly disk save");
            super.saveNBTData(tag);
        }
    }

    private static final class NativeBus extends MTEHatchInputBus {

        private NativeBus() {
            super("apeiron.verification.waila_native_bus", 1, 64, new String[0], null);
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        @Override
        public void saveNBTData(NBTTagCompound tag) {
            throw new IllegalStateException("Waila invoked a native bus disk save");
        }
    }

    public static void verify() {
        try {
            Assembly source = new Assembly();
            ItemStack pattern = AEApi.instance()
                .definitions()
                .items()
                .encodedPattern()
                .maybeStack(1)
                .get();
            NBTTagCompound patternTag = new NBTTagCompound();
            patternTag.setByteArray("largePrivatePayload", new byte[32768]);
            pattern.setTagCompound(patternTag);
            Arrays.fill(source.mInventory, 0, 360, pattern);
            NBTTagCompound saved = new NBTTagCompound();
            source.saveNBTData(saved);
            int saveBytes = size(saved);
            source.rejectSaves = true;
            IWailaDataProvider omni = (IWailaDataProvider) Class.forName("me.exz.omniocular.waila.TileEntityHandler")
                .getDeclaredConstructor()
                .newInstance();
            NBTTagCompound snapshot = readOmni(omni, (TileEntity) source.getBaseMetaTileEntity());
            check(!snapshot.hasKey("ApeironPatternInput"), "snapshot contains persisted recipes and inventory");
            check(
                snapshot.getTagList("Inventory", 10)
                    .tagCount() == 0,
                "snapshot contains encoded patterns");
            check(
                snapshot.getTagList("ApeironBufferStatus", 10)
                    .tagCount() == 24,
                "buffer statuses disappeared");
            int emptyBytes = size(snapshot);
            check(saveBytes > emptyBytes * 1000L, "packet still scales with the 360 encoded pattern payloads");

            BigInteger huge = BigInteger.TEN.pow(600)
                .add(BigInteger.valueOf(17));
            List<IAEStack<?>> ingredients = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                ItemStack item = new ItemStack(Items.dye, 1, i);
                item.setTagCompound(patternTag);
                ingredients.add(BigAEStackValues.copyWithSize(AEItemStack.create(item), huge));
            }
            for (BigPatternBuffer buffer : source.getBuffers()) {
                buffer.assign(0, pattern, Arrays.asList(new ItemStack(Items.iron_ingot)));
                buffer.setRecipeInputs(ingredients);
                check(buffer.add(ingredients, BigInteger.valueOf(2)), "buffer setup failed");
                buffer.setLocked(true);
            }
            snapshot = readOmni(omni, (TileEntity) source.getBaseMetaTileEntity());
            NBTTagList statuses = snapshot.getTagList("ApeironBufferStatus", 10);
            check(statuses.tagCount() == 24, "busy buffer states disappeared");
            NBTTagCompound first = statuses.getCompoundTagAt(0);
            check(first.getBoolean("locked") && first.getBoolean("recipe"), "lock/recipe state disappeared");
            check(
                first.getString("copies")
                    .equals("2"),
                "available batches changed");
            check(
                first.getTagList("ingredients", 10)
                    .tagCount() == 8,
                "recipe preview is not bounded");
            check(first.getInteger("moreIngredients") == 7, "omitted ingredients not reported");
            check(
                !first.getTagList("ingredients", 10)
                    .getCompoundTagAt(0)
                    .hasKey("tag"),
                "raw item NBT leaked");
            List<String> tooltip = new ArrayList<>();
            MTEInfinitePatternInputAssembly.addBufferStatusTooltip(tooltip, snapshot);
            check(
                tooltip.stream()
                    .anyMatch(line -> line.contains("1.0E600")),
                "large counts disappeared");
            check(
                pattern.getTagCompound()
                    .getByteArray("largePrivatePayload").length == 32768,
                "live pattern changed");
            check(
                source.getBuffers()
                    .get(0)
                    .getAmountBig(ingredients.get(0))
                    .equals(huge.multiply(BigInteger.valueOf(2))),
                "Waila modified buffered materials");
            int activeBytes = size(snapshot);
            check(activeBytes < WailaNBTBudget.MAX_BYTES, "busy buffer packet exceeds its budget");

            NativeBus bus = new NativeBus();
            bus.mInventory[0] = new ItemStack(Items.diamond);
            NBTTagCompound ordinary = readOmni(omni, (TileEntity) bus.getBaseMetaTileEntity());
            check(
                ordinary.getTagList("Inventory", 10)
                    .tagCount() == 1,
                "native bus lost its item preview");
            check(ordinary.hasKey("mStoredEnergy") && ordinary.hasKey("mWorks"), "generic operating aliases missing");
            check(
                !MachineWailaSnapshot.write(null, new TileEntity(), new NBTTagCompound()),
                "non-GT tile path changed");
            verifyCodec();
            Apeiron.LOG.info(
                "Waila snapshot verification passed: 360-pattern save {} bytes, idle HUD {} bytes, 24 busy buffers {} bytes; "
                    + "transformed OmniOcular skips disk saves for Apeiron and native GT, and Waila codec bounds arbitrary providers",
                saveBytes,
                emptyBytes,
                activeBytes);
        } catch (ReflectiveOperationException | java.io.IOException failure) {
            throw new IllegalStateException("Waila snapshot verification failed", failure);
        }
    }

    private static NBTTagCompound readOmni(IWailaDataProvider provider, TileEntity tile) {
        return provider.getNBTData(null, tile, new NBTTagCompound(), null, 0, 0, 0);
    }

    private static void verifyCodec() throws java.io.IOException {
        NBTTagCompound providerTag = new NBTTagCompound();
        providerTag.setString("id", "external.mod.machine");
        providerTag.setInteger("WailaX", 42);
        providerTag.setByteArray("hugeData", new byte[4 * 1024 * 1024]);
        ByteBuf packet = Unpooled.buffer();
        try {
            WailaPacketHandler.INSTANCE.writeNBT(packet, providerTag);
            NBTTagCompound received = WailaPacketHandler.INSTANCE.readNBT(packet);
            check(received.getBoolean("ApeironWailaTruncated"), "packet codec budget mixin was not applied");
            check(received.getInteger("WailaX") == 42, "bounded packet lost its target");
            check(size(received) <= WailaNBTBudget.MAX_BYTES, "codec transmitted an oversized payload");
            check(providerTag.getByteArray("hugeData").length == 4 * 1024 * 1024, "codec modified a provider tag");
        } finally {
            packet.release();
        }
    }

    private static int size(NBTTagCompound tag) throws java.io.IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(tag, new DataOutputStream(bytes));
        return bytes.size();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
