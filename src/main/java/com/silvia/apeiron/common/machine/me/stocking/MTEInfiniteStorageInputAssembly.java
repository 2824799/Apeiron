// SPDX-License-Identifier: GPL-3.0-only
// Combined item/fluid ME input adapted from GT Not Leisure's SuperDualInputHatchME.
package com.silvia.apeiron.common.machine.me.stocking;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.me.helpers.AENetworkProxy;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;

public class MTEInfiniteStorageInputAssembly extends MTEInfiniteStorageInputBus implements IDualInputHatch {

    private final FluidPort fluidPort = new FluidPort();

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

    /** Both native input lists share one stocking session and one AE node. */
    public MTEInfiniteStorageInputHatch getFluidInput() {
        return fluidPort;
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

    private final class FluidPort extends MTEInfiniteStorageInputHatch {

        private FluidPort() {
            super(
                "apeiron.storage_assembly_fluid_port",
                MTEInfiniteStorageInputAssembly.this.mTier,
                new String[0],
                null);
            // Assigning a real base tile to a second MTE would replace the placed assembly.
            IGregTechTileEntity view = (IGregTechTileEntity) Proxy.newProxyInstance(
                IGregTechTileEntity.class.getClassLoader(),
                new Class<?>[] { IGregTechTileEntity.class },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        if (method.getName()
                            .equals("equals")) return proxy == args[0];
                        if (method.getName()
                            .equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName()
                            .equals("toString")) return "Apeiron storage assembly fluid view";
                    }
                    if (method.getName()
                        .equals("getMetaTileEntity")) return this;
                    if (method.getName()
                        .equals("setMetaTileEntity")) return null;
                    IGregTechTileEntity base = MTEInfiniteStorageInputAssembly.this.getBaseMetaTileEntity();
                    if (base == null) {
                        if (method.getName()
                            .equals("isDead")) return true;
                        throw new IllegalStateException("Storage assembly fluid port is not attached to a tile");
                    }
                    try {
                        return method.invoke(base, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
            setBaseMetaTileEntity(view);
        }

        @Override
        public StockingInputLogic getStockingInput() {
            return MTEInfiniteStorageInputAssembly.this.getStockingInput();
        }

        @Override
        public AENetworkProxy getProxy() {
            return MTEInfiniteStorageInputAssembly.this.getProxy();
        }

        @Override
        public boolean isValid() {
            return MTEInfiniteStorageInputAssembly.this.isValid();
        }
    }
}
