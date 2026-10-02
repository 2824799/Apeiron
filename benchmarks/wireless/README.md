# GT 无线电网直扣基准

该基准在独立 JVM 中调用实际 `WirelessNetworkManager.addEUToGlobalEnergyMap` 和 Apeiron `DirectWirelessEnergySource.consumeEUBig`。正常的 GT 存档对象接收脏标记，账户初始化只发生在基准进程内，不访问玩家世界。

在 Apeiron 根目录运行：

```bash
JAVA_HOME=/home/nahida/.local/share/jdks/temurin-25 ./gradlew benchmarkWirelessEnergy --no-daemon --no-configuration-cache --console=plain
```

JMH 1.37，单线程，每项独立 fork，2 次 500ms 预热、3 次 500ms 测量，包含 GC 分配统计。覆盖 1/1024 个账户与 63/256/4096 位余额；`one` 每次扣 1 EU，`halfBalance` 每次扣 `2^floor(balanceBits / 2)` EU，余额在每轮开始时重置。两种测项都在测量期间保持余额充足。

`gregTechDebit` 预先构造负增量；`apeironDebit` 包含非负校验、网络可用性检查与负增量构造。模拟检查、源对象创建与账户初始化不计入单次扣款。不得提高 JMH 线程数：GT 能源表和团队表按服务器单线程模型设计。

## 本机结果：2026-10-01

Intel Core i7-12650H，Azul JDK 17.0.20.1，512 MiB 堆。Gradle 启动用 Java 25，基准 JVM 由项目的 Java 工具链选择，实际为 Java 17。使用 GT `5.09.54.190` 编译依赖；相关无线接口源码已经与最新 `5.09.54.192` 标签核对一致。

下表为轮询 **1024 个账户**的平均值，`ns/op` 越小越快：

| 余额位数 | 单笔扣款 | GT 接口 ns/op | Apeiron 直扣 ns/op | Apeiron 分配 B/op |
| ---: | --- | ---: | ---: | ---: |
| 63 | 1 EU | 21.81 | 22.85 | 64.01 |
| 63 | 2^31 EU | 21.11 | 26.92 | 64.01 |
| 256 | 1 EU | 24.70 | 24.34 | 88.01 |
| 256 | 2^128 EU | 27.47 | 26.22 | 88.01 |
| 4096 | 1 EU | 65.26 | 65.64 | 568.02 |
| 4096 | 2^2048 EU | 100.24 | 99.84 | 568.03 |

1/1024 账户的全部 24 个结果见 [CSV 快照](results/2026-10-01.csv)，包含 JMH 误差与分配量。原始 JSON 生成于 `build/reports/jmh/wireless-energy.json`。本次迭代与 fork 数较少，适合判断数量级，不适合据此判定几个纳秒的实现差异。

在这组数据里，直接调用无线电网每次约 **16–100 ns**，包括大数余额与大数扣款。调用次数按机器数量计算，与这次代表的并行数量没有逐份调用关系。

按本机 256 位余额、大数单笔扣款的 Apeiron 数据作算术推算，1000 台运行机器每 tick 各扣一次，只计这些调用约 **0.026 ms/tick**，以 20 TPS 运行约产生 **1.76 MB/s** 分配。若 10000 台机器使用 4096 位余额和大数单笔扣款，对应约 **1.00 ms/tick** 与 **113.6 MB/s** 分配，GC 压力就值得关注。

上述推算不是游戏实测，也不包含真实服务器的 GC、调度、结构检查、配方匹配、显示、网络和世界保存。结论是当前“每个控制器每运行 tick 汇总扣一次”的框架可行；应避免按每个并行配方扣款，以及每个能源仓各自定时扣款。下一阶段接入机器后再测实际 TPS、GC 和真实耗电数量。
