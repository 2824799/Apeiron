// SPDX-License-Identifier: GPL-3.0-only
// Combined item/fluid ME input adapted from GT Not Leisure's SuperDualInputHatchME.
package com.silvia.apeiron.common.machine.me.stocking;

import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;

public class MTEInfiniteStorageInputAssembly extends MTEInfiniteStorageInputBus implements IDualInputHatch {

    private final IDualInputInventory inventory = new IDualInputInventory() {

        @Override
        public boolean isEmpty() {
            return getItemInputs().length == 0 && getFluidInputs().length == 0;
        }

        @Override
        public ItemStack[] getItemInputs() {
            return getStockingInput().itemViews();
        }

        @Override
        public FluidStack[] getFluidInputs() {
            return getStockingInput().fluidViews();
        }
    };

    public MTEInfiniteStorageInputAssembly(int id, String name, String localName) {
        super(id, name, localName, StockingInputLogic.Kind.MIXED);
    }

    protected MTEInfiniteStorageInputAssembly(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, description, textures, StockingInputLogic.Kind.MIXED);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEInfiniteStorageInputAssembly(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    protected String typeKey() {
        return "infinite_storage_input_assembly";
    }

    @Override
    public Iterator<? extends IDualInputInventory> inventories() {
        return Collections.singletonList(inventory)
            .iterator();
    }

    @Override
    public Optional<IDualInputInventory> getFirstNonEmptyInventory() {
        return inventory.isEmpty() ? Optional.empty() : Optional.of(inventory);
    }

    @Override
    public boolean supportsFluids() {
        return true;
    }

    @Override
    public ItemStack[] getSharedItems() {
        return new ItemStack[0];
    }
}
