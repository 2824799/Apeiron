package com.silvia.apeiron.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.StringJoiner;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import appeng.api.interfaces.IInterfaceNameProvider;
import gregtech.common.config.Gregtech;
import gregtech.crossmod.ae2.ChatComponentNonConsumedItemsSuffix;

/** AE and GT changed terminal suffixes from strings to chat components between supported releases. */
public final class InterfaceTerminalNames {

    private static final Method PROVIDER_SUFFIX;
    private static final Field LEGACY_MANUAL_FORMAT;

    static {
        try {
            PROVIDER_SUFFIX = IInterfaceNameProvider.class.getMethod("getInterfaceNameSuffix");
            LEGACY_MANUAL_FORMAT = hasComponentSuffix() ? null
                : Gregtech.machines.getClass()
                    .getField(
                        DependencyCapabilities.hasDeclaredField(
                            "gregtech.common.config.Gregtech$Machines",
                            "itemSlotsSuffixFormat",
                            "Ljava/lang/String;") ? "itemSlotsSuffixFormat" : "cibManualSlotsSuffixFormat");
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private InterfaceTerminalNames() {}

    public static boolean hasComponentSuffix() {
        return IChatComponent.class.isAssignableFrom(PROVIDER_SUFFIX.getReturnType());
    }

    public static IChatComponent suffix(IInterfaceNameProvider provider) {
        try {
            Object suffix = PROVIDER_SUFFIX.invoke(provider);
            if (suffix == null) return null;
            if (suffix instanceof IChatComponent component) return component;
            String text = (String) suffix;
            return text.isEmpty() ? null : new ChatComponentText(text);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot read native interface name suffix", error);
        }
    }

    public static IChatComponent sharedItems(List<ItemStack> items) {
        if (hasComponentSuffix()) return new ChatComponentNonConsumedItemsSuffix(items);
        StringJoiner names = new StringJoiner(", ");
        for (ItemStack stack : items) names.add(stack.getDisplayName());
        try {
            return new ChatComponentText(String.format((String) LEGACY_MANUAL_FORMAT.get(Gregtech.machines), names));
        } catch (IllegalFormatException invalidFormat) {
            // Match native GT's handling of an invalid user-supplied suffix format.
            return new ChatComponentText("");
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot read native catalyst suffix format", error);
        }
    }
}
