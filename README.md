# Dimensional Pockets — 1.20.1 Port

Pocket dimensions for **Minecraft 1.20.1 / Forge 47.4.0**: place a *Dimensional Pocket* block, step
inside, and you land in a private 16×16×16 pocket room of your own. Every placed block gets its own
room, and each of the six walls carries a *connector* that can tunnel **energy, fluids and items**
straight through the wall to the machine sitting on the other side.

This is a **full-featured port** of the 1.7.10 mod
[Dimensional Pockets](https://github.com/NPException/Dimensional-Pockets) by
**NPException / Team(GTN)**, released under the **GNU LGPL v3**.

| | |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.0 (loader `[47,)`) |
| modId | `dimensionalpockets` |
| Version | 1.0.5 (tracks the upstream version) |
| License | GNU LGPL v3 (see [`LICENSE`](LICENSE)) |
| Download | [**Releases → 1.0.5**](https://github.com/ls2023wsw/DimensionalPockets-1.20.1/releases/tag/1.0.5) — asset `DimensionalPockets-1.20.1-1.0.5.jar` |

---

## 这是什么 / What it is

**次元口袋**：放置一个次元口袋方块 → 它在专属的**口袋维度**里对应一个独立的 **16³ 房间**
（内部 14³ 可用）→ 空手右键进入、潜行右键墙出来；房间每面墙有一个「连接器」，
可把能量 / 流体 / 物品**穿墙**转发到方块对应的那一面。

- 每个口袋方块 = 一个独立房间，破坏方块会掉回带索引的物品，放回原处仍是**同一个房间**
- 六面独立的状态机（`NONE` / `ENERGY`），用**地狱水晶**切换
- **末地水晶**在房间里可设重生点
- 房间**区块常驻加载**（可配置）
- 指南书（10 页 GUI + 配方页）
- 能量用 Forge Energy（原版是 CoFH RF）；流体走 `IFluidHandler`；物品走 `IItemHandler`

## 安装 / Install

1. 装好 **Minecraft 1.20.1 + Forge 47.4.0**（或任何 1.20.1 Forge 整合包）。
2. 从 [Releases](https://github.com/ls2023wsw/DimensionalPockets-1.20.1/releases/latest) 下载 **`DimensionalPockets-1.20.1-1.0.5.jar`**，丢进 `.minecraft/mods/`。
3. 无前置模组，无编译期外部依赖（只依赖 Forge + 原版）。

## 构建 / Build

本项目**不带 Gradle wrapper**，需要自备环境：

- **JDK 17**
- **Gradle 8.6**（其他 8.x 应该也行）
- **ForgeGradle 6.0.44**（由 `build.gradle` 的 `buildscript` 拉取）

```bash
cd DimensionalPockets-1.20.1
gradle build          # 产物：build/libs/DimensionalPockets-1.20.1-1.0.5.jar
```

依赖仓库按 `build.gradle` 里声明的顺序解析：BMCLAPI 镜像 → `maven.minecraftforge.net` → Maven Central。

## 与上游 1.7.10 的差异 / Differences from the 1.7.10 original

移植遵循「**玩法与观感逐项对齐**」的原则，几何 / 数值 / 存档格式都按上游复刻。主要差异：

**已移除**

- **一切联网行为**：上游的匿名统计上报（DPAnalytics）与版本检查器（VersionChecker）整体移除
- legacy GLSL 着色器管线（1.20.1 不存在；上游该配置默认就是 `false`，所以非着色器版即原行为）
- OpenComputers 真集成、Thaumcraft / TConstruct 跨模组配置（依赖的模组在 1.20.1 侧没有对应物）
- 上游自带的 `oc` 迷你框架（注意：它**不是** OpenComputers，是作者自建框架），改用原生 `Block` / `BlockEntity` / `ForgeConfigSpec` 等价重写

**因 1.20.1 机制而改变的语义**

- 维度从「数字 id 33 + biome 99」改为数据包 `data/dimensionalpockets/dimension{,_type}/pocket.json`
- `usePeacefulRecipes` 配置项保留但**当前不影响配方**（1.20.1 的配方 JSON 是静态的，Forge 47 没有读配置值的 recipe condition）
- 水晶不再可堆叠（1.20.1 设 `durability` 会强制 `maxStackSize=1`）

**已顺手修掉的上游 bug**（详见 `移植说明-1.20.1.md` §5.10）

- 创建者名写进了 `generated` 键（应为 `creator`）
- `chunkCoords` 从未写入 NBT（客户端会 NPE）
- `Pocket#getNBT()` 体内自递归
- `TileDimensionalPocket#onBlockAdded` 里的 `throw new RuntimeException("YOU DESERVED THIS!")`（在 1.20.1 会直接崩服）

## 文档 / Documentation

| 文件 | 内容 |
|---|---|
| [`移植说明-1.20.1.md`](移植说明-1.20.1.md) | **主文档**：注册名对照、逐项功能映射、丢弃项与原因、**25 条游戏内测试清单**、已知风险、移植踩坑记录、渲染修复记录 |
| [`INDEX.md`](INDEX.md) | 源码导航（包职责、「要做 X → 读 Y」表、构建部署说明） |

## 状态 / Status

- ✅ 编译通过，可在 1.20.1 + Forge 47.4.0 加载
- ✅ 已在整合包内实机验证：可进世界、核心玩法（放置 / 进入 / 返回 / 破坏后重回同一房间）可用
- ✅ 渲染修复：物品形态走自定义 BEWLR、墙面粒子场对齐、粒子层加法混合（见 `移植说明-1.20.1.md` §7）
- ⚠️ 完整的 25 条游戏内清单（能量 / 流体穿墙、指南书、配置项等）见 `移植说明-1.20.1.md` §3，逐项实测仍在进行

**已知问题**（留档待办，见 `移植说明-1.20.1.md` §7.6）

- 站在口袋房间里打开背包 / 聊天 / 暂停时，粒子场会被跳过、只剩外框
- 粒子场沿用 `entityTranslucent` 的 shader，会按面法线做光照调制（上游是 `glDisable(GL_LIGHTING)`），六个面亮度略有差异

## 许可与署名 / License & Credits

- **上游**：[NPException/Dimensional-Pockets](https://github.com/NPException/Dimensional-Pockets) v1.0.5，
  作者 **NPException、Jezza、Gellebgs**，许可 **GNU LGPL v3**（`LICENSE` 原样保留）
- **1.20.1 移植**：殇花
- 本移植同样是 **GNU LGPL v3**。再分发或修改时请保留 `LICENSE` 与上游署名。

> 本项目是上游作品的衍生作品（derivative work），不是独立实现。
> 上游所有美术资源（贴图 / GUI）与玩法设计版权归原作者所有。
