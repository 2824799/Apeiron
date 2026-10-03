package com.silvia.apeiron.mixin.ae.crafting.core;

import java.math.BigInteger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silvia.apeiron.ae.crafting.core.BigCraftingConfirmation;
import com.silvia.apeiron.ae.crafting.core.BigCraftingCpuStatus;
import com.silvia.apeiron.ae.crafting.core.BigCraftingJob;
import com.silvia.apeiron.ae.crafting.core.BigMECraftingInventory;
import com.silvia.apeiron.ae.crafting.core.CraftingTreeSource;
import com.silvia.apeiron.ae.crafting.core.UnlimitedCraftingCPU;
import com.silvia.apeiron.ae.stack.BigAERequestableStack;
import com.silvia.apeiron.ae.stack.BigAEStack;
import com.silvia.apeiron.ae.stack.BigAEStackValues;
import com.silvia.apeiron.math.CraftingPlanAmounts;

import appeng.api.config.Actionable;
import appeng.api.config.CraftingAllow;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.implementations.CraftingCPUStatus;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftingTreeData;
import appeng.crafting.MECraftingInventory;

/** Keeps the crafting confirmation plan exact while it is converted into update packets. */
@Mixin(value = ContainerCraftConfirm.class, remap = false)
public abstract class ContainerCraftConfirmMixin extends Container implements BigCraftingConfirmation {

    @Shadow
    protected ICraftingJob result;

    @Shadow
    public abstract long getUsedBytes();

    @Shadow
    public abstract boolean cpuCraftingSameItem(CraftingCPUStatus status);

    @Unique
    @GuiSync(30002)
    public String apeiron$usedBytesExact = "";

    @Unique
    private BigInteger apeiron$simulatedExtracted = BigInteger.ZERO;

    @Inject(
        method = { "detectAndSendChanges", "func_75142_b" },
        at = @At(
            value = "INVOKE",
            target = "Lappeng/container/implementations/ContainerCraftConfirm;setJob(Ljava/util/concurrent/Future;)V"),
        require = 1)
    private void apeiron$sendFastTree(CallbackInfo ci) {
        if (!(result instanceof CraftingTreeSource)) return;
        try {
            final java.util.List<PacketCraftingTreeData> chunks = PacketCraftingTreeData
                .createChunks(((CraftingTreeSource) result).getJobTree());
            for (Object crafter : crafters) {
                if (crafter instanceof EntityPlayerMP) {
                    for (PacketCraftingTreeData chunk : chunks) {
                        NetworkHandler.instance.sendTo(chunk, (EntityPlayerMP) crafter);
                    }
                }
            }
        } catch (RuntimeException error) {
            AELog.warn(error, "Could not send the fast crafting plan tree");
        }
    }

    @Override
    public BigInteger getUsedBytesBig() {
        return apeiron$usedBytesExact.isEmpty() ? BigInteger.valueOf(getUsedBytes())
            : new BigInteger(apeiron$usedBytesExact);
    }

    @Redirect(
        method = { "detectAndSendChanges", "func_75142_b" },
        at = @At(value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingJob;getByteTotal()J"),
        require = 1)
    private long apeiron$captureBytes(ICraftingJob job) {
        final BigInteger bytes = job instanceof BigCraftingJob ? ((BigCraftingJob) job).getByteTotalBig()
            : BigInteger.valueOf(job.getByteTotal());
        apeiron$usedBytesExact = bytes.toString();
        return BigAEStackValues.saturatedLong(bytes);
    }

    @Redirect(
        method = { "detectAndSendChanges", "func_75142_b" },
        at = @At(
            value = "INVOKE",
            target = "Lappeng/crafting/MECraftingInventory;extractItems(Lappeng/api/storage/data/IAEStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/BaseActionSource;)Lappeng/api/storage/data/IAEStack;",
            ordinal = 1),
        require = 1)
    private IAEStack<?> apeiron$wholeAvailableAmount(MECraftingInventory inventory, IAEStack<?> request,
        Actionable mode, BaseActionSource source) {
        return ((BigMECraftingInventory) inventory).getStoredStackBig(request);
    }

    @Redirect(
        method = { "detectAndSendChanges", "func_75142_b" },
        at = @At(
            value = "INVOKE",
            target = "Lappeng/crafting/MECraftingInventory;extractItems(Lappeng/api/storage/data/IAEStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/BaseActionSource;)Lappeng/api/storage/data/IAEStack;",
            ordinal = 0),
        require = 1)
    private IAEStack<?> apeiron$simulateExactMissing(MECraftingInventory inventory, IAEStack<?> request,
        Actionable mode, BaseActionSource source) {
        final IAEStack<?> extracted = inventory.extractItems((IAEStack) request, mode, source);
        apeiron$simulatedExtracted = BigAEStackValues.get(extracted);
        return extracted;
    }

    @Redirect(
        method = { "detectAndSendChanges", "func_75142_b" },
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;setStackSize(J)Lappeng/api/storage/data/IAEStack;",
            ordinal = 3),
        require = 1)
    private IAEStack<?> apeiron$exactMissingRemainder(IAEStack<?> missing, long legacy) {
        final BigInteger remainder = BigAEStackValues.get(missing)
            .subtract(apeiron$simulatedExtracted)
            .max(BigInteger.ZERO);
        apeiron$simulatedExtracted = BigInteger.ZERO;
        return BigAEStackValues.set(missing, remainder);
    }

    @Redirect(
        method = { "detectAndSendChanges", "func_75142_b" },
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;setUsedPercent(F)Lappeng/api/storage/data/IAEStack;"),
        require = 1)
    private IAEStack<?> apeiron$exactUsedPercent(IAEStack<?> used, float legacyPercent) {
        final IAEStack<?> available = ((BigMECraftingInventory) result.getStorageAtBeginning()).getStoredStackBig(used);
        return used.setUsedPercent(
            CraftingPlanAmounts.usedPercent(
                BigAEStackValues.get(used),
                BigAEStackValues.get(available),
                BigAEStackValues.isInfinite(available)));
    }

    @Inject(method = "cpuMatches", at = @At("HEAD"), cancellable = true, require = 1)
    private void apeiron$matchExactCapacity(CraftingCPUStatus cpu, CallbackInfoReturnable<Boolean> cir) {
        final BigInteger required = getUsedBytesBig();
        if (cpu.allowMode() == CraftingAllow.ONLY_NONPLAYER || required.signum() <= 0) {
            cir.setReturnValue(false);
            return;
        }
        final boolean unlimited = cpu instanceof UnlimitedCraftingCPU
            && ((UnlimitedCraftingCPU) cpu).isCraftingStorageUnlimited();
        if (cpu.isBusy()) {
            cir.setReturnValue(
                cpuCraftingSameItem(cpu) && cpu.isCraftingLinkStandalone()
                    && (unlimited || BigCraftingCpuStatus.storage(cpu)
                        .compareTo(required.add(BigCraftingCpuStatus.usedStorage(cpu))) >= 0));
        } else {
            cir.setReturnValue(
                unlimited || BigCraftingCpuStatus.storage(cpu)
                    .compareTo(required) >= 0);
        }
    }

    private static final ThreadLocal<BigInteger> APEIRON_NEXT_STACK_SIZE = new ThreadLocal<>();
    private static final ThreadLocal<BigInteger> APEIRON_NEXT_REQUESTABLE = new ThreadLocal<>();
    private static final ThreadLocal<BigInteger> APEIRON_NEXT_REQUESTABLE_CRAFTS = new ThreadLocal<>();

    @Redirect(
        method = { "detectAndSendChanges", "func_75142_b" },
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getStackSize()J", ordinal = 0))
    private long apeiron$capturePlannedStackSize(final IAEStack<?> stack) {
        APEIRON_NEXT_STACK_SIZE.remove();
        if (stack instanceof BigAEStack && ((BigAEStack) stack).isStackSizeBig()) {
            APEIRON_NEXT_STACK_SIZE.set(((BigAEStack) stack).getStackSizeBig());
        }
        return stack.getStackSize();
    }

    @Redirect(
        method = { "detectAndSendChanges", "func_75142_b" },
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;setStackSize(J)Lappeng/api/storage/data/IAEStack;",
            ordinal = 0))
    private IAEStack<?> apeiron$restoreExtractStackSize(final IAEStack<?> stack, final long value) {
        final BigInteger exact = APEIRON_NEXT_STACK_SIZE.get();
        APEIRON_NEXT_STACK_SIZE.remove();
        return exact == null ? stack.setStackSize(value) : BigAEStackValues.set(stack, exact);
    }

    @Redirect(
        method = { "detectAndSendChanges", "func_75142_b" },
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getCountRequestable()J", ordinal = 0))
    private long apeiron$captureRequestable(final IAEStack<?> stack) {
        APEIRON_NEXT_REQUESTABLE.remove();
        if (stack instanceof BigAERequestableStack && ((BigAERequestableStack) stack).isCountRequestableBig()) {
            APEIRON_NEXT_REQUESTABLE.set(((BigAERequestableStack) stack).getCountRequestableBig());
        }
        return stack.getCountRequestable();
    }

    @Redirect(
        method = { "detectAndSendChanges", "func_75142_b" },
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;setStackSize(J)Lappeng/api/storage/data/IAEStack;",
            ordinal = 1))
    private IAEStack<?> apeiron$restoreCraftRequestSize(final IAEStack<?> stack, final long value) {
        final BigInteger exact = APEIRON_NEXT_REQUESTABLE.get();
        APEIRON_NEXT_REQUESTABLE.remove();
        return exact == null ? stack.setStackSize(value) : BigAEStackValues.set(stack, exact);
    }

    @Redirect(
        method = { "detectAndSendChanges", "func_75142_b" },
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;getCountRequestableCrafts()J",
            ordinal = 0))
    private long apeiron$captureRequestableCrafts(final IAEStack<?> stack) {
        APEIRON_NEXT_REQUESTABLE_CRAFTS.remove();
        if (stack instanceof BigAERequestableStack && ((BigAERequestableStack) stack).isCountRequestableCraftsBig()) {
            APEIRON_NEXT_REQUESTABLE_CRAFTS.set(((BigAERequestableStack) stack).getCountRequestableCraftsBig());
        }
        return stack.getCountRequestableCrafts();
    }

    @Redirect(
        method = { "detectAndSendChanges", "func_75142_b" },
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IAEStack;setCountRequestableCrafts(J)Lappeng/api/storage/data/IAEStack;"))
    private IAEStack<?> apeiron$restoreRequestableCrafts(final IAEStack<?> stack, final long value) {
        final BigInteger exact = APEIRON_NEXT_REQUESTABLE_CRAFTS.get();
        APEIRON_NEXT_REQUESTABLE_CRAFTS.remove();
        if (exact != null && stack instanceof BigAERequestableStack) {
            return ((BigAERequestableStack) stack).setCountRequestableCraftsBig(exact);
        }
        return stack.setCountRequestableCrafts(value);
    }
}
