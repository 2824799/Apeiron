package com.silvia.apeiron.ae.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageInterceptor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

/** Exact-count companion for AE2 storage interceptors. */
public interface BigStorageInterceptor extends IStorageInterceptor {

    IAEStack<?> injectItemsBig(IAEStack<?> input, Actionable mode, BaseActionSource source);

    default IAEItemStack injectItemsBig(IAEItemStack input, Actionable mode, BaseActionSource source) {
        return (IAEItemStack) injectItemsBig((IAEStack<?>) input, mode, source);
    }
}
