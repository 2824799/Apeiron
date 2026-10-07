package com.silvia.apeiron.ae.smoke;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

import com.silvia.apeiron.Apeiron;
import com.silvia.apeiron.ae.crafting.core.BigCraftingCPUStorage;
import com.silvia.apeiron.ae.crafting.core.BigCraftingJob;
import com.silvia.apeiron.math.AdaptiveInteger;
import com.silvia.apeiron.math.BigValueCodec;

import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.util.WorldCoord;
import appeng.me.cluster.implementations.CraftingCPUCluster;

/** Constructs the transformed CPU itself and exercises rejected submissions and exact saved storage. */
public final class AECraftingCPUSmoke {

    private static final BigInteger HUGE = BigInteger.TEN.pow(60)
        .add(BigInteger.valueOf(17));

    private AECraftingCPUSmoke() {}

    public static void verify() {
        try {
            verifyCompletion();
            if (com.silvia.apeiron.compat.DependencyCapabilities
                .hasMethod("appeng.me.cluster.implementations.CraftingCPUCluster$TaskProgress", "access$200", null)) {
                CraftingCPUCluster.TaskProgress task = new CraftingCPUCluster.TaskProgress();
                com.silvia.apeiron.ae.crafting.core.BigTaskProgress exact = (com.silvia.apeiron.ae.crafting.core.BigTaskProgress) task;
                exact.setValueBig(HUGE);
                java.lang.reflect.Method decrement = task.getClass()
                    .getDeclaredMethod("access$210", task.getClass());
                decrement.setAccessible(true);
                decrement.invoke(null, task);
                check(
                    exact.getValueBig()
                        .equals(HUGE.subtract(BigInteger.ONE)),
                    "legacy native progress failed exact decrement");
            }
            final CraftingCPUCluster cpu = newCPU();
            final BigCraftingCPUStorage storage = (BigCraftingCPUStorage) (Object) cpu;
            final Field insideSubmit = CraftingCPUCluster.class.getDeclaredField("apeiron$insideSubmitJob");
            insideSubmit.setAccessible(true);
            final BaseActionSource source = new BaseActionSource();
            check(
                cpu.submitJob(null, job(BigInteger.valueOf(7), false), source, null) == null,
                "inactive CPU unexpectedly accepted an ordinary job");
            check(!insideSubmit.getBoolean(cpu), "ordinary rejected submission retained its in-progress flag");
            check(
                cpu.submitJob(null, job(HUGE, true), source, null) == null,
                "CPU without storage accepted an oversized job");
            check(!insideSubmit.getBoolean(cpu), "exact rejected submission retained its in-progress flag");
            check(
                storage.getUsedStorageBig()
                    .signum() == 0,
                "rejected submission consumed CPU storage");

            final NBTTagCompound tag = new NBTTagCompound();
            cpu.writeToNBT(tag);
            BigValueCodec.writeNBT(tag, "usedStorage", "ApeironUsedStorage", new AdaptiveInteger(HUGE));
            BigValueCodec.writeNBT(
                tag,
                "availableStorage",
                "ApeironAvailableStorage",
                new AdaptiveInteger(HUGE.multiply(BigInteger.valueOf(2))));
            final CraftingCPUCluster restored = newCPU();
            restored.readFromNBT(tag);
            final BigCraftingCPUStorage restoredStorage = (BigCraftingCPUStorage) (Object) restored;
            check(
                restoredStorage.getUsedStorageBig()
                    .equals(HUGE),
                "CPU reload lost exact used storage");
            check(
                restoredStorage.getAvailableStorageBig()
                    .equals(HUGE.multiply(BigInteger.valueOf(2))),
                "CPU reload lost exact available storage");
            check(
                restored.getUsedStorage() == Long.MAX_VALUE && restored.getAvailableStorage() == Long.MAX_VALUE,
                "CPU legacy byte counters did not saturate");
            check(
                restored.submitJob(null, job(HUGE.add(BigInteger.ONE), true), source, null) == null,
                "CPU accepted a job larger than its exact remaining storage");
            check(!insideSubmit.getBoolean(restored), "rejected restored CPU submission retained its flag");
            check(
                restoredStorage.getUsedStorageBig()
                    .equals(HUGE),
                "rejected restored submission altered used storage");

            final NBTTagCompound written = new NBTTagCompound();
            restored.writeToNBT(written);
            check(
                BigValueCodec.readNBT(written, "usedStorage", "ApeironUsedStorage")
                    .toBigInteger()
                    .equals(HUGE),
                "CPU save lost exact used storage");
            restored.cancel();
            check(
                restoredStorage.getUsedStorageBig()
                    .signum() == 0 && restored.getUsedStorage() == 0,
                "CPU cancellation did not clear both exact and legacy storage");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("AE crafting CPU verification failed", error);
        }
        Apeiron.LOG.info(
            "AE crafting CPU construction, rejected submissions, exact storage persistence and cancellation verification passed");
    }

    private static void verifyCompletion() throws ReflectiveOperationException {
        CraftingCPUCluster cpu = newCPU();
        Field outputs = CraftingCPUCluster.class.getDeclaredField("finalOutput");
        outputs.setAccessible(true);
        Object finalOutput = outputs.get(cpu);
        Field original = finalOutput.getClass()
            .getDeclaredField("originalOutput");
        original.setAccessible(true);
        appeng.api.storage.data.IAEItemStack stack = appeng.util.item.AEItemStack
            .create(new net.minecraft.item.ItemStack(net.minecraft.init.Items.diamond));
        com.silvia.apeiron.ae.stack.BigAEStackValues.set(stack, HUGE);
        original.set(finalOutput, stack);
        Field listeners = CraftingCPUCluster.class.getDeclaredField("craftCompleteListeners");
        listeners.setAccessible(true);
        java.util.List<appeng.api.util.CraftCompleteListener> callbacks = new java.util.ArrayList<>();
        callbacks.add((output, count, ticks) -> {
            CraftingCPUCluster.CraftNotification notification = new CraftingCPUCluster.CraftNotification(
                output,
                count,
                ticks);
            check(
                ((com.silvia.apeiron.ae.crafting.packets.BigCraftNotification) notification).getOutputsCountBig()
                    .equals(HUGE),
                "native completion callback truncated exact count");
        });
        // The last callback intentionally does not consume the sidecar.
        callbacks.add((output, count, ticks) -> {});
        listeners.set(cpu, callbacks);
        java.lang.reflect.Method complete = CraftingCPUCluster.class.getDeclaredMethod("completeJob");
        complete.setAccessible(true);
        complete.invoke(cpu);
        check(
            com.silvia.apeiron.ae.crafting.packets.BigCraftNotificationValues.take(7)
                .equals(BigInteger.valueOf(7)),
            "completion count leaked to an unrelated notification");
    }

    private static CraftingCPUCluster newCPU() throws ReflectiveOperationException {
        final CraftingCPUCluster cpu = new CraftingCPUCluster(new WorldCoord(0, 0, 0), new WorldCoord(0, 0, 0));
        // The native isActive() expects the machine source assigned when a core tile joins the cluster.
        final Field machineSource = CraftingCPUCluster.class.getDeclaredField("machineSrc");
        machineSource.setAccessible(true);
        machineSource.set(cpu, new MachineSource(null));
        return cpu;
    }

    private static ICraftingJob<?> job(final BigInteger bytes, final boolean exact) {
        final Class<?>[] contracts = exact ? new Class<?>[] { ICraftingJob.class, BigCraftingJob.class }
            : new Class<?>[] { ICraftingJob.class };
        return (ICraftingJob<?>) Proxy
            .newProxyInstance(ICraftingJob.class.getClassLoader(), contracts, (proxy, method, arguments) -> {
                if (method.getName()
                    .equals("getByteTotalBig")) return bytes;
                if (method.getName()
                    .equals("getByteTotal"))
                    return bytes.min(BigInteger.valueOf(Long.MAX_VALUE))
                        .longValue();
                throw new UnsupportedOperationException("unexpected rejected CPU job call: " + method.getName());
            });
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
