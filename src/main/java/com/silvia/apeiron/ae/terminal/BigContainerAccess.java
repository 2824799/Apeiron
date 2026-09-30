package com.silvia.apeiron.ae.terminal;

import net.minecraft.entity.player.EntityPlayerMP;

/** Public bridge for AEBaseContainer's protected held-stack synchronization hook. */
public interface BigContainerAccess {

    void apeiron$updateHeld(EntityPlayerMP player);
}
