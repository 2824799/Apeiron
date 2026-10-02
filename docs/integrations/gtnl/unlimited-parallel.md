# GT Not Leisure 无限并行与无线能源接入

本阶段建立 GTNL 的独立配置和并行计划框架，复用 [TST 的无限能源仓方案](../tst/unlimited-parallel.md)。机器开关记录完整控制器适配的选择；配置和纯计算工具本身不修改原生机器执行。

## 参考版本

2026-10-01 核对了上游 [GT-Not-Leisure](https://github.com/ABKQPO/GT-Not-Leisure)：

- 有版本号的最新发布为 [0.2.7-rc1](https://github.com/ABKQPO/GT-Not-Leisure/releases/tag/0.2.7-rc1)，发布于 2026-09-25。
- `releases/latest` 当前指向长期更新的 [dev-build](https://github.com/ABKQPO/GT-Not-Leisure/releases/tag/dev-build)，该页面包含多个 GTNH 分支的产物，不能只按这个标签判断代码时间。
- 已下载 2026-10-01 03:43:38 UTC 更新的 [dev-290 源码 JAR](https://github.com/ABKQPO/GT-Not-Leisure/releases/download/dev-build/sciencenotleisure-dev-290-sources.jar)，存放于工作区 `_temp/GT-Not-Leisure-dev-290-research/`，解压源码位于其 `sources/`。
- `MultiMachineBase`、`WirelessEnergyMultiMachineBase`、`GTNLProcessingLogic`、`GTNLParallelHelper` 与 `_temp/GT-Not-Leisure/` 中的 `0.2.7-rc1` 对应文件逐字节一致。开发客户端当前使用后者。

Forge 模组 ID 为 **`sciencenotleisure`**。框架目录使用 `gtnl`，配置与 TST 分开加载，未安装 GTNL 时也能生成配置和执行纯计划测试，框架没有对 GTNL 类的直接链接。

## 已实现的框架

| 文件／接口 | 职责 |
| --- | --- |
| `config/apeiron/gtnl-unlimited-parallel.cfg` | GTNL 总开关、7 个候选机器开关，默认全部关闭 |
| `UnlimitedParallelConfig<M>` | TST／GTNL 共用的配置加载器，每个实例独立保存不可变选择快照 |
| `GtnlParallelMachine` | 稳定类名配置键、中文名称及原生无限模式的准确位宽 |
| `GtnlUnlimitedParallelConfig` | GTNL 配置入口；总开关、机器选择和原生模式同时满足才产生适配请求 |
| `GtnlParallelPolicy` | 纯计划策略，保留 GT 电源面板的手动并行限额 |
| `ParallelLimit`／`BigParallelPlan` | 共用的无限状态和材料、输出空间、能源限制计算 |
| `BigParallelController` | 共用的大数并行、EU/t、总耗电接口 |
| `BigGtnlParallelController`／`BigTstParallelController` | 两个模组的完整控制器适配接口，继承同一契约 |
| `DirectWirelessEnergySource`／`GTWirelessEnergyNetwork` | 共用的无缓存 GT 无线账户访问与整笔扣款 |

`TstUnlimitedParallelConfig` 原有文件名、分类、配置键和方法保留。两个模组的选择不会互相启用、关闭或覆盖。

## 候选机器与原有条件

以下候选来自实际的并行上限方法，未将超频次数、流体容量、UI 输入边界或输出拆栈的 `MAX_VALUE` 当作无限并行标记。

| 配置键 | 中文名称 | 原生无限模式 | 适配需要保留的路径 |
| --- | --- | --- | --- |
| `IndustrialArcaneAssembler` | 工业奥术装配室 | `getMaxParallelRecipes() == INT_MAX` | 研究条件、奥术物品处理及完成回调 |
| `LapotronChip` | 兰波顿工厂 | `getMaxParallelRecipes() == INT_MAX` | 标准配方、超频与机器条件 |
| `TeleportationArrayToAlfheim` | 精灵传送法阵 | `getMaxParallelRecipes() == INT_MAX` | 魔力模拟输入、催化剂及特殊配方逻辑 |
| `CheatOreProcessingFactory` | 作弊者的究极集成矿处 | `getMaxParallelRecipes() == INT_MAX` | 独立矿处、未匹配物品返回及原有无耗电行为 |
| `NineIndustrialMultiMachine` | 大明科技 | `getMaxParallelRecipes() == INT_MAX` | 无线循环、工作模式与输出倍率 |
| `EternalGregTechWorkshop` | 永恒格雷工坊 | `getMaxParallelRecipes() == INT_MAX` | 控制器和模块分别运行，燃料、升级、模块耗电独立处理 |
| `AssemblerMatrix` | 装配矩阵 | 调试机壳下 `mMaxParallelLong == LONG_MAX` | 原始 long 字段、样板队列、样板执行与输出缓存 |

装配矩阵的 `getMaxParallelRecipes()` 直接把 `mMaxParallelLong` 强制转换为 `int`，因此 `LONG_MAX` 会变成 `-1`。判断其模式必须先读取原始字段并确认调试机壳条件，不能从旧方法或饱和投影反推。普通奇点机壳贡献的 `INT_MAX × 机壳数量` 仍是有限结构上限，不会自动变为无限。

`ReactionFurnace.getMaxParallelRecipesLong()` 按并行控制器档位计算 `baseParallel × 8192 - 1`。即使其 `int` 方法返回饱和值，仍是有限档位；不加入上述无限模式目录。其他并行控制仓、蒸汽机器和有限档位机器同样保留结构与升级限制。

## 通用处理与输出接入点

GTNL 大量机器继承 `MultiMachineBase`，它创建 `GTNLProcessingLogic`，由 `GTNLParallelHelper` 处理输入、并行和输出。标准路径可以共用完整适配，但需要贯通以下顺序：

1. 在 `GTNLProcessingLogic`／`GTNLParallelHelper` 的输入模拟阶段匹配配方，读取精确材料数量；保留矿词、NBT、分离输入、催化剂、配方锁定、研究条件和机器超频规则。
2. 使用实际机器模式及电源面板配置计算上限。读取真实原生上限，满足 GTNL 配置请求时才转换为 `ParallelLimit.unlimited()`；如果 `alwaysMaxParallel == false`，继续受 `powerPanelMaxParallel` 限制。
3. `BigParallelPlan` 以有限材料快照、输出空间及下一运行 tick 可用的无线 EU 计算实际并行；任何无限模式都仍得到一个有限的实际任务。
4. 成功确认能源和输出能力后再提交输入。原生 `GTNLProcessingLogic.applyRecipe()` 到达时已经消耗输入，不能在其后发现无线余额不足才开始处理回滚。
5. 在 `MultiMachineBase.runActiveMachineTick()` 的 `onRunningTick()` 接入一次汇总扣电，成功后才推进进度。余额不足时保留进度、已投入材料和精确产出计划。
6. 在 `flushRecipeOutputs()` 转交精确产出，随后仍调用 `triggerOutputAfterRecipe()` 与 `finishRecipeProgress()`，保持机器完成回调。

原生 `GTNLParallelHelper` 使用 `int currentParallel`，EU 可用量为 `long`，输入／产出使用 `ItemStack[]`、`FluidStack[]`。它把大产出反复拆成 `Integer.MAX_VALUE` 的物理栈；大数路径应按类型合并到 `BigMachineOutputQueue`，避免数组大小随物品数量增长。概率、倍率和自定义输出回调仍需要保留原有语义，不能用期望值直接替代概率抽取。

三种无限 ME 输出设备已提供通用的大数事务接口。采用标准 GT `HatchElement` 或输出注册方法的结构复用现有总成双通道识别；GTNL 自定义的强制类型、分层蒸馏塔输出及蒸汽结构需检查各自结构方法。控制器的大数产出通过 `BigMachineOutputQueue` 进入物品／流体事务；物理库存仍按预算分块，未接受余量保留并随控制器或掉落物存档。

当前框架不把 GTNL 原生 `ItemStack[]` 自动替换成大数队列。单纯装上无限 ME 输出设备扩大的是接收端能力，无法消除控制器前面的输入、并行、概率、EU 和数组边界。

## 无线与独立执行路径

GTNL 和 TST 都调用 GT 的 `WirelessNetworkManager`。共用 `DirectWirelessEnergySource` 即可保持团队归属、实际余额及 GT 存档，无需建立第二套账户。GT 无线余额本身已经是 `BigInteger`，接口性能及测量边界见 [真实 GT 无线电网基准](../../../benchmarks/wireless/README.md)。

`WirelessEnergyMultiMachineBase` 当前循环调用 `wirelessModeProcessOnce()`，每轮先处理配方，再按 `long calculatedEut × duration` 转成大数扣除整轮费用，并把物理数组累加。`cycleNum` 默认 100000，`maxParallelStored`、`cycleNow`、累计运行时间仍是 `int`；把余额扩大不能解决这些限制。

直接无线路径应把一笔任务的精确总费用保存到运行状态，由控制器每个运行 tick 扣一笔汇总费用。旧无线预扣费路径与新路径必须互斥，保存明确的结算模式和已扣费用，避免重载、循环和切换机器模式时重复扣电。若维持原始整轮费用而分期扣款，用商和余数分配到实际运行时间，不能丢失最后几个 EU。

无限能源仓沿用 [TST 设计](../tst/unlimited-parallel.md) 的 **偏移 3／默认 ID 31303**。它表示直接无线能源来源；不预存本地电量，不缓存账户余额，服务器主线程通过当前拥有者访问 GT 团队账户。同一控制器即使识别多个能源仓，也只结算一次整笔运行费用。

独立路径需要单独接入公共工具：

- **大明科技**的 `createProcessingLogic()`、`checkProcessing()` 有独立处理和倍率，需按机器模式正确处理完整输入／产出。
- **作弊矿处**的 `OP_Process_Wireless()`／`checkProcessing_wirelessMode()` 虽有无线命名，当前实现实际没有无线扣款；应保持其原有无耗电语义。
- **永恒格雷工坊**使用燃料、模块及升级系统；控制器的 `INT_MAX` 与模块的真实配方执行分开，逐一确认结算归属，不能套用普通单配方的 EU/t。
- **装配矩阵**具有 AE 样板、缓存输出与 long 并行字段，其数量还需贯穿样板请求、输入提交、运行状态、输出和存档。
- **反应炉**、蒸汽系统、分层流体输出及其他自定义 `checkProcessing()` 不能仅靠替换公共 helper 获得完整适配。

## 验证

本轮新增 8 项 JUnit 测试，项目总计 40 项全部通过，覆盖：

- 启动时自动生成 GTNL 配置，默认全部关闭，不需要安装 GTNL；旧主配置迁移同时产生 TST／GTNL 文件。
- GTNL 总开关、单机选择、原生模式判断及重新加载；TST／GTNL 的开关相互独立且已保存值持续保留。
- 装配矩阵读取真实 `LONG_MAX`，有限大 long 值、`INT_MAX` 投影和负数溢出值不会被错误识别。
- `10^60 + 17` 的精确并行、产出倍率、EU/t 与总耗电；旧 int／long 投影饱和。
- 手动并行上限、材料、输出空间与下一 tick 的无线余额仍然约束实际并行。

`assemble`、JUnit、`checkstyleMain`、`checkstyleTest` 和涉及修改文件的 Spotless 全部通过。验证范围是配置与纯计划框架；游戏中机器的完整执行需要控制器适配接入后验证。
