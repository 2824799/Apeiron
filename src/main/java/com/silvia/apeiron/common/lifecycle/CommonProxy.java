package com.silvia.apeiron.common.lifecycle;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.Tags;
import com.silvia.apeiron.common.machine.registration.ApeironMachines;
import com.silvia.apeiron.config.ApeironConfig;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        ApeironConfig.loadFromDirectory(event.getModConfigurationDirectory());
        Apeiron.LOG.info("Apeiron {} is loading.", Tags.VERSION);
        ApeironMachines.registerBlock();
        com.silvia.apeiron.common.block.crafting.ApeironCraftingBlocks.register();
        com.silvia.apeiron.common.machine.me.input.pattern.ApeironPatternItems.register();
    }

    public void init(FMLInitializationEvent event) {
        ApeironMachines.registerMachines();
        appeng.api.AEApi.instance()
            .registries()
            .interfaceTerminal()
            .register(com.silvia.apeiron.common.machine.me.input.MTEInfinitePatternInputAssembly.class);
    }

    public void postInit(FMLPostInitializationEvent event) {
        ApeironMachines.validateRegisteredReservation();
    }
}
