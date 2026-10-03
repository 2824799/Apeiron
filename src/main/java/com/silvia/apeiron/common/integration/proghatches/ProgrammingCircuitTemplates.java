package com.silvia.apeiron.common.integration.proghatches;

import java.util.Collection;
import java.util.Collections;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.render.TextureFactory;
import reobf.proghatches.block.BlockIOHub;
import reobf.proghatches.gt.metatileentity.ProgrammingCircuitProviderPrefabricated;
import reobf.proghatches.item.ItemProgrammingCircuit;
import reobf.proghatches.main.MyMod;

/** Loaded only when Programmable Hatches is installed; its original template API remains authoritative. */
public final class ProgrammingCircuitTemplates {

    private ProgrammingCircuitTemplates() {}

    public static ITexture[] textures(ITexture base, boolean active) {
        return new ITexture[] { base,
            TextureFactory.of(
                MyMod.iohub,
                active ? BlockIOHub.magicNO_provider_active_overlay : BlockIOHub.magicNO_provider_overlay) };
    }

    public static Collection<ItemStack> expand(ItemStack input) {
        if (input != null && input.getItem() == Item.getItemFromBlock(GregTechAPI.sBlockMachines)) {
            int id = input.getItemDamage();
            IMetaTileEntity meta = id >= 0 && id < GregTechAPI.METATILEENTITIES.length
                ? GregTechAPI.METATILEENTITIES[id]
                : null;
            if (meta instanceof ProgrammingCircuitProviderPrefabricated) {
                Collection<ItemStack> circuits = ((ProgrammingCircuitProviderPrefabricated) meta).getCircuit();
                return circuits == null ? Collections.emptyList() : circuits;
            }
        }
        if (input != null && input.getItem() == MyMod.progcircuit) return Collections.singletonList(input.copy());
        return Collections.singletonList(ItemProgrammingCircuit.wrap(input, 1));
    }
}
