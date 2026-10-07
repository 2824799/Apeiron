package com.silvia.apeiron.ae.smoke;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigInteger;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.stack.BigAEItemStacks;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.ae.storage.BigIMEInventory;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.storage.SecurityInventory;
import appeng.tile.misc.TileSecurity;
import appeng.util.IterationCounter;
import appeng.util.item.AEItemStack;

/** Regression check for the real transformed security terminal, including javac's generic bridge methods. */
public final class AESecurityInventorySmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(60)
        .add(BigInteger.valueOf(17));

    private AESecurityInventorySmoke() {}

    public static void verify() {
        try {
            final Field inventoryField = TileSecurity.class.getDeclaredField("inventory");
            inventoryField.setAccessible(true);
            final TileSecurity nativeTile = new TileSecurity();
            check(
                inventoryField.get(nativeTile) instanceof BigIMEInventory,
                "security terminal failed to construct with the exact inventory adapter");

            final VerificationTile tile = new VerificationTile();
            final SecurityInventory inventory = (SecurityInventory) inventoryField.get(tile);
            final IMEInventory<IAEItemStack> bridge = inventory;
            final BigIMEInventory exact = (BigIMEInventory) (Object) inventory;
            final PlayerSource player = new PlayerSource(null, tile);
            final BaseActionSource machine = new BaseActionSource();
            final ItemStack physicalCard = AEApi.instance()
                .definitions()
                .items()
                .biometricCard()
                .maybeStack(1)
                .get();
            final IAEItemStack card = BigAEItemStacks.copyWithSize(AEItemStack.create(physicalCard), HUGE);
            final IAEItemStack small = BigAEItemStacks.copyWithSize(card, BigInteger.ONE);

            tile.allowed = false;
            check(
                BigAEStackValues.get(inventory.injectItems(card, Actionable.MODULATE, player))
                    .equals(HUGE),
                "typed security insertion bypassed permissions");
            check(
                BigAEStackValues.get(bridge.injectItems(card, Actionable.MODULATE, player))
                    .equals(HUGE),
                "generic security insertion bypassed permissions");
            check(
                BigAEStackValues.get(exact.injectItemsBig(card, Actionable.MODULATE, player))
                    .equals(HUGE),
                "exact security insertion bypassed permissions");
            check(
                BigAEStackValues.get(bridge.injectItems(card, Actionable.MODULATE, machine))
                    .equals(HUGE),
                "machine source bypassed security permissions");
            check(
                stored(inventory, small).signum() == 0 && tile.changes == 0,
                "rejected insertion modified security inventory");

            tile.allowed = true;
            final IAEItemStack invalid = BigAEItemStacks
                .copyWithSize(AEItemStack.create(new ItemStack(Items.diamond)), HUGE);
            check(
                BigAEStackValues.get(bridge.injectItems(invalid, Actionable.MODULATE, player))
                    .equals(HUGE),
                "security inventory accepted a non-biometric item");
            check(
                inventory.injectItems(card, Actionable.SIMULATE, player) == null,
                "typed security insertion simulation rejected a valid card");
            check(
                bridge.injectItems(card, Actionable.SIMULATE, player) == null,
                "generic security insertion simulation rejected a valid card");
            check(stored(inventory, small).signum() == 0 && tile.changes == 0, "security simulation changed inventory");
            check(
                bridge.injectItems(card, Actionable.MODULATE, player) == null,
                "generic security insertion lost the valid card");
            check(
                stored(inventory, small).equals(HUGE) && tile.changes == 1,
                "security insertion truncated stored quantity or skipped notification");
            check(
                BigAEStackValues.get(card)
                    .equals(HUGE),
                "security insertion mutated caller quantity");
            check(
                BigAEStackValues.get(inventory.getAvailableItem(small, IterationCounter.fetchNewId()))
                    .equals(HUGE),
                "typed security availability lost the full quantity");
            check(
                BigAEStackValues.get(bridge.getAvailableItem(small, IterationCounter.fetchNewId()))
                    .equals(HUGE),
                "generic security availability lost the full quantity");
            check(
                BigAEStackValues.get(bridge.injectItems(card, Actionable.MODULATE, player))
                    .equals(HUGE),
                "duplicate security card was accepted");
            check(
                stored(inventory, small).equals(HUGE) && tile.changes == 1,
                "duplicate rejection changed security inventory");

            tile.allowed = false;
            check(
                inventory.extractItems(card, Actionable.MODULATE, player) == null,
                "typed security extraction bypassed permissions");
            check(
                bridge.extractItems(card, Actionable.MODULATE, player) == null,
                "generic security extraction bypassed permissions");
            check(
                exact.extractItemsBig(card, Actionable.MODULATE, player) == null,
                "exact security extraction bypassed permissions");
            check(
                stored(inventory, small).equals(HUGE) && tile.changes == 1,
                "denied extraction changed security inventory");

            tile.allowed = true;
            final IAEItemStack partial = BigAEItemStacks.copyWithSize(card, HUGE.subtract(BigInteger.valueOf(23)));
            check(
                BigAEStackValues.get(inventory.extractItems(partial, Actionable.SIMULATE, player))
                    .equals(BigAEStackValues.get(partial)),
                "typed security extraction simulation lost exact quantity");
            check(
                BigAEStackValues.get(bridge.extractItems(partial, Actionable.SIMULATE, player))
                    .equals(BigAEStackValues.get(partial)),
                "generic security extraction simulation lost exact quantity");
            check(
                stored(inventory, small).equals(HUGE) && tile.changes == 1,
                "security extraction simulation changed inventory");
            check(
                BigAEStackValues.get(bridge.extractItems(partial, Actionable.MODULATE, player))
                    .equals(BigAEStackValues.get(partial)),
                "generic security extraction lost exact quantity");
            check(
                stored(inventory, small).equals(BigInteger.valueOf(23)) && tile.changes == 2,
                "security extraction lost the remainder or skipped notification");

            check(
                BigAEStackValues.get(bridge.extractItems(small, Actionable.SIMULATE, player))
                    .equals(BigInteger.valueOf(23)),
                "native security-card extraction semantics changed");
            check(
                stored(inventory, small).equals(BigInteger.valueOf(23)) && tile.changes == 2,
                "native security simulation changed inventory");
            check(
                BigAEStackValues.get(bridge.extractItems(small, Actionable.MODULATE, player))
                    .equals(BigInteger.valueOf(23)),
                "native security-card extraction failed");
            check(
                stored(inventory, small).signum() == 0 && tile.changes == 3,
                "native security extraction did not empty the inventory");
            check(
                inventory.injectItems(small, Actionable.MODULATE, player) == null,
                "ordinary security card insertion failed");
            check(
                BigAEStackValues.get(inventory.extractItems(small, Actionable.MODULATE, player))
                    .equals(BigInteger.ONE),
                "ordinary security card extraction failed");
            check(
                stored(inventory, small).signum() == 0 && tile.changes == 5,
                "ordinary security transfers changed notification semantics");
            check(tile.permissionChecks > 0, "security transfers never checked native security permission");
            verifyBridgeMethods();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("AE security inventory verification failed", error);
        }
        Apeiron.LOG.info(
            "AE security terminal construction, typed and generic transfers, permissions and exact counts verification passed");
    }

    private static BigInteger stored(final SecurityInventory inventory, final IAEItemStack type) {
        return BigAEStackValues.get(
            inventory.getStoredItems()
                .findPrecise(type));
    }

    private static void verifyBridgeMethods() throws NoSuchMethodException {
        for (final String name : new String[] { "injectItems", "extractItems" }) {
            check(
                SecurityInventory.class
                    .getDeclaredMethod(name, IAEStack.class, Actionable.class, BaseActionSource.class)
                    .isBridge(),
                "security transfer generic bridge is missing: " + name);
        }
        check(
            SecurityInventory.class.getDeclaredMethod("getAvailableItem", IAEStack.class, int.class)
                .isBridge(),
            "security availability generic bridge is missing");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    /** Keeps AE's permission checks and card validation; substitutes only world/network access for this test. */
    private static final class VerificationTile extends TileSecurity {

        private AENetworkProxy verificationProxy;
        private boolean allowed;
        private int changes;
        private int permissionChecks;

        @Override
        public AENetworkProxy getProxy() {
            // TileSecurity calls getProxy from its constructor before this subclass's fields are initialized.
            if (verificationProxy == null) {
                final ISecurityGrid permissions = (ISecurityGrid) Proxy.newProxyInstance(
                    ISecurityGrid.class.getClassLoader(),
                    new Class<?>[] { ISecurityGrid.class },
                    (proxy, method, arguments) -> {
                        if (method.getName()
                            .equals("hasPermission")) {
                            check(arguments[1] == SecurityPermissions.SECURITY, "unexpected security permission");
                            permissionChecks++;
                            return allowed;
                        }
                        throw new UnsupportedOperationException("unexpected security grid call: " + method.getName());
                    });
                verificationProxy = new AENetworkProxy(this, "apeiron-verification", null, false) {

                    @Override
                    public ISecurityGrid getSecurity() {
                        return permissions;
                    }
                };
            }
            return verificationProxy;
        }

        @Override
        public int getOwner() {
            // AE assigns -1 to an unencoded biometric card; this test terminal must have a different owner.
            return 0;
        }

        @Override
        public void inventoryChanged() {
            changes++;
        }
    }
}
