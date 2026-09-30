# Apeiron

Apeiron 是面向 GT New Horizons（Minecraft 1.7.10）的模组项目。目标是让配方投入与产出、机器并行数、耗电与储电量，以及 Applied Energistics 物品存储等数量支持任意精度的大整数。

当前对接 **AE2 rv3-beta-1076-GTNH**。通过 Mixin 扩展 AE2 自带的物品栈、流体栈和后端，保留原有 `int`／`long` 方法签名；旧接口无法表示精确数量时返回饱和值，新增的大数接口返回 `BigInteger`。数值工具采用 `long` 快路径，溢出后转为 `BigInteger`，并支持回落到 `long`。任意精度消除固定整数位宽的限制，存储单元本身仍遵循其配置容量。

## 当前进度（2026-09-30）

AE 核心存储与输入输出已接入精确数量，并通过开发客户端中的运行时自检。合成、样板、统计和 GUI 已加入多处大数实现；下表记录代码覆盖范围，具体运行验证范围见后文。AE 全适配仍在推进，尚未将每个旧数值路径替换完毕。

| 范围 | 已加入的实现 |
| --- | --- |
| 数值工具与 AE 栈 | `AdaptiveInteger`、精确运算、饱和转换、科学计数法与完整数量格式；物品／流体栈的存储数量、可请求数量和合成次数，复制与列表合并。 |
| 存档与同步 | AE 栈、存储单元、合成任务及诊断的精确 NBT 字段；ByteBuf 编解码、AE 数据包的可选精确扩展及容器同步。 |
| 存储与网络 | 物品／流体 Cell、Cell Handler、库存包装器、网络库存、网络监控、安全库存、创意 Cell；精确容量、已用容量与剩余容量接口。 |
| 网络状态 | `GridStorageCache` 的精确容量／类型数汇总，Network Status 容器同步与容量显示；原有统计接口继续保留。 |
| 输入输出 | Import Bus、Export Bus 的合成结果接收、Formation Plane、Interface、IO Port、物理库存及外部流体 Handler 的数量边界。 |
| 自动化与监控 | Level Emitter、Advanced Level Emitter、Pattern Repeater 存储拦截器、Conversion Monitor、Super ME Replenisher、存储监控与流量统计。 |
| 合成 | 大数请求、`BigCraftingJobFast` 规划、CPU 任务次数、库存／等待数量、最终输出、进度、字节成本与存储占用；样板单次产出读取精确数量。 |
| 样板 | 样板数量 NBT、样板终端乘除、数量输入，以及优化器部分显示与倍率计算。 |
| 显示与诊断 | 终端数量与悬浮提示、合成确认／树／列表／CPU 状态表、合成完成通知、诊断产量／排序／同步及显示。 |
| 整理与扫描 | Storage Reshuffle 转移、回退、取消恢复、报告和扫描记录的精确数量；扫描健康页容量、类型数、百分比与排序的显示接入。 |
| 特殊及外部后端 | Condenser、Void Item／Fluid Inventory，以及 Factorization Barrel、Jabba Barrel、MFR Deep Storage Unit、BetterStorage Crate 的可选反射适配。 |

## AE 物品输入与输出

`BigIMEInventory` 提供物品专用的 `injectItemsBig` 与 `extractItemsBig`；`BigMEInventory` 提供通用 `IAEStack` 的精确输入输出，覆盖物品和流体。返回值沿用 AE 语义：输入返回未放入的余量，全部接受时返回 `null`；输出返回实际取出的栈，没有取出时返回 `null`。

数量通过 `BigAEItemStacks.stackSize(stack)` 或通用的 `BigAEStackValues.get(stack)` 读取为 `BigInteger`。调用端可使用 `BigMEInventories` 在普通 AE 库存引用上分派，原有 `injectItems`／`extractItems` 签名继续可用。

目前精确输入／输出已接入物品和流体存储单元、`MEPassThrough`、`MEInventoryHandler`、`NetworkInventoryHandler`、`NetworkMonitor`、独立监控包装器、安全库存和创意存储单元。网络层保留 AE 的权限、优先级、粘滞库存及两轮存放规则；跨多个单元汇总和提取时使用精确数量。

标准 AE 存储单元的容量来源仍是 AE 的 `long` 接口。需要超过 `long` 的容量时，存储单元物品需额外实现 `BigStorageCell.getBytesBig`；运行时自检使用了这个扩展。`BigCellInventory` 与 `BigCellInventoryHandler` 暴露精确数量、字节容量和类型数；每种物品／流体数量、总数量、NBT 存档与重载保留精确值。

原版 `ItemStack`、玩家背包、实体物品和 Forge 流体接口仍受其物理数据格式限制。跨越这些边界时按 `int` 分块调用，AE 内部余量保持精确；外部接口的模拟操作不重复计算同一份容量。外部流体 Handler 的实际传输已加入循环分块。

未提供精确接口的第三方 AE 库存后端，接到超出 `long` 的请求时会抛出 `UnsupportedBigInventoryException`，防止静默截断。AE 栈和部分同步包在需要时追加 Apeiron 精确字段，同时保留旧字段；客户端与服务端均需安装 Apeiron 才能使用完整的大数同步。

## AE 终端显示

终端物品／流体格中的数量超过 `long` 范围后使用三位有效数字的科学计数法，例如 `9.99E18`；鼠标悬浮提示中的“已存储／物品数量”保留完整大数，并使用千位分隔符。普通 `long` 数量继续使用 AE 原有显示格式。合成与诊断界面、网络容量页及 Storage Reshuffle 页也已加入精确数据读取和格式化。

## AE Mixin 总开关

启动时会自动生成 `config/apeiron.cfg`。其中的 `mixins.enableAeMixins` 控制所有 AE 大数 Mixin，默认值为 `true`：

```text
mixins {
    B:enableAeMixins=true
}
```

将它改为 `false` 后重启游戏即可停用 AE 适配。Mixin 在类加载阶段生效，运行中修改不会立即生效；配置插件只跳过 `com.silvia.apeiron.mixin.ae` 下的 AE Mixin，不影响 Apeiron 的普通数值工具和其他非 AE 代码。

## 待完善与验证边界

- AE 能源核心仍通过 `IEnergySource.extractAEPower(double, ...)` 交互；已适配带电转移中的物品／流体数量，但耗电和储电核心尚未完全改为任意精度。GT 机器并行、EU 消耗和储电的大数化仍是项目目标。
- 超过 `long` 的合成请求目前由 `BigCraftingJobFast` 接管。AE 合成 v2 的 `CraftingRequest`、Resolver 及其独立计数字段尚未全面改为大数，普通请求产生超大中间数量的场景仍需审计。
- 样板优化器已适配部分显示和倍率计算；`GuiOptimizePatterns.amountToCraftI` 等输入／同步入口仍有 `int` 限制，样板编码与限制配置路径需继续审计。
- GUI 的实际打开、排序、悬浮提示、超大数值比例，以及完整合成提交／合并／取消流程仍需端到端验证。可选外部仓储适配也需在安装对应模组的环境中验证。

## 验证

2026-09-30 的开发环境中，`compileJava` 和启用自检的 `runClient` 均成功。已通过的运行时自检包括 AE 栈及列表、物品单元的模拟／写入／提取／重载、物理库存边界、流体网络、Fluid Cell Handler 精确数量桥接，以及 Reshuffle 转移与取消恢复的数量守恒。

日志中的成功标记：

```text
AEItemStack big-count runtime verification passed
AE item input/output big-count runtime verification passed
AE physical inventory, fluid network and reshuffle runtime verification passed
```

在本目录执行编译与运行自检：

```bash
JAVA_HOME=/home/nahida/.local/share/jdks/temurin-25 \
./gradlew compileJava --no-daemon --console=plain

JAVA_HOME=/home/nahida/.local/share/jdks/temurin-25 \
APEIRON_VERIFY_STACK=1 APEIRON_VERIFY_EXIT=1 \
./gradlew runClient --no-daemon --console=plain
```

其他环境请将 `JAVA_HOME` 改为本机 Java 25 路径。`APEIRON_VERIFY_STACK=1` 开启三组自检，`APEIRON_VERIFY_EXIT=1` 在自检成功后退出客户端。启动自检仅覆盖上述断言及其触发的类加载，不代表所有 GUI 或可选集成都已执行。

## 构建

使用 Java 25，在本目录执行：

    ./gradlew build

构建产物位于 build/libs/。工程采用 GTNH Gradle 构建约定。

## 当前结构

- src/main/java/com/silvia/apeiron/：Forge 模组入口与客户端／公共生命周期代理。
- src/main/java/com/silvia/apeiron/math/：数值工具、格式化与编解码。
- src/main/java/com/silvia/apeiron/ae/：AE 大数接口、桥接工具与运行时自检的根包。
- src/main/java/com/silvia/apeiron/ae/stack/：AE 物品／流体栈及其精确数量操作。
- src/main/java/com/silvia/apeiron/ae/storage/：ME 库存、Cell、容量、物理库存和库存适配器。
- src/main/java/com/silvia/apeiron/ae/automation/：输入输出、Emitter、Pattern Target 和供电转移桥接。
- src/main/java/com/silvia/apeiron/ae/crafting/：合成核心、诊断和网络包，分别位于 `core/`、`diagnostics/`、`packets/`。
- src/main/java/com/silvia/apeiron/ae/terminal/：终端数量、提示、容器和网络状态。
- src/main/java/com/silvia/apeiron/ae/flow/：流量统计、格式化和同步数据。
- src/main/java/com/silvia/apeiron/ae/reshuffle/：Storage Reshuffle、扫描和恢复对象。
- src/main/java/com/silvia/apeiron/ae/sync/：通用精确数值编解码、数据包负载和同步注册桥接。
- src/main/java/com/silvia/apeiron/ae/compat/：Condenser 和可选反射后端。
- src/main/java/com/silvia/apeiron/ae/smoke/：开发环境运行时自检。
- src/main/java/com/silvia/apeiron/crafting/：精确数量合成计算器。
- src/main/java/com/silvia/apeiron/config/：Forge 配置与 AE Mixin 总开关。
- src/main/java/com/silvia/apeiron/mixin/：Mixin 配置插件与 AE 适配的根包。
- src/main/java/com/silvia/apeiron/mixin/ae/stack/：AE 物品／流体栈、列表、数据包。
- src/main/java/com/silvia/apeiron/mixin/ae/storage/：Cell、库存、网络库存、监控和物理库存桥接。
- src/main/java/com/silvia/apeiron/mixin/ae/automation/：输入输出总线、Interface、Formation Plane、Emitter 和 IO Port。
- src/main/java/com/silvia/apeiron/mixin/ae/crafting/：合成核心与合成 GUI，分别位于 `core/` 和 `gui/`。
- src/main/java/com/silvia/apeiron/mixin/ae/terminal/：终端核心、等级发射器和样板终端，按子包继续分类。
- src/main/java/com/silvia/apeiron/mixin/ae/flow/：流量统计、吞吐量和同步。
- src/main/java/com/silvia/apeiron/mixin/ae/reshuffle/：Storage Reshuffle 与扫描。
- src/main/java/com/silvia/apeiron/mixin/ae/replenisher/：Super ME Replenisher。
- src/main/java/com/silvia/apeiron/mixin/ae/compat/：Condenser、Void Inventory 和可选第三方存储后端。
- src/main/resources/：模组元数据与本地化资源。
- gradle/、gtnhShared/：Gradle 包装器与 GTNH 构建约定。
- benchmarks/：独立的 JMH 数值性能基准，运行方式见 [基准测试说明](benchmarks/README.md)。

AE2 参考源码位于工作区的 `_temp/Applied-Energistics-2-Unofficial/`。
