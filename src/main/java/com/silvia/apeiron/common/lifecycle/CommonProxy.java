package com.silvia.apeiron.common.lifecycle;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.Tags;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Apeiron.LOG.info("Apeiron {} is loading.", Tags.VERSION);
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}
}
