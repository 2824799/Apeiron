package com.silvia.apeiron.client;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.oredict.OreDictionary;

import com.silvia.apeiron.common.lifecycle.CommonProxy;
import com.silvia.apeiron.common.machine.me.input.pattern.ApeironPatternItems;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        MinecraftForge.EVENT_BUS.register(new ApeironBlockTooltips());
        if (Loader.isModLoaded("NotEnoughItems")) {
            codechicken.nei.api.API.hideItem(new ItemStack(ApeironPatternItems.multipliedPattern));
            codechicken.nei.api.API.hideItem(new ItemStack(ApeironMachines.block, 1, OreDictionary.WILDCARD_VALUE));
        }
    }
}
