package com.silvia.apeiron.verification;

import com.silvia.apeiron.compat.CompatibilitySmoke;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;

/** Only installed into isolated verification profiles; never included in the released mod. */
@Mod(
    modid = "apeiron_verification",
    name = "Apeiron Verification",
    version = "1",
    dependencies = "required-after:apeiron")
public final class VerificationMod {

    @Mod.EventHandler
    public void verify(FMLPostInitializationEvent event) {
        CompatibilitySmoke.verify();
        if ("1".equals(System.getenv("APEIRON_VERIFY_EXIT"))) cpw.mods.fml.common.FMLCommonHandler.instance()
            .exitJava(0, false);
    }
}
