package com.silvia.apeiron.common.machine.parallel;

import java.lang.reflect.Field;

import gregtech.api.util.ParallelHelper;

/** Only replace an unchanged standard helper; specialized consumer/output callbacks retain native semantics. */
public final class NativeParallelPolicy {

    private static final String TST_HELPER = "com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.processingLogics.GTCM_ParallelHelper";
    private static final ClassValue<Object> DEFAULTS = new ClassValue<Object>() {

        @Override
        protected Object computeValue(Class<?> type) {
            try {
                return type.getDeclaredConstructor()
                    .newInstance();
            } catch (ReflectiveOperationException error) {
                return Boolean.FALSE;
            }
        }
    };

    private NativeParallelPolicy() {}

    public static boolean supports(ParallelHelper helper) {
        return supports(helper, false);
    }

    public static boolean supports(ParallelHelper helper, boolean exactOutputProvider) {
        Class<?> type = helper.getClass();
        if (type != ParallelHelper.class && !type.getName()
            .equals(TST_HELPER)) return false;
        Object defaults = DEFAULTS.get(type);
        if (defaults == Boolean.FALSE) return false;
        try {
            for (Class<?> current = type; current != Object.class; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    switch (field.getName()) {
                        case "customItemOutputCalculation":
                        case "customFluidOutputCalculation":
                            field.setAccessible(true);
                            if (!exactOutputProvider && field.get(helper) != null) return false;
                            break;
                        case "chanceMultiplier":
                            field.setAccessible(true);
                            if (field.getDouble(helper) != 1.0) return false;
                            break;
                        case "isRecipeLocked":
                            field.setAccessible(true);
                            if (field.getBoolean(helper)) return false;
                            break;
                        case "maxParallelCalculator":
                        case "inputConsumer":
                            field.setAccessible(true);
                            if (field.get(helper) != field.get(defaults)) return false;
                            break;
                        default:
                            break;
                    }
                }
            }
            return true;
        } catch (IllegalAccessException error) {
            return false;
        }
    }
}
