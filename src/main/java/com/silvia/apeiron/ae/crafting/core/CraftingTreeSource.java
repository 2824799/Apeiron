package com.silvia.apeiron.ae.crafting.core;

import appeng.crafting.v2.CraftingJobV2;

/** A completed planner that can provide a native AE2 display tree without recalculating the job. */
public interface CraftingTreeSource {

    CraftingJobV2<?> getJobTree();
}
