# 数值计数基准测试

GT 无线电网的真实接口基准使用根项目的 `benchmarkWirelessEnergy` 任务，见 [无线电网基准说明](wireless/README.md)。以下为独立数值累加测试。

这是与 Forge 构建隔离的 JMH 小工程，用来比较一次数量累加及读取的成本：

| 测项 | 含义 |
| --- | --- |
| `longAdd` | `long` 字段累加 |
| `bigIntegerSmallCachedDelta` | `long` 范围内的 `BigInteger` 累加，增量对象预先创建 |
| `bigIntegerSmallFromLong` | 同上，但每次把 `long` 增量转换为 `BigInteger` |
| `hybridSmall` | `long` 快路径，检查溢出后累加 |
| `int128Small` | 两个 `long` 组成的 128 位计数器，在 `long` 范围内累加 |
| `bigIntegerLargeFromLong` | 起始值为 2^80，直接用 `BigInteger` 累加 |
| `hybridLarge` | 起始值为 2^80，混合计数器的大数路径 |
| `int128Large` | 起始值为 2^80，128 位计数器累加 |

在仓库根目录使用 Java 25 执行：

```bash
./gradlew -p benchmarks benchmark
```

每项有 3 次预热、5 次测量、2 个独立 JVM 进程，每次迭代 1 秒。控制台报告 `ns/op`（越小越快）以及 `gc.alloc.rate.norm`（每次操作分配的字节数）；原始结果写入 `benchmarks/build/reports/jmh/results.json`。可用 `-PbenchFilter=hybridSmall` 只运行一项。

计数对象在每轮测量前创建，因此结果**不包含**创建计数器、存档、NBT、网络传输或游戏内的其他开销。大数场景与 `long` 无法比较数值上限。这里的 128 位计数器只接受非负增量，数值范围是 0 到 2^127−1；溢出时抛出异常，不能单独实现无上限。本测试的混合计数器是独立的简化模型，不能当作 AE2InfinityCell 或未来 Apeiron 实现的实测结果；将来有实际计数类后应替换这里的模型并重跑。

## 一次本机实测

2026-09-29，Intel Core i7-12650H、Temurin JDK 25.0.4、JMH 1.37，单线程。数据是 2 个进程各 5 次测量的平均值：

| 测项 | ns/op | B/op | 相对 `longAdd` |
| --- | ---: | ---: | ---: |
| `longAdd` | 0.415 | 约 0 | 1× |
| `hybridSmall` | 1.770 | 约 0 | 4.3× |
| `int128Small` | 1.748 | 约 0 | 4.2× |
| `bigIntegerSmallCachedDelta` | 11.782 | 64 | 28.4× |
| `bigIntegerSmallFromLong` | 11.919 | 64 | 28.7× |
| `bigIntegerLargeFromLong` | 11.597 | 72 | 不适用 |
| `hybridLarge` | 9.622 | 72 | 不适用 |
| `int128Large` | 1.755 | 约 0 | 不适用 |

本轮固定 128 位计数器在 2^80 时比 `BigInteger` 大约快 6.6 倍，且累加时没有逐次分配；处于 `long` 范围时与混合计数器的小数值路径耗时接近。它的上限是 2^127−1，若要保持无上限，超过这个范围还得转入 `BigInteger`。这些是单线程、单次累加的微基准数据，不代表游戏内整体性能。
