package com.silvia.apeiron.mixin.ae.stack;

import net.minecraft.entity.player.EntityPlayerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.silvia.apeiron.ae.terminal.BigContainerAccess;
import com.silvia.apeiron.ae.sync.BigSyncRegistrarAccess;

import appeng.container.AEBaseContainer;
import appeng.container.sync.SyncRegistrar;

/** Exposes one protected AE container operation to the exact terminal action bridge. */
@Mixin(value = AEBaseContainer.class, remap = false)
public abstract class AEBaseContainerBigMixin implements BigContainerAccess, BigSyncRegistrarAccess {

    @Shadow
    protected abstract void updateHeld(EntityPlayerMP player);

    @Shadow
    protected abstract SyncRegistrar syncRegistrar();

    @Override
    public void apeiron$updateHeld(final EntityPlayerMP player) {
        this.updateHeld(player);
    }

    @Override
    public SyncRegistrar apeiron$syncRegistrar() {
        return this.syncRegistrar();
    }
}
