package com.silvia.apeiron.common.integration.messtech;

import gregtech.api.util.GTRecipe;

public interface MessNaniteController {

    boolean canAdvanceNanites();

    int naniteDuration(GTRecipe recipe, int duration);
}
