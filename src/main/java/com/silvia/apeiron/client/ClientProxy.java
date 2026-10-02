package com.silvia.apeiron.client;

import net.minecraft.item.ItemStack;

import com.silvia.apeiron.common.lifecycle.CommonProxy;
import com.silvia.apeiron.common.machine.me.input.pattern.ApeironPatternItems;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        if (Loader.isModLoaded("NotEnoughItems"))
            codechicken.nei.api.API.hideItem(new ItemStack(ApeironPatternItems.multipliedPattern));
    }
}
