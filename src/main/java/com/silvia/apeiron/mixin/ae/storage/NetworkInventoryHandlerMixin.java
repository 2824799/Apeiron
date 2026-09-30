package com.silvia.apeiron.mixin.ae.storage;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.storage.BigMEInventory;
import com.silvia.apeiron.ae.stack.BigAEStackValues;

import com.silvia.apeiron.ae.storage.BigIMEInventory;
import com.silvia.apeiron.ae.storage.BigMEInventories;
import com.silvia.apeiron.ae.storage.UnsupportedBigInventoryException;

import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.storage.NetworkInventoryHandler;

/** Preserve exact counts while AE2 distributes input and output across network handlers. */
@Mixin(value = NetworkInventoryHandler.class, remap = false)
public abstract class NetworkInventoryHandlerMixin implements BigIMEInventory, BigMEInventory {

    @Shadow(remap = false)
    private boolean diveList(NetworkInventoryHandler<?> networkInventoryHandler, Actionable type) {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @Shadow(remap = false)
    private boolean testPermission(BaseActionSource source, SecurityPermissions permission) {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @Shadow(remap = false)
    private void surface(NetworkInventoryHandler<?> networkInventoryHandler, Actionable type) {
        throw new AssertionError("mixin shadow was not replaced");
    }

    @SuppressWarnings("unchecked")
    private NetworkInventoryHandler apeiron$self() {
        return (NetworkInventoryHandler) (Object) this;
    }

    @SuppressWarnings("unchecked")
    private List<IMEInventoryHandler<IAEStack>> apeiron$handlers() {
        return (List<IMEInventoryHandler<IAEStack>>) (List<?>) apeiron$self().getHandlers();
    }

    private boolean apeiron$hasItem(IMEInventoryHandler<IAEStack> inventory, IAEStack input,
        BaseActionSource source) {
        try {
            if (inventory.isPrioritized(input)) return true;
            if (BigAEStackValues.isBig(input) && !(inventory instanceof BigMEInventory)) {
                IAEStack probe = BigAEStackValues.copyWithSize(input, BigInteger.ONE);
                return inventory.extractItems(probe, Actionable.SIMULATE, source) != null;
            }
            return BigMEInventories.extractItemsBig(inventory, input, Actionable.SIMULATE, source) != null;
        } catch (UnsupportedBigInventoryException ignored) {
            return false;
        }
    }

    private IAEStack apeiron$inject(IMEInventoryHandler<IAEStack> inventory, IAEStack input,
        Actionable mode, BaseActionSource source) {
        try {
            return BigMEInventories.injectItemsBig(inventory, input, mode, source);
        } catch (UnsupportedBigInventoryException ignored) {
            return input;
        }
    }

    private IAEStack apeiron$extract(IMEInventoryHandler<IAEStack> inventory, IAEStack request,
        Actionable mode, BaseActionSource source) {
        try {
            return BigMEInventories.extractItemsBig(inventory, request, mode, source);
        } catch (UnsupportedBigInventoryException ignored) {
            return null;
        }
    }

    @Override
    public IAEStack injectItemsBig(IAEStack input, Actionable mode, BaseActionSource source) {
        if (input == null || input.getStackSize() == 0) return null;
        if (input.getStackSize() < 0) throw new IllegalArgumentException("negative injection count");
        NetworkInventoryHandler self = apeiron$self();
        if (diveList(self, mode)) return input;

        try {
            if (testPermission(source, SecurityPermissions.INJECT)) return input;

            List<IMEInventoryHandler<IAEStack>> inventories = apeiron$handlers();
            int size = inventories.size();
            int i = 0;
            boolean stickyInventoryFound = false;

            for (; i < size && input != null; i++) {
                IMEInventoryHandler<IAEStack> inventory = inventories.get(i);
                if (!inventory.getSticky() && !inventory.isAutoCraftingInventory()) break;
                if (inventory.canAccept(input) && apeiron$hasItem(inventory, input, source)) {
                    input = apeiron$inject(inventory, input, mode, source);
                    if (!stickyInventoryFound && inventory.getSticky()) stickyInventoryFound = true;
                }
            }

            if (stickyInventoryFound || input == null || i >= size) return input;

            IMEInventoryHandler<IAEStack> inventory = inventories.get(i);
            int lastPriority = inventory.getPriority();
            outer: while (true) {
                Map<Integer, BigInteger> simulatedPass1Inserted = new HashMap<>();
                int passTwoIndex = -1;

                while (true) {
                    boolean canAcceptInput = true;
                    boolean validForPass1 = inventory.validForPass(1);
                    if (validForPass1 && (canAcceptInput = inventory.canAccept(input))
                        && apeiron$hasItem(inventory, input, source)) {
                        BigInteger before = BigAEStackValues.get(input);
                        input = apeiron$inject(inventory, input, mode, source);
                        if (input == null) break outer;

                        if (mode == Actionable.SIMULATE && inventory.validForPass(2)) {
                            BigInteger accepted = before.subtract(BigAEStackValues.get(input));
                            if (accepted.signum() > 0) {
                                simulatedPass1Inserted.merge(i, accepted, BigInteger::add);
                            }
                        }
                    }

                    if (canAcceptInput && passTwoIndex == -1 && inventory.validForPass(2)) {
                        passTwoIndex = i;
                        if (!validForPass1) break;
                    }

                    i++;
                    if (i >= size) {
                        if (passTwoIndex == -1) break outer;
                        break;
                    }

                    inventory = inventories.get(i);
                    int priority = inventory.getPriority();
                    boolean prioritySwitch = lastPriority != priority;
                    lastPriority = priority;
                    if (prioritySwitch) break;
                }

                if (passTwoIndex != -1) {
                    i = passTwoIndex;
                    inventory = inventories.get(i);
                    lastPriority = inventory.getPriority();
                    while (true) {
                        if (inventory.canAccept(input) && !inventory.isPrioritized(input)) {
                            if (mode == Actionable.SIMULATE) {
                                BigInteger pass1Inserted = simulatedPass1Inserted.getOrDefault(i, BigInteger.ZERO);
                                if (pass1Inserted.signum() > 0) {
                                    IAEStack pass2Input = BigAEStackValues.copyWithSize(
                                        input,
                                        BigAEStackValues.get(input)
                                            .add(pass1Inserted));
                                    IAEStack pass2Leftover = apeiron$inject(inventory, pass2Input, mode, source);
                                    if (pass2Leftover == null) {
                                        input = null;
                                        break outer;
                                    }
                                    if (BigAEStackValues.get(pass2Leftover)
                                        .compareTo(BigAEStackValues.get(input)) < 0) {
                                        input = BigAEStackValues
                                            .copyWithSize(input, BigAEStackValues.get(pass2Leftover));
                                    }
                                } else {
                                    input = apeiron$inject(inventory, input, mode, source);
                                }
                            } else {
                                input = apeiron$inject(inventory, input, mode, source);
                            }
                            if (input == null) break outer;
                        }

                        i++;
                        if (i >= size) break outer;
                        inventory = inventories.get(i);
                        int priority = inventory.getPriority();
                        boolean prioritySwitch = lastPriority != priority;
                        lastPriority = priority;
                        if (prioritySwitch) break;
                    }
                }
            }
            return input;
        } finally {
            surface(self, mode);
        }
    }

    @Override
    public IAEStack extractItemsBig(IAEStack request, Actionable mode, BaseActionSource source) {
        if (request == null || request.getStackSize() == 0) return null;
        if (request.getStackSize() < 0) throw new IllegalArgumentException("negative extraction count");
        NetworkInventoryHandler self = apeiron$self();
        if (diveList(self, mode)) return null;

        try {
            if (testPermission(source, SecurityPermissions.EXTRACT)) return null;
            BigInteger requested = BigAEStackValues.get(request);
            IAEStack output = BigAEStackValues.copyWithSize(request, BigInteger.ZERO);
            List<IMEInventoryHandler<IAEStack>> inventories = apeiron$handlers();

            for (int i = inventories.size() - 1; i >= 0 && BigAEStackValues.get(output)
                .compareTo(requested) < 0; i--) {
                IAEStack remainingRequest = BigAEStackValues
                    .copyWithSize(request, requested.subtract(BigAEStackValues.get(output)));
                IAEStack extracted = apeiron$extract(inventories.get(i), remainingRequest, mode, source);
                if (extracted == null) continue;
                BigInteger amount = BigAEStackValues.get(extracted);
                if (amount.signum() <= 0) continue;
                if (amount.compareTo(requested.subtract(BigAEStackValues.get(output))) > 0) {
                    throw new IllegalStateException("inventory extracted more than requested");
                }
                BigAEStackValues.addStorage(output, extracted);
            }

            return BigAEStackValues.get(output)
                .signum() == 0 ? null : output;
        } finally {
            surface(self, mode);
        }
    }

    @Override
    public IAEItemStack injectItemsBig(IAEItemStack input, Actionable mode, BaseActionSource source) {
        return (IAEItemStack) injectItemsBig((IAEStack) input, mode, source);
    }

    @Override
    public IAEItemStack extractItemsBig(IAEItemStack request, Actionable mode, BaseActionSource source) {
        return (IAEItemStack) extractItemsBig((IAEStack) request, mode, source);
    }

    @Inject(method = "getAvailableItem", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyAvailable(IAEStack<?> request, int iteration, CallbackInfoReturnable<IAEStack<?>> cir) {
        IAEStack exact = BigMEInventories.getAvailableItemBig(apeiron$self(), request, iteration);
        if (BigAEStackValues.isBig(exact)) cir.setReturnValue(exact);
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyInject(IAEStack<?> input, Actionable mode, BaseActionSource source,
        CallbackInfoReturnable<IAEStack<?>> cir) {
        if (BigAEStackValues.isBig(input)) {
            cir.setReturnValue(injectItemsBig((IAEStack) input, mode, source));
        }
    }

    @Inject(method = "extractItems", at = @At("HEAD"), cancellable = true)
    private void apeiron$legacyExtract(IAEStack<?> request, Actionable mode, BaseActionSource source,
        CallbackInfoReturnable<IAEStack<?>> cir) {
        if (BigAEStackValues.isBig(request)) {
            cir.setReturnValue(extractItemsBig((IAEStack) request, mode, source));
        }
    }
}
