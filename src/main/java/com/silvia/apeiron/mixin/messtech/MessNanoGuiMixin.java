package com.silvia.apeiron.mixin.messtech;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.silvia.apeiron.common.integration.messtech.MessNanoController;
import com.silvia.apeiron.common.integration.messtech.MessTechAccess;
import com.silvia.apeiron.common.machine.parallel.WirelessRecipeState;
import com.silvia.apeiron.math.BigNumberFormatter;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Read-only thread displays use exact strings; native progress and board tank widgets remain native. */
@Pseudo
@Mixin(targets = "com.MessTech.common.gui.MTNanoScaleFoundryGui", remap = false)
public abstract class MessNanoGuiMixin {

    @Unique
    private final Map<Integer, StringSyncValue> apeiron$parallels = new HashMap<>();
    @Unique
    private final Map<Integer, StringSyncValue> apeiron$power = new HashMap<>();

    @Unique
    private MTEMultiBlockBase apeiron$machine() {
        return (MTEMultiBlockBase) MessTechAccess.get(this, "multiblock");
    }

    @Inject(method = "registerSyncValues", at = @At("RETURN"), require = 1)
    private void apeiron$sync(PanelSyncManager sync, CallbackInfo ci) {
        int count = ((Number) MessTechAccess.call(apeiron$machine(), "getMaxThreadCount")).intValue();
        for (int i = 0; i < count; i++) {
            final int index = i;
            StringSyncValue parallel = new StringSyncValue(() -> {
                Object thread = MessTechAccess.call(apeiron$machine(), "getThreadByIndex", index);
                WirelessRecipeState state = ((MessNanoController) apeiron$machine()).getMessNanoTasks()
                    .threadState(thread);
                return state == null
                    ? String.valueOf(MessTechAccess.call(apeiron$machine(), "getThreadParallel", index))
                    : BigNumberFormatter.formatCompact(state.getParallelsBig());
            });
            StringSyncValue power = new StringSyncValue(() -> {
                Object thread = MessTechAccess.call(apeiron$machine(), "getThreadByIndex", index);
                WirelessRecipeState state = ((MessNanoController) apeiron$machine()).getMessNanoTasks()
                    .threadState(thread);
                return state == null ? String.valueOf(MessTechAccess.call(apeiron$machine(), "getThreadEUt", index))
                    : BigNumberFormatter.formatCompact(
                        state.remainingEnergy()
                            .signum() == 0 ? java.math.BigInteger.ZERO : state.nextDebit(10000));
            });
            sync.syncValue("apeiron_threadParallel" + i, parallel);
            sync.syncValue("apeiron_threadEUt" + i, power);
            apeiron$parallels.put(i, parallel);
            apeiron$power.put(i, power);
        }
    }

    @Inject(method = "createThreadStatusBlock", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$row(int index, StringSyncValue name, BooleanSyncValue active, IntSyncValue progress,
        IntSyncValue maximum, IntSyncValue nativeParallel, LongSyncValue nativePower, StringSyncValue output,
        CallbackInfoReturnable<Flow> cir) {
        StringSyncValue parallel = apeiron$parallels.get(index - 1), power = apeiron$power.get(index - 1);
        cir.setReturnValue(
            Flow.column()
                .coverChildren()
                .child(new TextWidget<>(IKey.dynamic(() -> {
                    String localized = StatCollector.translateToLocal(
                        "machine.nanoscale.thread." + name.getStringValue()
                            .toLowerCase(Locale.ROOT));
                    if (!active.getBoolValue()) return EnumChatFormatting.GRAY + "#"
                        + index
                        + " "
                        + localized
                        + ": "
                        + StatCollector.translateToLocal("GT5U.waila.machine.idle");
                    return EnumChatFormatting.AQUA + "#"
                        + index
                        + " "
                        + localized
                        + EnumChatFormatting.GRAY
                        + " | "
                        + EnumChatFormatting.WHITE
                        + parallel.getStringValue()
                        + StatCollector.translateToLocal("machine.nanoscale.unit.parallel")
                        + " "
                        + EnumChatFormatting.RED
                        + power.getStringValue()
                        + StatCollector.translateToLocal("machine.nanoscale.unit.eut");
                })).height(10)
                    .scale(0.7f))
                .child(new TextWidget<>(IKey.dynamic(() -> {
                    double current = progress.getIntValue() / 20.0, max = maximum.getIntValue() / 20.0;
                    return StatCollector.translateToLocalFormatted(
                        "GT5U.gui.text.progress_recipe",
                        String.format("%.2f", current),
                        String.format("%.2f", max),
                        String.format("%.0f", max > 0 ? current / max * 100 : 0));
                })).height(10)
                    .scale(0.7f)
                    .setEnabledIf(w -> active.getBoolValue()))
                .child(
                    new TextWidget<>(IKey.dynamic(output::getStringValue)).height(10)
                        .scale(0.7f)
                        .setEnabledIf(
                            w -> active.getBoolValue() && !output.getStringValue()
                                .isEmpty())));
    }
}
