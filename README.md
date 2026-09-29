# Apeiron

Apeiron 是面向 GT New Horizons（Minecraft 1.7.10）的模组项目。目标是让配方投入与产出、机器并行数、耗电与储电量，以及 Applied Energistics 物品存储等数量支持任意精度的大整数。

目前仅有可构建的 Forge 模组骨架，尚未改变游戏中的数值、存档格式或网络协议。具体兼容方案会随着功能开发确定。

## 构建

使用 Java 25，在本目录执行：

    ./gradlew build

构建产物位于 build/libs/。工程采用 GTNH Gradle 构建约定。

## 当前结构

- src/main/java/com/silvia/apeiron/：Forge 模组入口与客户端／公共生命周期代理。
- src/main/resources/：模组元数据与本地化资源。
- gradle/、gtnhShared/：Gradle 包装器与 GTNH 构建约定。

新增功能时同步记录适用的 GTNH 版本、数值范围、兼容要求，以及存档和网络数据的处理方式。
