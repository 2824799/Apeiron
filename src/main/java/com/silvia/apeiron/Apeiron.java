package com.silvia.apeiron;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.silvia.apeiron.common.lifecycle.CommonProxy;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = Apeiron.MODID, name = Apeiron.NAME, version = Tags.VERSION, acceptedMinecraftVersions = "[1.7.10]")
public final class Apeiron {

    public static final String MODID = "apeiron";
    public static final String NAME = "Apeiron";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "com.silvia.apeiron.client.ClientProxy",
        serverSide = "com.silvia.apeiron.common.lifecycle.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }
}
