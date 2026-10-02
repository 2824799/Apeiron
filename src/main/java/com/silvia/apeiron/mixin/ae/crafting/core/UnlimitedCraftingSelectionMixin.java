package com.silvia.apeiron.mixin.ae.crafting.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.silvia.apeiron.ae.crafting.core.UnlimitedCraftingCPU;
import com.silvia.apeiron.ae.crafting.core.UnlimitedCraftingSelection;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerCraftConfirm;

@Mixin(value = ContainerCraftConfirm.class, remap = false)
public abstract class UnlimitedCraftingSelectionMixin implements UnlimitedCraftingSelection {

    @Unique
    @GuiSync(30000)
    public boolean apeiron$selectedStorageUnlimited;
    @Unique
    @GuiSync(30001)
    public boolean apeiron$selectedParallelUnlimited;

    @Override
    public boolean isSelectedStorageUnlimited() {
        return apeiron$selectedStorageUnlimited;
    }

    @Override
    public boolean isSelectedParallelUnlimited() {
        return apeiron$selectedParallelUnlimited;
    }

    @Inject(method = "onCPUUpdate", at = @At("TAIL"))
    private void apeiron$selection(ICraftingCPU cpu, CallbackInfo ci) {
        apeiron$selectedStorageUnlimited = cpu instanceof UnlimitedCraftingCPU
            && ((UnlimitedCraftingCPU) cpu).isCraftingStorageUnlimited();
        apeiron$selectedParallelUnlimited = cpu instanceof UnlimitedCraftingCPU
            && ((UnlimitedCraftingCPU) cpu).isCraftingParallelUnlimited();
    }
}
