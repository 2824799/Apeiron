package com.silvia.apeiron.api.machine.parallel;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** A space-elevator module borrows its parent controller's energy source. */
public interface BigSpaceElevatorModule {

    MTEMultiBlockBase getEnergyParent();

    ParallelLimit getModuleParallelLimitBig();
}
