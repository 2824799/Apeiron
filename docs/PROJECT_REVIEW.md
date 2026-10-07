# 全项目清理与注入审查（2026-10-07）

本轮检查覆盖 200 个注册 Mixin（602 个注入、重定向、覆盖或访问注解） 的配置归属、目标类和方法，并对关键输入、输出、能源、存档、显示路径核对运行条件。完整清单见 [MIXIN_INVENTORY.md](MIXIN_INVENTORY.md)。验证强度与限制应区分：接口和指令存在、Mixin 实际转换成功、回归用例通过，都不能证明所有第三方交互和玩家存档组合绝无副作用。

## 清理结果

- 将原先正式源码中的 41 个有实际回归用途的验证类移至 `src/verification/java`，通过独立 `apeiron-verification.jar` 执行。正常模组初始化不再判断测试环境变量，也不携带测试执行器。
- 删除没有调用方且依赖真实玩家的 `AEPatternEncodingSmoke`。保留真实数量守恒、取消退款、库存隔离、保存恢复、机器输出等回归，避免把覆盖历史事故的测试当成冗余删除。
- 将 `OptionalFlowStatistics` 中仅供验证的缓存构造方法移到测试夹具。
- 合并重复的 Waila 验证调用；测试夹具与真实功能一样按依赖能力选择，旧 GT 不构造依赖新版 `IOutputHatch` 的夹具。
- 配置选择归入 `MixinFeature`，删除插件中重复的配置与类存在性判断。未知注册命名空间会报错，新增注入必须归属功能组。

## 发现并修复

| 问题 | 修复与约束 |
| --- | --- |
| 多处注入未指定 `require`，未命中也能启动 | 设置 `defaultRequire=1`；只有已知可选位置显式允许零命中，替代注入使用分组约束或能力选择 |
| 公共 AE 栈 Mixin 引用了只存在于客户端的方法 | 分离客户端渲染 Mixin，GUI、物品提示和吞吐显示注册到客户端列表 |
| 指向不存在的 `incCountRequestableCrafts` 方法 | 删除失效注入，保留实际使用的大数接口 |
| CPU 完成数量的调用发生在 lambda 内部，原注入未命中 | 在原生 `completeJob` 的监听器遍历处传递精确数量；保留原回调顺序，用 `finally` 清理未消费的临时数值，防止影响下一条通知 |
| beta-1 的 TaskProgress 使用编译器生成的私有访问器 | 按能力选取旧访问器与新字段路径，验证超过 long 范围的实际递减 |
| beta-1 没有元件 Ctrl 内容数量提示 | 基础字节提示继续适配；只在上游确有内容数量调用时加载对应 Mixin |
| 合成诊断提示读取私有字段，旧版实际通过访问器调用 | 改在最终提示参数中修正已生产数量和样本数，避开字段可见性差异 |
| 网络状态、整理界面错用父接口／父类作为调用目标 | 改为字节码实际使用的 `IAEItemStack` 与 `GuiText` |
| 无限 CPU 的容量、并行提示注入仍定位旧渲染方法 | 合并到现有合成确认界面的 `drawListFG` 适配；仅格式化 CPU 数量时显示无限，物品提示使用原来的精确格式 |
| 关闭轻量 Waila 后仍裁剪原生输出数组 | 原生数组裁剪也遵守轻量开关；裁剪只作用于提示数据，不修改机器实际产物 |

## 注入范围与开关

配置文件为 `config/apeiron/apeiron.cfg`。以下开关在 Mixin 加载时生效，需要重启。涉及数值协议的客户端和服务端必须保持一致。开关用于诊断、兼容与功能选择，不是大数存档降级转换器；关闭核心前应备份存档，不能假定旧模组能读取超范围数据。

| 配置范围 | 控制内容与边界 |
| --- | --- |
| `mixins.appliedenergistics2.enableAeMixins` | AE 精确数值、协议、合成和 GT 公共输入/输出/能源协作核心。它们通过注入接口相互依赖，作为整体关闭，避免只关一半造成类型转换或数据协议错误 |
| `mixins.sciencenotleisure.enableWirelessIntegration` | GTNL 无线批处理；原生无线保持原超频规则，Apeiron 仓按自身配置。该集成也修复原生大数输出，不以安装能源仓作为唯一触发条件 |
| `mixins.gregtech.enableBeamlineInputs` | 束流源室、靶室隔离输入支持与已安装增强模组的配置协作 |
| `mixins.gregtech.enableQuantumEnhancement` | 量子操纵者模块；配方放宽条件以实际安装增强方块为前提 |
| `mixins.gregtech.enableSpaceElevatorIntegration` | 太空电梯主模块供电子模块、大数并行与输出适配 |
| `mixins.tectech.enableEyeOfHarmonyEnhancement` | 鸿蒙之眼增强模块的结构、成功率、时间与补料逻辑；没有模块保留原生处理 |
| 原有 TecTech、TST、Infinity Cell 开关 | 保留大数输出、样板编码防护、无限存储集成的已有控制；按目标类和方法能力选择历史实现 |
| `mixins.programmablehatches.enablePatternOptimization` | 编程仓的样板优化接口 |
| `mixins.waila.enableLightweightSnapshots` | 全局提示数据预算及机器快照裁剪；它确实会影响其他提供者的过大提示。需要完整 NBT 的自定义脚本可关闭；不处理世界保存数据 |
| `mixins.network.enablePayloadDiagnostics` | 给原版超大自定义包异常补充通道信息；不放宽包大小限制、不改变收发结果 |

GT 公共结构注册仅接管 Apeiron 仓室类型；容量检查只在精确输出设备能接收对应通道时放行，保留普通仓容量和过滤规则。运行 tick 的能源接管要求已准备/运行的无线状态，输入采集、Tick 累加、普通发电逻辑继续由原机执行。大数输出属于独立能力，不要求安装能源仓。

AE 核心的数值表示、序列化和若干 `@Overwrite` 是有意的全局修改，不能声称它们仅影响新方块。其相互依赖不适合拆成几百个单注入开关。仍需留意其他模组覆盖相同方法、修改协议或跨版本更改回调的情况。

## 兼容检查方案

详见 [兼容检查说明](../compatibility/README.md)。不用依赖版本的排列组合，保留四套已知核心依赖闭包，运行时根据实际类、方法和必要调用选择代码。

- 日常 `build`：所有历史版本的静态契约检查，加最新支持核心服务端运行验证。
- 日常客户端任务：最新支持核心客户端运行验证。
- 发布标签或手动完整验证：四版服务端与四版客户端，使用同一个开发 JAR。
- 记录类路径、测试 JAR、配置和版本清单作为任务输入。配置摘要排除 Java properties 和 IC2 INI 自动写入的注释时间戳，避免未改配置却反复启动。已通过且输入未变的任务可复用结果；失败、缺少完成标记、依赖解析不符不能产生成功凭据。
- 静态报告记录选择结果、目标方法和可检查的调用/字段位置；运行验证加载所有已选目标，并执行实际行为回归。静态检查不模拟 Mixin 的完整重映射、切片、序数和其他转换器顺序。

## 本轮验证

- `spotlessApply build compatibilityFullCheck` 通过：101 项 JUnit 测试，四版静态契约，四版服务端和四版客户端的真实转换及行为回归。
- 八个成功凭据记录相同开发 JAR：`014272b39d49bc49b360aef1a8a99fdcaaa6fa25aba1fdb9fd62ae9f4eafca79`。
- 正式 JAR、开发 JAR 和源码 JAR 均检查过，未包含 Smoke／verification 测试文件。
- 额外合并环境服务端／客户端通过：AE2 1081、GT 5.09.54.207、TST 0.8.0-RC2.1、GTNL 0.2.7-rc2 等当前本地集成；包括 GTNL 原生／Apeiron 并行、束流输入、锅炉 Tick 消耗、Waila、TST 输出和诸神之锻回归。这是该具体组合的验证，不扩大为历史第三方版本的任意混搭支持。
- 重复执行 `build compatibilityClientCheck` 成功，耗时约 2 秒；四版静态任务及代表客户端／服务端均为 `UP-TO-DATE`，Minecraft 启动次数为零。
- 配置摘要边界验证通过：仅增加注释保持摘要不变，修改实际属性值改变摘要，恢复后摘要与原值一致。验证使用隔离测试目录。

本地证据：`.local/project-audit-full.log`、`.local/project-audit-merged.log`、`.local/project-audit-warm-final.log`、`.local/project-audit-cache-boundaries.log`。这些日志保留在 Git 忽略目录；CI 上传运行凭据、依赖清单、静态报告和日志。
