package com.silvia.apeiron.common.machine.tectech.verification;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.Fluid;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.common.integration.waila.WailaNBTBudget;
import com.silvia.apeiron.common.machine.energy.MachineWailaSnapshot;
import com.silvia.apeiron.common.machine.tectech.EyeOfHarmonyWailaDisplay;
import com.silvia.apeiron.common.machine.tectech.EyeOfHarmonyWailaSnapshot;

import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gtneioreplugin.plugin.block.BlockDimensionDisplay;
import gtneioreplugin.plugin.block.ModBlocks;
import mcp.mobius.waila.api.IWailaDataProvider;
import tectech.TecTech;
import tectech.recipe.EyeOfHarmonyRecipe;
import tectech.recipe.EyeOfHarmonyRecipeStorage;
import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;
import tectech.util.FluidStackLong;
import tectech.util.ItemStackLong;

/** Real HUD entry points for an unmodified Eye, including an idle recipe and an oversized native output list. */
public final class EyeOfHarmonyWailaSmoke {

    private EyeOfHarmonyWailaSmoke() {}

    @SuppressWarnings("unchecked")
    public static void verify() {
        EyeOfHarmonyRecipeStorage originalStorage = TecTech.eyeOfHarmonyRecipeStorage;
        try {
            Fixture eye = new Fixture();
            ItemStack planet = new ItemStack(ModBlocks.getBlock("Ow"));
            planet.setStackDisplayName("Misleading T10 planet name");
            eye.mInventory[eye.getControllerSlotIndex()] = planet;
            if (originalStorage == null) {
                // postInit runs before GT creates its recipe registry. Supply an isolated lookup without registering
                // recipes.
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                Field singleton = unsafeClass.getDeclaredField("theUnsafe");
                singleton.setAccessible(true);
                FixtureStorage lookup = (FixtureStorage) unsafeClass.getMethod("allocateInstance", Class.class)
                    .invoke(singleton.get(null), FixtureStorage.class);
                lookup.recipe = new EyeOfHarmonyRecipe(
                    new ArrayList<>(),
                    new BlockDimensionDisplay("Ow"),
                    1.5D,
                    10_000_000_000L,
                    20_000_000_000L,
                    20,
                    0,
                    0.8D);
                TecTech.eyeOfHarmonyRecipeStorage = lookup;
            }
            EyeOfHarmonyRecipe recipe = TecTech.eyeOfHarmonyRecipeStorage.recipeLookUp(planet);
            check(recipe != null, "fixture has no Eye recipe");
            Map<Fluid, Long> stored = (Map<Fluid, Long>) field("validFluidMap").get(eye);
            stored.put(Materials.Hydrogen.mGas, 1234L);
            stored.put(Materials.Helium.mGas, 2345L);
            stored.put(Materials.RawStarMatter.mFluid, 3456L);
            BigInteger energy = BigInteger.TEN.pow(30)
                .add(BigInteger.valueOf(17));
            field("usedEU").set(eye, energy.negate());
            field("outputEU_BigInt").set(eye, energy);
            check(eye.mInputHatches.isEmpty(), "fixture has an enhancement module");
            NBTTagCompound tag = snapshot(eye);
            String prefix = EyeOfHarmonyWailaSnapshot.PREFIX;
            check(
                tag.hasKey(prefix + "astralArrayAmount", 99) && tag.getLong(prefix + "astralArrayAmount") == 0,
                "native zero astral count is absent");
            check(tag.getLong(prefix + "parallelAmount") == 1, "native parallels are absent");
            check(tag.hasKey(prefix + "animationsEnabled"), "animation setting is absent");
            check(
                new BigInteger(tag.getByteArray(prefix + "usedEU")).equals(energy.negate()),
                "input EU is missing or rounded");
            check(
                new BigInteger(tag.getByteArray(prefix + "outputEU_BigInt")).equals(energy),
                "output EU is missing or rounded");
            check(
                tag.getLong("stored." + Materials.Hydrogen.mGas.getUnlocalizedName()) == 1234,
                "stored hydrogen is absent");
            NBTTagList rows = requirements(tag);
            check(rows.tagCount() == 2, "idle native Eye has no hydrogen/helium requirement");
            check(
                rows.getCompoundTagAt(0)
                    .getString("required")
                    .equals(Long.toString(recipe.getHydrogenRequirement())),
                "hydrogen requirement was guessed from the planet name");
            check(
                rows.getCompoundTagAt(1)
                    .getString("required")
                    .equals(Long.toString(recipe.getHeliumRequirement())),
                "helium requirement was guessed from the planet name");
            List<String> idleTooltip = EyeOfHarmonyWailaDisplay.updateOmni(Collections.emptyList(), tag);
            check(
                idleTooltip.size() == 3 && idleTooltip.stream()
                    .noneMatch(line -> line.contains("NaN")),
                "localized fluid requirements failed to render");

            field("parallelAmount").setLong(eye, 65536L);
            field("astralArrayAmount").setLong(eye, 8637L);
            rows = requirements(snapshot(eye));
            check(
                rows.tagCount() == 1 && rows.getCompoundTagAt(0)
                    .getString("fluid")
                    .equals(Materials.RawStarMatter.mFluid.getName()),
                "parallel Eye did not switch to stellar plasma");
            check(
                new BigInteger(
                    rows.getCompoundTagAt(0)
                        .getString("required")).signum()
                    > 0,
                "stellar requirement is zero");

            List<ItemStackLong> items = new ArrayList<>(
                Collections.nCopies(10000, new ItemStackLong(new ItemStack(Items.diamond), 17)));
            List<FluidStackLong> fluids = new ArrayList<>(
                Collections.nCopies(360, new FluidStackLong(Materials.RawStarMatter.getFluid(1), 23)));
            field("outputItems").set(eye, items);
            field("outputFluids").set(eye, fluids);
            field("starMatter").set(eye, new FluidStackLong(Materials.Universium.getMolten(1), 31));
            field("stellarPlasma").set(eye, fluids.get(0));
            field("recipeRunning").setBoolean(eye, true);
            field("successChance").setDouble(eye, 0.75);
            field("successfulParallelAmount").setLong(eye, 49152L);
            tag = snapshot(eye);
            check(requirements(tag).tagCount() == 0, "running Eye advertises already-consumed startup fluids");
            check(tag.getDouble(prefix + "recipeSuccessChance") == 0.75, "success chance disappeared");
            check(tag.getLong(prefix + "successfulParallelAmount") == 49152L, "successful parallels disappeared");
            NBTTagCompound preview = tag.getCompoundTag(prefix + "fluidOutput");
            check(
                preview.getLong(prefix + "numberOfFluids") == 360 && preview.getLong("359fluidAmount") == 23,
                "special output aliases disappeared");
            check(!preview.hasKey("0fluidAmount"), "complete native fluid output list leaked into the HUD");
            check(
                tag.getCompoundTag(prefix + "recipeStarMatter")
                    .getString("FluidName")
                    .equals(
                        Materials.Universium.getMolten(1)
                            .getFluid()
                            .getName()),
                "special output fluid identity disappeared");
            check(size(tag) < 16384, "Eye snapshot scales with all recipe outputs");
            check(
                items.size() == 10000 && fluids.size() == 360
                    && fluids.get(0).amount == 23
                    && stored.get(Materials.Hydrogen.mGas) == 1234L,
                "HUD changed the live recipe or internal fluid storage");

            field("recipeRunning").setBoolean(eye, false);
            eye.mInventory[eye.getControllerSlotIndex()] = null;
            tag = snapshot(eye);
            check(requirements(tag).tagCount() == 0, "empty controller slot has a fabricated requirement");
            List<String> tooltip = EyeOfHarmonyWailaDisplay.updateOmni(Collections.singletonList("idle"), tag);
            check(tooltip.equals(Collections.singletonList("idle")), "empty controller slot produces NaN rows");
            Apeiron.LOG.info(
                "Eye Waila verification passed: native save aliases, real idle fluid requirements, exact EU and bounded 10000-item/360-fluid preview");
        } catch (ReflectiveOperationException | java.io.IOException failure) {
            throw new IllegalStateException("Eye of Harmony Waila verification failed", failure);
        } finally {
            TecTech.eyeOfHarmonyRecipeStorage = originalStorage;
        }
    }

    private static NBTTagList requirements(NBTTagCompound tag) {
        return tag.getCompoundTag(EyeOfHarmonyWailaSnapshot.DISPLAY_TAG)
            .getTagList("requirements", 10);
    }

    private static NBTTagCompound snapshot(Fixture eye) throws ReflectiveOperationException {
        NBTTagCompound tag = new NBTTagCompound();
        TileEntity tile = (TileEntity) eye.getBaseMetaTileEntity();
        net.minecraft.entity.player.EntityPlayerMP viewer = com.silvia.apeiron.common.integration.waila.verification.MachineWailaCompatibilitySmoke
            .viewer();
        try {
            IWailaDataProvider omni = (IWailaDataProvider) Class.forName("me.exz.omniocular.waila.TileEntityHandler")
                .getDeclaredConstructor()
                .newInstance();
            tag = omni.getNBTData(viewer, tile, tag, null, 0, 0, 0);
        } catch (ClassNotFoundException absent) {
            check(MachineWailaSnapshot.write(viewer, tile, tag), "Eye did not use a lightweight snapshot");
        }
        return WailaNBTBudget.limit(tag);
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = MTEEyeOfHarmony.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static int size(NBTTagCompound tag) throws java.io.IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(tag, new DataOutputStream(bytes));
        return bytes.size();
    }

    private static final class Fixture extends MTEEyeOfHarmony {

        Fixture() {
            super("apeiron.verify.eye_waila");
            setBaseMetaTileEntity(new BaseMetaTileEntity());
        }

        @Override
        public void saveNBTData(NBTTagCompound tag) {
            throw new IllegalStateException("Eye HUD invoked a complete disk save");
        }
    }

    private static final class FixtureStorage extends EyeOfHarmonyRecipeStorage {

        private EyeOfHarmonyRecipe recipe;

        @Override
        public EyeOfHarmonyRecipe recipeLookUp(ItemStack stack) {
            return recipe;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
