# TST 无限并行与无限能源仓

研究与框架实现日期：2026-10-01。TST 最新发布为 `0.8.0-RC1.3`，GT 最新发布为 `5.09.54.192`。本轮核对了最新 GT 标签下的无线电网、世界存档、团队管理、并行处理器和无线能源仓；这些相关文件与项目当前编译依赖 `5.09.54.190` 的参考源码一致。

## 已落地的框架

| 部分 | 当前实现 |
| --- | --- |
| 配置目录 | `config/apeiron/apeiron.cfg`；旧主配置在新文件不存在时自动迁移。 |
| TST 独立配置 | `config/apeiron/tst-unlimited-parallel.cfg`，一个总开关与各机器独立选择。 |
| 候选机器 | 19 个有 INT_MAX 并行模式的正式机器；包含有前置档位、工作模式和控制器物品条件的模式。 |
| 并行上限 | `ParallelLimit` 表达有限上限或无上限，无上限不使用 `INT_MAX`、`LONG_MAX` 或巨大常数作哨兵。 |
| 精确计划 | `BigParallelPlan` 计算并行、按类型数量、EU/t 与总耗电，乘法使用 `AdaptiveInteger` 的 `long` 快路径及大数回退。 |
| 控制器契约 | `BigTstParallelController` 约定精确并行、上限和耗电读取。 |
| 无线能源契约 | `BigWirelessEnergySource`、`WirelessEnergyNetwork`、`DirectWirelessEnergySource`。 |
| GT 桥接 | `GTWirelessEnergyNetwork` 调用原有团队感知的大数接口，保留原世界存档。 |
| 无限能源仓 | 预留机器偏移 3，默认 ID 31303；在完整控制器能源接入后注册。 |

本阶段配置记录请求适配的机器，尚无机器控制器使用新并行计划。后续适配必须完整覆盖输入、处理、耗电和输出后再启用该机器；不能只修改并行上限。

## 无线电网的实际实现

GT `WirelessNetworkManager` 的账户余额是 `HashMap<UUID, BigInteger>`，通过 `SpaceProjectManager.getLeader(owner)` 找到玩家当前团队账户。

`addEUToGlobalEnergyMap(owner, delta)` 接受完整 `BigInteger`，负值表示扣款。一次调用先标记世界存档需要保存，再查询团队、读取余额、加上增量并判断非负。余额足够时写回；不足时返回 `false`，原余额不变。`long` 和 `int` 重载最终也调用大数方法。

`markDirty()` 标记需要保存，不是在这次扣款中直接序列化或写盘。真正保存时，`GlobalEnergyWorldSavedData.writeToNBT()` 序列化整个能源表。因此每 tick 扣一次电会增加算术和对象分配，不能等同于每 tick 写一次硬盘。

GT 普通无线能源仓从无线余额搬运一批 EU 到本地 `long` 缓存。TecTech 多安无线能源仓还根据 `long` 溢出调整取电周期。这样的仓即使网络余额是大数，本地缓存与机器消费接口仍有固定类型限制。TST 自带的无限无线仓也没有单靠名称消除所有处理器边界。

TST 的 `WirelessEnergyMultiMachineBase` 已有“每轮配方一次性扣整轮电”的模式，总耗电使用 `BigInteger`，但参与乘法的 `getCalculatedEut()` 已经是 `long`，并行计数仍为 `int`。配方可能循环处理 `cycleNum` 次；后续需要把支持大数的公共路径整体接入，不能继续靠提高循环次数模拟无限并行。

## 无限能源仓的接入方式

名称固定为 **无限能源仓**（Infinite Energy Hatch）。它提供无线账户访问能力，由机器控制器发起扣款，仓自身不周期性取电、预存电力、产生 EU 或为机器记录欠款。

1. 结构检查识别能源仓，选择一个有效所有者账户，并验证机器允许该账户的使用方式。多个仓不叠加无线账户额度；不同所有者的仓默认不能组合分担同一笔耗电。
2. 选配方时先模拟材料和输出，得到一个有限的资源上限；从无线余额读取快照，计算可支付下一运行 tick 的并行数量。
3. 建立运行状态前，完成输入与输出的事务预检；在服务器线程内确认扣款及输入提交的顺序，任何失败都不得丢材料或提前输出。
4. 每个有效运行 tick，控制器将本 tick 的全部耗电汇总成一个大数，调用一次 `consumeEUBig(totalEUt)`。整笔扣款成功后才推进进度。
5. 余额不足时暂停当前配方，保留已投入材料、精确并行、产出和进度；暂停恢复不会重复消耗输入或预付之后的 tick。
6. 完成时把精确产出交给已有大数 ME 输出链路。运行中的精确字段与进度写入控制器 NBT；读档不预取下一 tick 的电。

`canConsumeEUBig` 只用于模拟检查，不锁定或预约余额。多个机器可以同时看到同一份余额，正式扣款以 GT 返回值为准；不缓存团队 UUID，玩家变更团队后下一笔操作使用新的团队账户。更换能源仓所有者时需重新建立账户访问对象。

所有无线访问都在服务器线程进行，GT 的原能源表不提供并发线程事务。TST 原生一次性扣整轮电的路径与新每 tick 路径必须互斥，防止重复扣电。固定处理时间的无线模式必须明确沿用原总成本，按运行 tick 分配时保留除法余数，确保累计扣款与原应收总成本一致；不能简单改成“基础 EU/t × 新运行时长”导致成本变化。

仓的电压等级与配方解锁条件仍由机器自己的规则验证。去掉数值上限不自动解锁所有配方；旧 `maxEUInput()`、安培和 EU/t 投影也不能驱动大数模式的核心运算。

## 并行计算与类型边界

“无限”指无固定整数上限，每次实际并行都是有限大整数：

```text
输入上限 = min(每种消耗材料的可用量 / 单份需求量)
实际并行 = min(输入上限, 输出容量上限, 可支付并行, 有限机器上限[若存在])
本 tick 耗电 = 单份精确 EU/t × 实际并行
整轮标称耗电 = 本 tick 耗电 × 配方 tick 数
```

上述整数除法向下取整。催化剂、零消耗输入与非消耗流体单独检查；Ore Dictionary、通配 metadata 和 NBT 匹配继续按原配方语义归并。同一种材料在配方出现多次时先合并需求，再计算上限，不能重复使用同一份库存。无消耗输入的配方也必须提供有限运行目标或输出容量上限，不能拿“无限”状态直接开始循环。

`BigParallelPlan` 要求调用者提供有限输入上限；能耗为零也不会产生“数学上的无限并行”。输出空间可以声明无容量上限，但最终运行数量仍由资源上限确定。计划只进行算术，不消耗材料、扣电或写入产物。

现有 TST/GT 的边界如下：

| 层级 | 原有边界 | 接入要求 |
| --- | --- | --- |
| 机器 | `getMaxParallelRecipes()` 返回 `int`，部分子类直接固定 INT_MAX | 保留旧签名，新增上限状态接口，保留原模式和解锁条件。 |
| GT `ProcessingLogic` | `maxParallel`、`calculatedParallels` 为 `int`，`calculatedEut` 为 `long` | 精确运行状态独立存放，原字段为兼容投影。 |
| TST `GTCM_ParallelHelper` | `currentParallel`、`maxParallel` 为 `int`，`availableEUt` 为 `long` | 新的完整计算链不能再把数量传给这些旧字段后继续计算。 |
| 输入 | 消耗倍率为 `int`，部分最大并行计算用 `double` | AE 输入大数事务与精确配方需求；实体栈边界限制实际操作量。 |
| 超频、概率与机器倍率 | `long`、`float`、`double` 混合运算 | 保留原机器算法与舍入规则，禁止先截断数量再转回大数。 |
| 输出 | Apeiron 公共 ME 大数输出已经接入 | 重用精确列表、容量预检、输出事务与待输出缓存。 |
| 进度、恢复与显示 | 进度通常是 `int` tick，旧数量显示来自基本类型 | 本阶段保留合理配方 tick 格式；数量、EU/t、并行另存与同步大数字段。 |

公共基类接入能覆盖使用该逻辑的机器，但 TST 存在专用处理器、直接固定最大并行的子类和特殊模式。机器选择不等于逐台复制算法，后续可按公共基类、原生无线、特殊处理三组分批接入。

## 配置与候选机器

主配置控制原有按模组分类的 Mixin 与机器 ID；独立 TST 配置记录无限并行请求。总开关和每项选择均默认 `false`。需要 `general.enableUnlimitedParallel=true` 与对应 `machines` 项同时开启，且当前原生模式的并行值为 INT_MAX，才满足请求条件。

| 配置项 | 游戏名称 | 原有条件 |
| --- | --- | --- |
| `GT_TileEntity_IndustrialMagicMatrix` | 工业注魔矩阵 | 英雄证明 |
| `GT_TileEntity_MagneticMixer` | "小型"磁力搅拌机 | INT_MAX 模式 |
| `GT_TileEntity_PhysicalFormSwitcher` | 物质形态转换器 | INT_MAX 模式 |
| `GT_TileEntity_SpaceScaler` | 空间缩放仪 | INT_MAX 模式 |
| `TST_BallLightning` | 球状闪电 | 无线模式或模式 3 |
| `TST_CoreDeviceOfHumanPowerGenerationFacility` | 人类能源设施的核心装置 | INT_MAX 模式 |
| `TST_DeployedNanoCore` | 展开的纳米核心 | INT_MAX 模式 |
| `TST_HephaestusAtelier` | 赫菲斯托斯的工坊 | INT_MAX 模式 |
| `TST_HyperThermalConvector` | 高能态热对流器 | INT_MAX 模式；专用热交换语义需单独核对 |
| `TST_IndistinctTentacle` | 不可视之触 | 无线模式 |
| `TST_IndustrialAlchemyTower` | 工业炼金塔 | 英雄证明 |
| `TST_LargeCanner` | 大型装罐机 | INT_MAX 模式 |
| `TST_LargeIndustrialCokingFactory` | 大型工业炼焦厂 | INT_MAX 模式 |
| `TST_MegaMacerator` | "小型"家用破壁机 | 机壳档位 3 |
| `TST_MegaStoneBreaker` | 硅岩制造机 | 总功率档位至少 29 |
| `TST_MiracleDoor` | 奇迹之门 | INT_MAX 模式 |
| `TST_Scavenger` | 拾荒者 | INT_MAX 模式 |
| `TST_ThermalEnergyDevourer` | 热能饕餮 | 无线或原设置为 INT_MAX 的模式 |
| `TST_VacuumFilterExtractor` | 真空抽滤器 | INT_MAX 模式 |

这里以实际源码中的并行模式为筛选依据，不把每个候选都认定为 TST 官方标注的“最终机器”。测试结构及实验中的空间站处理器未列入正式默认目录。

## 无线接口性能

仓每 tick 直扣一次是当前方案。GT 接口包含团队查找、账户查找、`BigInteger` 加减与存档脏标记；运算成本随数字的位数和机器调用次数增长，不随一笔事务代表的并行数量逐份增长。

实际接口基准位于 `benchmarks/wireless/`。运行命令：

```bash
JAVA_HOME=/home/nahida/.local/share/jdks/temurin-25 ./gradlew benchmarkWirelessEnergy --no-daemon --no-configuration-cache --console=plain
```

任务使用项目配置的 JVM 工具链；本机本轮基准实际使用 Azul JDK 17.0.20.1。JMH 1.37，单线程，每个测项独立 fork；2 次 500ms 预热，3 次 500ms 测量，并记录 GC 分配量。覆盖 1/1024 个账户、63/256/4096 位余额，以及 1 EU 和具有余额一半位数的较大单笔扣款。

两条测量路径分别调用实际 GT `addEUToGlobalEnergyMap`，以及实际 Apeiron `DirectWirelessEnergySource.consumeEUBig`。使用正常的 GT 世界存档对象接收脏标记；只初始化隔离的内存账户，不加载或保存玩家世界。编译依赖为 GT `5.09.54.190`，已核对与最新 `5.09.54.192` 的相关实现一致。

结果写入 `build/reports/jmh/wireless-energy.json`。实测数字与推算见 [无线电网基准说明](../../../benchmarks/wireless/README.md)。微基准只反映这些接口的耗时与分配，不包含游戏 tick、结构检查、配方匹配、网络、真实世界保存和生产环境的 GC 压力。

## 框架验证

2026-10-01 的 `assemble`、Checkstyle 和本轮涉及文件的 Spotless 检查通过；32 项 JUnit 测试全部通过，其中 11 项覆盖新配置、并行和无线能源框架。验证包括早期 Mixin 从游戏目录加载配置并保留禁用状态、旧文件迁移与新文件优先、机器选择与原生模式条件、超过 `long` 的精确计划、余额变化后的正式扣款、模拟不扣款、真实 GT 团队变更和大数余额存档。

JMH 24 个测项完成并保留结果。验证范围为框架与真实无线 API，后续控制器、能源仓方块和游戏内运行流程在对应接入阶段验证。

## 源码依据

- [TST 0.8.0-RC1.3 发布](https://github.com/Nxer/Twist-Space-Technology-Mod/releases/tag/0.8.0-RC1.3)
- [GT 5.09.54.192 发布](https://github.com/GTNewHorizons/GT5-Unofficial/releases/tag/5.09.54.192)
- [GT 无线账户接口](https://github.com/GTNewHorizons/GT5-Unofficial/blob/5.09.54.192/src/main/java/gregtech/common/misc/WirelessNetworkManager.java)
- [GT 无线账户存档](https://github.com/GTNewHorizons/GT5-Unofficial/blob/5.09.54.192/src/main/java/gregtech/common/misc/GlobalEnergyWorldSavedData.java)
- [GT 多安无线仓](https://github.com/GTNewHorizons/GT5-Unofficial/blob/5.09.54.192/src/main/java/tectech/thing/metaTileEntity/hatch/MTEHatchWirelessMulti.java)
- [TST 无线机器公共基类](https://github.com/Nxer/Twist-Space-Technology-Mod/blob/0.8.0-RC1.3/src/main/java/com/Nxer/TwistSpaceTechnology/common/machine/multiMachineClasses/WirelessEnergyMultiMachineBase.java)
- [TST 公共处理逻辑](https://github.com/Nxer/Twist-Space-Technology-Mod/blob/0.8.0-RC1.3/src/main/java/com/Nxer/TwistSpaceTechnology/common/machine/multiMachineClasses/processingLogics/GTCM_ProcessingLogic.java)
- [TST 公共并行计算器](https://github.com/Nxer/Twist-Space-Technology-Mod/blob/0.8.0-RC1.3/src/main/java/com/Nxer/TwistSpaceTechnology/common/machine/multiMachineClasses/processingLogics/GTCM_ParallelHelper.java)

TST 本地源码保存在 `/home/nahida/app/game/minecraft/_temp/Twist-Space-Technology-Mod/`，GT 最新版本研究用文件保存在 `/home/nahida/app/game/minecraft/_temp/GT5-Unofficial-5.09.54.192-research/`。
