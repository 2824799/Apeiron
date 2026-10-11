# 注入清单

由 `mixins.apeiron.json` 与源码生成。全部注册项均受功能组配置控制；实际版本的选择结果与方法检查见 `build/reports/mixins/*.tsv`。

| 注入类 | 端 | 注入 / 重定向 / 覆盖 / 访问器数量 |
| --- | --- | --- |
| `gtnl.GtnlProcessingLogicBigMixin` | 公共 | Inject:3, Redirect:2 |
| `gtnl.GtnlWirelessMachineMixin` | 公共 | Inject:3 |
| `gtnl.GtnlWirelessBatchMixin` | 公共 | Inject:2 |
| `gtnl.GtnlWirelessStepMixin` | 公共 | Redirect:3, Inject:1 |
| `ae.terminal.core.PatternBufferQuickMoveMixin` | 公共 | Inject:1 |
| `tst.compat.TstPatternEncodeGuardMixin` | 公共 | Inject:1 |
| `tst.energy.WaterPurifierRecipeAccessor` | 公共 | Accessor:1 |
| `gregtech.lanthanides.TargetChamberInputMixin` | 公共 | Inject:4, Redirect:1 |
| `gregtech.lanthanides.AutoLaserRegistrationMixin` | 公共 | Inject:1 |
| `gregtech.lanthanides.SourceChamberInputMixin` | 公共 | Inject:1, Redirect:2 |
| `gregtech.input.BigDualInputProcessingMixin` | 公共 | Inject:3 |
| `gregtech.input.BigInputHatchElementMixin` | 公共 | Inject:1 |
| `gregtech.input.LegacyBigInputHatchElementMixin` | 公共 | Inject:1 |
| `gregtech.input.BigInputRegistrationMixin` | 公共 | Inject:1 |
| `aeinfinitycell.storage.CellCountBigMixin` | 公共 |  |
| `aeinfinitycell.storage.InfinityCellRecordBigMixin` | 公共 |  |
| `aeinfinitycell.ae.InfinityInventoryBigMixin` | 公共 | Inject:4 |
| `aeinfinitycell.stack.EssentiaStackBigMixin` | 公共 | Inject:5 |
| `aeinfinitycell.stack.EssentiaListBigMixin` | 公共 | Inject:2 |
| `ae.stack.FluidListBigMixin` | 公共 | Inject:2 |
| `aeinfinitycell.stack.EUStackBigMixin` | 公共 | Inject:10 |
| `gregtech.output.MixedOutputRegistrationMixin` | 公共 | Inject:4 |
| `gregtech.output.MixedOutputHatchElementMixin` | 公共 | Inject:1 |
| `gregtech.output.TecTechMixedOutputRegistrationMixin` | 公共 | Inject:3 |
| `gregtech.output.GtppMixedOutputRegistrationMixin` | 公共 | Inject:2 |
| `gregtech.output.PendingBigOutputTickMixin` | 公共 | Inject:1 |
| `gregtech.output.PendingBigOutputDropMixin` | 公共 | Inject:1 |
| `gregtech.output.MultiBlockProcessingAccessor` | 公共 | Accessor:5, Invoker:1 |
| `gregtech.output.NativeOutputBatchMixin` | 公共 | Inject:1 |
| `gregtech.output.ModernNativeFluidOutputBatchMixin` | 公共 | Inject:1 |
| `gregtech.output.LegacyNativeFluidOutputBatchMixin` | 公共 | Inject:1 |
| `tst.output.ItemStackLongBigMixin` | 公共 |  |
| `tst.output.FluidStackLongBigMixin` | 公共 |  |
| `tst.output.ParallelOutputBigMixin` | 公共 | Inject:1 |
| `tst.output.ItemStacksGiverBigMixin` | 公共 | Inject:3 |
| `tst.output.MultiMachineOutputBigMixin` | 公共 | Inject:14 |
| `tst.output.LegacyTstOutputQueueMixin` | 公共 | Inject:1 |
| `tst.output.ModernTstOutputQueueMixin` | 公共 | Inject:3 |
| `tst.output.WirelessOutputBigMixin` | 公共 | Inject:1 |
| `tst.output.StarcoreOutputBigMixin` | 公共 | Inject:1 |
| `tst.output.OreProcessingOutputBigMixin` | 公共 | Redirect:2 |
| `tectech.EyeOfHarmonyBigOutputMixin` | 公共 | Inject:12, Redirect:5 |
| `tectech.EyeOfHarmonyEnhancementMixin` | 公共 | Inject:6, ModifyConstant:3, Redirect:6 |
| `ae.stack.AEBaseContainerBigMixin` | 公共 |  |
| `ae.replenisher.TileSuperMEReplenisherBigMixin` | 公共 | Inject:4 |
| `ae.replenisher.ContainerSuperMEReplenisherBigMixin` | 公共 | Inject:2 |
| `ae.stack.AEItemStackMixin` | 公共 | Inject:5 |
| `ae.stack.AEStackMixin` | 公共 | Inject:13 |
| `ae.stack.AEFluidStackMixin` | 公共 | Inject:5 |
| `ae.stack.AEItemListsMixin` | 公共 | Inject:2 |
| `ae.storage.CellInventoryMixin` | 公共 | Inject:15 |
| `ae.storage.CellInventoryHandlerBigMixin` | 公共 |  |
| `ae.storage.MEPassThroughMixin` | 公共 |  |
| `ae.storage.MEInventoryHandlerMixin` | 公共 |  |
| `ae.storage.NetworkInventoryHandlerMixin` | 公共 | Inject:3 |
| `ae.storage.NetworkMonitorMixin` | 公共 | Inject:2 |
| `ae.storage.GridStorageCacheBigMixin` | 公共 | Inject:1 |
| `ae.storage.CellProviderMountsMixin` | 公共 | Redirect:1 |
| `ae.storage.SecurityInventoryMixin` | 公共 | Inject:3 |
| `ae.storage.CreativeCellInventoryMixin` | 公共 | Inject:3 |
| `ae.stack.AppEngPacketMixin` | 公共 |  |
| `ae.crafting.core.PacketCraftRequestMixin` | 公共 | Inject:1, Redirect:1 |
| `ae.crafting.core.CraftingTaskProgressMixin` | 公共 | Overwrite:7 |
| `ae.crafting.core.CraftingCPUClusterMixin` | 公共 | Redirect:3, Overwrite:9, Inject:9 |
| `ae.crafting.core.CraftingCpuEntryMixin` | 公共 | Inject:3, Overwrite:5 |
| `ae.crafting.core.CraftingCPUStatusMixin` | 公共 | Inject:3, Overwrite:5 |
| `ae.crafting.core.CraftingTimingRecordMixin` | 公共 | Inject:1, Overwrite:4 |
| `ae.crafting.core.CraftNotificationBigMixin` | 公共 | Inject:4, Redirect:1 |
| `ae.crafting.core.PacketCraftingCompleteNotificationBigMixin` | 公共 | Inject:2, Redirect:1 |
| `ae.crafting.core.CompletedDiagnosticRecordMixin` | 公共 | Inject:2 |
| `ae.crafting.core.CraftingCpuDiagnosticsMixin` | 公共 | Overwrite:2 |
| `ae.crafting.core.CraftingNetworkDiagnosticsMixin` | 公共 | Overwrite:9 |
| `ae.crafting.core.DiagnosticRowViewMixin` | 公共 | Inject:1 |
| `ae.crafting.core.PacketCraftingDiagnosticsUpdateBigMixin` | 公共 | Inject:2 |
| `ae.crafting.core.CraftingCPUFinalOutputMixin` | 公共 | Overwrite:3 |
| `ae.crafting.core.MECraftingInventoryMixin` | 公共 | Inject:2 |
| `ae.crafting.core.SccResolverResultMixin` | 公共 |  |
| `ae.crafting.core.CraftingGridCacheMixin` | 公共 |  |
| `ae.crafting.core.CraftingJobFastTreeMixin` | 公共 |  |
| `ae.crafting.core.CraftingTreeSerializerBigMixin` | 公共 | Overwrite:2 |
| `ae.flow.FlowRateMixin` | 公共 |  |
| `ae.flow.ItemFlowGridCacheMixin` | 公共 | Inject:4 |
| `ae.flow.FlowRateFormatterMixin` | 公共 | Inject:1 |
| `ae.flow.PacketFlowRatesMixin` | 公共 | Inject:2 |
| `ae.stack.ItemImmutableListMixin` | 公共 | Overwrite:2 |
| `ae.stack.ItemSortersMixin` | 公共 | Inject:1 |
| `ae.storage.PlatformMixin` | 公共 | Inject:2 |
| `ae.automation.PartSharedItemBusMixin` | 公共 |  |
| `ae.automation.PartBaseExportBusMixin` | 公共 | Inject:1 |
| `ae.automation.PartPatternRepeaterMixin` | 公共 | Inject:1 |
| `ae.automation.PartLevelEmitterMixin` | 公共 | Inject:6 |
| `ae.automation.PartAdvancedLevelEmitterMixin` | 公共 | Inject:6 |
| `ae.automation.ContainerLevelEmitterMixin` | 公共 | Inject:2 |
| `ae.automation.ContainerAdvancedLevelEmitterMixin` | 公共 | Inject:2 |
| `ae.terminal.core.ContainerNetworkStatusBigMixin` | 公共 | Inject:2 |
| `ae.flow.AbstractPartMonitorMixin` | 公共 | Inject:1 |
| `ae.storage.InventoryAdaptorMixin` | 公共 | Inject:2 |
| `ae.storage.MEMonitorIInventoryMixin` | 公共 | Inject:2 |
| `ae.storage.MEIInventoryWrapperMixin` | 公共 | Inject:2 |
| `ae.crafting.core.ContainerCraftConfirmMixin` | 公共 | Inject:2, Redirect:11 |
| `ae.crafting.core.ContainerCraftingCPUBigMixin` | 公共 | Redirect:3 |
| `ae.terminal.core.ContainerMEMonitorableBigMixin` | 公共 | Inject:1 |
| `ae.crafting.core.MultiCraftingTrackerMixin` | 公共 |  |
| `ae.reshuffle.ReshuffleSourceMixin` | 公共 |  |
| `ae.reshuffle.ReshufflePendingMixin` | 公共 |  |
| `ae.reshuffle.ReshuffleTaskMixin` | 公共 | Inject:5 |
| `ae.reshuffle.ReshuffleReportMixin` | 公共 | Inject:3 |
| `ae.reshuffle.ReshuffleItemChangeMixin` | 公共 | Inject:1 |
| `ae.automation.DualityInterfaceMixin` | 公共 | Inject:5, Redirect:4 |
| `ae.storage.AdaptorIInventoryMixin` | 公共 | Accessor:2, Invoker:1 |
| `ae.automation.TileIOPortMixin` | 公共 | Inject:1 |
| `ae.storage.MEMonitorIFluidHandlerMixin` | 公共 | Inject:2 |
| `ae.storage.MEMonitorHandlerMixin` | 公共 | Inject:2 |
| `ae.automation.InterfaceInventoryMixin` | 公共 |  |
| `ae.automation.PartFormationPlaneMixin` | 公共 | Inject:1 |
| `ae.automation.PartImportBusMixin` | 公共 | Inject:1 |
| `ae.crafting.core.CraftingCPUTransferMixin` | 公共 | Overwrite:1, Inject:4 |
| `ae.reshuffle.ScanTaskBigMixin` | 公共 | Overwrite:1, Redirect:4, Inject:3 |
| `ae.reshuffle.ScanRecordBigMixin` | 公共 | Inject:1 |
| `ae.compat.TileCondenserBigMixin` | 公共 | Inject:3 |
| `ae.compat.VoidItemInventoryBigMixin` | 公共 | Inject:1 |
| `ae.compat.VoidFluidInventoryBigMixin` | 公共 | Inject:1 |
| `ae.compat.FactorizationBarrelBigMixin` | 公共 | Inject:2 |
| `ae.compat.JabbaBarrelBigMixin` | 公共 | Inject:2 |
| `ae.compat.MinefactoryReloadedDeepStorageUnitBigMixin` | 公共 | Inject:2 |
| `ae.compat.BSCrateBigMixin` | 公共 | Inject:2 |
| `ae.automation.PartConversionMonitorBigMixin` | 公共 | Inject:2 |
| `ae.terminal.pattern.ContainerPatternTermBigMixin` | 公共 | Overwrite:2 |
| `ae.terminal.pattern.PatternMultiplierHelperBigMixin` | 公共 | Overwrite:3 |
| `ae.terminal.pattern.PatternHelperBigMixin` | 公共 | Inject:1 |
| `gregtech.energy.ProcessingLogicAccessor` | 公共 | Invoker:4 |
| `gregtech.energy.ProcessingLogicBigParallelMixin` | 公共 | Inject:3, Redirect:2 |
| `gregtech.quantum.QuantumEnhancementMixin` | 公共 |  |
| `gregtech.quantum.QuantumProcessingEnhancementMixin` | 公共 | Inject:1 |
| `gregtech.energy.WirelessControllerStateMixin` | 公共 | Inject:7 |
| `gregtech.energy.NativeMachineWailaMixin` | 公共 | Redirect:2, Inject:1 |
| `gregtech.energy.WirelessRunningTickMixin` | 公共 | Inject:3 |
| `gregtech.energy.WirelessParallelGuiMixin` | 公共 | Inject:4 |
| `proghatches.PatternInputOptimizationMixin` | 公共 |  |
| `gregtech.energy.ExtendedWirelessRunningTickMixin` | 公共 | Inject:3 |
| `gregtech.energy.LegacyWirelessParallelGuiMixin` | 公共 | Inject:2 |
| `ae.crafting.core.PatternBatchDispatchMixin` | 公共 | Redirect:2 |
| `ae.terminal.pattern.OptimizerPatternBigMixin` | 公共 |  |
| `ae.terminal.pattern.LegacyOptimizerTaskMixin` | 公共 | Inject:1 |
| `ae.terminal.pattern.ModernOptimizerTaskMixin` | 公共 | Inject:1 |
| `ae.terminal.pattern.ContainerOptimizePatternsBigMixin` | 公共 | ModifyVariable:1, Inject:1, Redirect:2 |
| `ae.crafting.core.UnlimitedCraftingSelectionMixin` | 公共 | Inject:1 |
| `gregtech.energy.WirelessRecipeGuiMixin` | 公共 | Inject:2 |
| `gregtech.energy.LegacyWirelessRecipeGuiMixin` | 公共 | Inject:1 |
| `compat.omniocular.WirelessTooltipMixin` | 公共 | Redirect:1, Inject:1 |
| `compat.waila.WailaPacketBudgetMixin` | 公共 | ModifyVariable:1 |
| `gregtech.energy.GodforgeWirelessEnergyMixin` | 公共 | Inject:2 |
| `gregtech.energy.GodforgeExoticStateMixin` | 公共 | Inject:2 |
| `gregtech.energy.GodforgeExoticRecipeMixin` | 公共 | Redirect:1 |
| `gregtech.energy.GodforgeMilestoneMixin` | 公共 | Redirect:2 |
| `gregtech.energy.GodforgeWirelessGuiMixin` | 公共 | Inject:3 |
| `gregtech.energy.TecTechWirelessRunningMixin` | 公共 | Inject:2 |
| `gregtech.spaceelevator.SpaceElevatorModuleEnergyMixin` | 公共 | Inject:1, Redirect:1 |
| `gregtech.spaceelevator.SpaceElevatorModuleStartupPowerMixin` | 公共 | Inject:2 |
| `gregtech.spaceelevator.SpaceElevatorAssemblerMinerParallelMixin` | 公共 | Inject:1 |
| `gregtech.spaceelevator.SpaceElevatorMinerUltimateMixin` | 公共 | Inject:1 |
| `gregtech.spaceelevator.SpaceElevatorPumpParallelMixin` | 公共 | Inject:1 |
| `gregtech.spaceelevator.SpaceElevatorPumpUltimateMixin` | 公共 | Inject:1 |
| `gregtech.spaceelevator.SpaceElevatorOutputBigMixin` | 公共 | Inject:1 |
| `gregtech.energy.ItemProcessingDispatchMixin` | 公共 | Redirect:1 |
| `gregtech.energy.GodforgeRecipePredictionMixin` | 公共 | Inject:1 |
| `gregtech.input.NativeInputWatcherMixin` | 公共 | Inject:2 |
| `gregtech.output.ModernMEOutputHatchMixin` | 公共 |  |
| `gregtech.output.NativeMEOutputCapacityMixin` | 公共 | Inject:2 |
| `gregtech.output.LegacyNativeMEOutputCapacityMixin` | 公共 | Inject:2 |
| `ae.reshuffle.LegacyReshuffleTaskMixin` | 公共 | Inject:6 |
| `ae.crafting.core.LegacyCraftingEntrypointMixin` | 公共 | Inject:1 |
| `ae.crafting.core.ModernCraftingEntrypointMixin` | 公共 | Inject:1 |
| `ae.crafting.core.LegacyTaskProgressAccessMixin` | 公共 | Inject:4 |
| `ae.crafting.core.ModernTaskProgressAccessMixin` | 公共 | Redirect:2 |
| `gregtech.spaceelevator.LegacyModuleParallelGuiMixin` | 客户端 | Inject:1 |
| `network.CustomPayloadDiagnosticsMixin` | 客户端 | ModifyConstant:1 |
| `aeinfinitycell.stack.EUStackRendererBigMixin` | 客户端 | Redirect:1 |
| `aeinfinitycell.nei.InfinityPreviewCountAccessor` | 客户端 | Accessor:1 |
| `aeinfinitycell.nei.InfinityPreviewBigMixin` | 客户端 | Redirect:2, Inject:1 |
| `ae.stack.AEBaseCellBigMixin` | 客户端 | Redirect:3 |
| `ae.stack.AEBaseCellContentsMixin` | 客户端 | Redirect:2 |
| `ae.replenisher.GuiSuperMEReplenisherBigMixin` | 客户端 | Overwrite:1 |
| `ae.terminal.core.GuiMEMonitorableMixin` | 客户端 | Inject:1 |
| `ae.terminal.core.GuiAmountMixin` | 客户端 | Inject:5 |
| `ae.terminal.core.GuiCraftAmountMixin` | 客户端 | ModifyArg:1 |
| `ae.terminal.level.GuiLevelEmitterBigMixin` | 客户端 | Redirect:1 |
| `ae.terminal.level.GuiAdvancedLevelEmitterBigMixin` | 客户端 | Redirect:1 |
| `ae.crafting.gui.GuiCraftConfirmBigMixin` | 客户端 | Redirect:11, Inject:4 |
| `ae.crafting.gui.GuiCraftingListBigMixin` | 客户端 | Redirect:3 |
| `ae.crafting.gui.GuiCraftingTreeBigMixin` | 客户端 | Inject:1, Redirect:2 |
| `ae.crafting.gui.GuiCraftingTreeTaskBigMixin` | 客户端 | Redirect:2 |
| `ae.terminal.core.GuiNetworkStatusBigMixin` | 客户端 | Inject:4, Redirect:22 |
| `ae.crafting.gui.GuiCraftingCPUTableBigMixin` | 客户端 | Inject:1, Redirect:4, ModifyVariable:1, ModifyArg:1 |
| `ae.crafting.gui.GuiCraftingCPUBigMixin` | 客户端 | Redirect:5 |
| `ae.crafting.gui.GuiCraftingDiagnosticRowBigMixin` | 客户端 | Inject:1, Overwrite:3 |
| `ae.crafting.gui.GuiCraftingDiagnosticTerminalBigMixin` | 客户端 | Overwrite:1, ModifyArg:1 |
| `ae.terminal.pattern.GuiOptimizePatternsBigMixin` | 客户端 | Redirect:7 |
| `ae.terminal.pattern.GuiPatternTermBigMixin` | 客户端 | Redirect:1 |
| `ae.terminal.pattern.GuiPatternValueAmountBigMixin` | 客户端 | Redirect:1 |
| `ae.reshuffle.GuiStorageReshuffleBigMixin` | 客户端 | Inject:1, Overwrite:2, Redirect:17 |
| `ae.stack.ItemMEStackPacketBigMixin` | 客户端 | Inject:1 |
| `ae.stack.AEStackOverlayMixin` | 客户端 | Inject:1, Redirect:1 |
| `ae.flow.PartThroughputMonitorMixin` | 客户端 | Inject:2, Redirect:1 |

合计：200 个注册类，其中 29 个仅客户端；602 个上述注解。接口桥接类可能没有注入注解；该数字不等于实际版本上的命中次数。
