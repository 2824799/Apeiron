package com.silvia.apeiron.compat;

/** Optional adapters require the native lifecycle they wrap; older layouts retain their native path. */
public final class MessTechContracts {

    private static final String NANO = "com.MessTech.common.machine.MTNanoScaleFoundry";
    private static final String THREAD = "com.MessTech.common.machine.Base.TickableParallelismAcrossMultiMachineBase";

    private MessTechContracts() {}

    public static boolean supports(String mixin, String target) {
        switch (mixin) {
            case "MessNanoMixin":
            case "MessThreadMixin":
            case "MessNanoGuiMixin":
                return methods(
                    NANO,
                    "checkAndStartThread",
                    "calculateOverclockedTask",
                    "getThreadOutputText",
                    "getWailaNBTData",
                    "getWailaBody",
                    "saveNBTData",
                    "loadNBTData")
                    && methods(THREAD, "tickThread", "clearAllTasks")
                    && DependencyCapabilities
                        .invokes(NANO, "checkAndStartThread", NANO.replace('.', '/'), "calculateOverclockedTask")
                    && (!mixin.equals("MessNanoGuiMixin")
                        || methods(target, "registerSyncValues", "createThreadStatusBlock"));
            case "MessBosesMixin":
                return methods(
                    target,
                    "incrementProgressTime",
                    "getHighestNaniteTier",
                    "getAvailableNanites",
                    "setNaniteStalled") && fields(target, "requiredNanites");
            case "MessWirelessMixin":
                return methods(target, "checkProcessingWirelessLoop", "wirelessModeProcessOnce", "startWirelessRecipe")
                    && DependencyCapabilities.invokes(
                        target,
                        "checkProcessingWirelessLoop",
                        target.replace('.', '/'),
                        "wirelessModeProcessOnce");
            case "MessModuleMixin":
                return methods(
                    target,
                    "checkProcessingWirelessLoop",
                    "wirelessModeProcessOnce",
                    "onRunningTick",
                    "supportsPowerPanel")
                    && fields(target, "parentElevator", "costingEU", "costingEUText")
                    && DependencyCapabilities.invokes(
                        target,
                        "checkProcessingWirelessLoop",
                        target.replace('.', '/'),
                        "wirelessModeProcessOnce");
            case "MessPumpMixin":
                return fields(target, "planetTypes", "gasTypes", "batchSize") && batchCall(target);
            case "MessMinerMixin":
                return methods(
                    target,
                    "gatherItemInputs",
                    "getBestPlasmaTier",
                    "getEffectiveDistance",
                    "getRecipeTime",
                    "getBonusStackChance",
                    "cycleDistance") && fields(target, "asteroidOutpost", "configuredOres", "isWhitelisted")
                    && batchCall(target);
            case "MessApiaryMixin":
                return methods(target, "runBees", "hasAnyBee", "getMainBatchDuration") && DependencyCapabilities
                    .invokes(target, "checkProcessing_EM", target.replace('.', '/'), "hasAnyBee");
            default:
                return true;
        }
    }

    private static boolean batchCall(String owner) {
        return DependencyCapabilities
            .invokes(owner, "checkProcessing_EM", owner.replace('.', '/'), "startBatchedProcessing");
    }

    private static boolean methods(String owner, String... names) {
        for (String name : names) if (!DependencyCapabilities.hasMethod(owner, name, null)) return false;
        return true;
    }

    private static boolean fields(String owner, String... names) {
        org.spongepowered.asm.lib.tree.ClassNode node = DependencyCapabilities.inspect(owner);
        if (node == null) return false;
        for (String name : names) if (node.fields.stream()
            .noneMatch(field -> field.name.equals(name))) return false;
        return true;
    }
}
