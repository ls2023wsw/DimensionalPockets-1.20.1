# DimensionalPockets-1.20.1 INDEX（模组导航）

> 上级索引：`../WORKSPACE_MAP.md`。**进本模组前先读本文件。**
> 最后更新：2026-10-10（第三轮：**署名改为殇花 + 发布到 GitHub**，见交付说明 §8）

---

## 0. 这是什么

**次元口袋**：1.7.10 模组 [Dimensional Pockets](https://github.com/NPException/Dimensional-Pockets) v1.0.5 到 **MC 1.20.1 + Forge 47.4.0** 的**全功能对等移植**（LGPL v3，原作者 NPException / Jezza / Gellebgs，署名与许可证原样保留）。

核心玩法：放置一个 `dimensional_pocket` 方块 → 每个方块在专属的**口袋维度**里对应一个独立的 **16³ 房间**（内部 14³ 可用）→ 空手右键进入、潜行右键墙出来；房间每面墙有一个「连接器」，可把能量 / 流体 / 物品**穿墙**转发到方块对应的那一面。

- `build.gradle` 的 `version = '1.0.5'`；modId `dimensionalpockets`（上游 `dimensionalPockets` 含大写，1.20.1 不允许）。
- **无编译期外部依赖**（只依赖 Forge + 原版）。
- 规模：**56 java / 8091 行 + 70 资源**。
- 完整交付说明（注册名对照 / 逐项映射 / 丢弃项 / 25 条游戏内测试清单 / 已知风险 / 19 条坑 / §7 渲染修复 / §8 发布记录）：**`移植说明-1.20.1.md`（同目录）**。
- **已开源发布**：**https://github.com/ls2023wsw/DimensionalPockets-1.20.1**（PUBLIC / GitHub 识别 `LGPL-3.0` / 默认分支 `master`）。仓库根另含 `README.md`（面向使用者）、`.gitignore`、`dist/DimensionalPockets-1.20.1-1.0.5.jar`（编译好的 mod）。**1.20.1 移植者署名：殇花**。

---

## 1. 入口与关键路径

| 用途 | 路径 |
|---|---|
| **装配入口** | `src/main/java/net/gtn/dimensionalpocket/DimensionalPockets.java`（注册表 + 配置 + 服务器生命周期） |
| 服务端代理 / 事件宿主 | `common/CommonProxy.java` |
| 日志门面 | `util/ModLog.java`（`[dimensionalpockets][模块][动作] key=value`） |
| 标识与资源常量 | `common/lib/Reference.java`；注册名常量 `common/lib/Strings.java` |
| 配置 | `common/core/DPConfig.java`（`ForgeConfigSpec` → `config/dimensionalpockets-common.toml`） |
| **口袋数据模型** | `common/core/pocket/Pocket.java` + `PocketRegistry.java` + `PocketConfig.java`（存档） |
| **维度与房间生成** | `common/core/ChunkGeneratorPocket.java` + `src/main/resources/data/dimensionalpockets/dimension{,_type}/pocket.json` |
| 维度天空/雾/云 | `client/PocketDimensionEffects.java` |
| 方块 | `common/block/BlockDimensionalPocket.java` / `BlockDimensionalPocketWall.java` |
| 方块实体 | `common/tileentity/TileDP.java`（基类）/ `TileDimensionalPocket.java`（核心）/ `TileDimensionalPocketWallConnector.java` |
| 物品 | `common/items/ItemBook.java` / `ItemEndCrystal.java` / `ItemNetherCrystal.java` |
| 传送 | `common/core/pocket/PocketTeleporter.java` + `PocketTeleportPreparation.java` |
| 客户端渲染 | `client/renderer/PortalRenderer.java` + `client/renderer/tile/TileRenderer*.java` |
| 客户端注册总入口 | `client/ClientSetup.java` |
| 指南书 GUI | `client/gui/InfoBookScreen.java` |
| 资源（方块态/模型/配方/掉落/标签） | `src/main/resources/assets/dimensionalpockets/`、`data/dimensionalpockets/`、`data/minecraft/tags/` |

---

## 2. 全部源码（56 个文件，按包分组）

| 包 | 文件 | 职责 / 何时读 |
|---|---|---|
| `(root)` | `DimensionalPockets.java` | **entrypoint**：`@Mod` 主类，全部 `DeferredRegister`（含 `CHUNK_GENERATORS`）+ 配置注册 + 服务器生命周期（`loadData` / `initChunkLoading` / `saveData`） |
| `common` | `CommonProxy` / `ModBlocks` / `ModItems` / `ModBlockEntities` / `ModTabs` | 注册表与代理。**改注册名 / 加物品先看这里** |
| `common\lib` | `Reference` / `Strings` | 常量。改维度 id / 存档路径 / 贴图路径 / 注册名读这里 |
| `common\core` | `DPConfig` / `ChunkGeneratorPocket` / `ChunkLoaderHandler` | 配置；**房间外壳生成在这里**（`fillFromNoise`，1176 格）；区块常驻加载 |
| `common\core\pocket` | `Pocket` / `PocketRegistry` / `PocketConfig` / `PocketGenParameters` / `PocketSideState` / `PocketTeleporter` / `PocketTeleportPreparation` | **玩法核心**。改房间分配、面状态、连接器、存档格式、传送落点都读这里 |
| `common\core\utils` | `CoordSet` / `TeleportDirection` / `Utils` / `Colour` | 坐标、19 向传送偏移表、杂项工具、颜色 |
| `common\block` | `BlockDimensionalPocket` / `BlockDimensionalPocketWall` | 方块行为（`use` 分支、破坏、掉落）。墙方块带 `CONNECTOR` 方块状态 |
| `common\block\event` | `BlockEventHandler` | 墙方块不可破坏守卫 |
| `common\event` | `InsidePocketEventHandler` | 口袋维度内禁用末影传送（`EntityTeleportEvent.EnderPearl/EnderEntity`） |
| `common\tileentity` | `TileDP` / `TileDimensionalPocket` / `TileDimensionalPocketWallConnector` | **穿墙转发 + 放置/移除/传送准备 + NBT 同步**都在这里 |
| `common\items` | `ItemDP` / `ItemBook` / `ItemEndCrystal` / `ItemNetherCrystal` | 指南书；末地水晶设重生点；地狱水晶切面状态 / 移动连接器；水晶耐久 = 可合成次数 |
| `client` | `ClientProxy` / `ClientSetup` / `PocketDimensionEffects` | 客户端注册总入口（BER / 屏幕 / 命令 / 维度特效） |
| `client\event` | `ClientEventHandler` / `ClientPlayerTickEventHandler` / `RenderEventHandler` | 客户端事件（含 `dptweak` 命令注册） |
| `client\gui` | `InfoBookScreen` | 10 页指南书 + 配方页 + 自绘箭头 |
| `client\renderer` | `PortalRenderer` / `tile\TileRendererPocket` / `tile\TileRendererPocketWall` / `item\ItemPocketRenderer` | 粒子场（非着色器版；**粒子层自建加法混合 `RenderType`**）+ 方块/墙/物品渲染 |
| `client\particles` | `PlayerStreamFX` / `PlayerExplosionFX` | 被吸入的粒子流（后者是上游死代码，保留） |
| `client\theme` / `client\lib` | `PocketTextures` / `TextureWrapper` / `IColourBlindTexture` / `TextureMaps` | 贴图查找与颜色盲变体 |
| `client\utils` | `RenderUtils` / `UtilsFX` / `GuiSheet` / `RecipeHelper` | 面绘制、帧贴图与渲染类型、GUI 贴图坐标、配方查询 |
| `client\commands` | `RenderTweakCommand` | `dptweak` 客户端命令 |
| `util` | `ModLog` | 日志门面（三级：`step` / `allow|deny` / `trace`） |

### 「要做 X → 读 Y」

| 要做的事 | 先读 |
|---|---|
| 改注册名 / 加物品方块 | `common/lib/Strings.java` → `ModBlocks` / `ModItems` / `ModBlockEntities` |
| 改房间大小 / 分配顺序 / 存档格式 | `common/core/pocket/Pocket.java`、`PocketRegistry.java`、`PocketConfig.java` |
| 改房间外壳几何 | `common/core/ChunkGeneratorPocket.java`（**几何必须与上游 1176 格一致**，见交付说明 §2.1） |
| 改穿墙转发（能量/流体/物品） | `common/tileentity/TileDimensionalPocket.java` + `TileDimensionalPocketWallConnector.java` |
| 改面状态机 | `common/core/pocket/PocketSideState.java`（**只有 `NONE` / `ENERGY` 两个值**） |
| 改传送落点 / 失败提示 | `common/core/pocket/Pocket.java`（`teleportTo` / `teleportFrom`）+ `TeleportDirection.java` |
| 改粒子场 / 渲染 | `client/renderer/PortalRenderer.java`（层数/旋转/滚动速度/**混合模式**）+ `client/utils/UtilsFX.java` |
| 改指南书页数或文案 | `client/gui/InfoBookScreen.java` + `assets/dimensionalpockets/lang/{en_us,zh_cn}.json` |
| 改配方 / 掉落 / 标签 | `src/main/resources/data/dimensionalpockets/recipes/`、`loot_tables/`、`data/minecraft/tags/blocks/mineable/` |
| 改维度属性（min_y / height / 天空） | `data/dimensionalpockets/dimension_type/pocket.json` + `client/PocketDimensionEffects.java` |

---

## 3. 构建 / 部署

```bat
E:\pjectAI\z3\02-构建环境\huanjing\build.bat E:\pjectAI\z3\01-模组源码\DimensionalPockets-1.20.1
```

- 产物：`build\libs\DimensionalPockets-1.20.1-1.0.5.jar`；**发布用的副本放 `dist\`**（同内容，随仓库一起发布）。
- 部署：复制到 `J:\game\MC\.minecraft\versions\优化2.0\mods\`（该目录原有 198 个 jar，**无同名冲突**）。
- 发布：改完源码 → 同步到备份仓库 `J:\AItempback\DimensionalPockets-1.20.1\`（**该仓库就是 GitHub 的源**，已排除 `build/`、`.gradle/`、`.mimosa/`）→ `git push`。push 要点见交付说明 §8.3（本机需显式设 `HTTPS_PROXY=http://127.0.0.1:10809`，gh 不读 Windows 系统代理）。
- **jar 校验要点**：确认 `assets/`（blockstates / models / textures / lang）、`data/dimensionalpockets/`（**dimension、dimension_type、worldgen/biome 三份 JSON 缺一世界就加载失败**）、`data/minecraft/tags/`、`META-INF/mods.toml`、`pack.mcmeta` 都在。
- **后台长任务启动方式**：`Write` 工具写的 `.bat` 是 LF 换行会被 cmd 误解析；PowerShell 5.1 跑无 BOM 的 UTF-8 脚本会按 GBK 解码而弄坏中文路径。正解是用 WMI 直接起
  `cmd.exe /c call <build.bat> <项目> > <日志> 2>&1`（见交付说明 §5.16）。

---

## 4. 相关

- **交付说明（必读）**：`移植说明-1.20.1.md`（同目录）—— 注册名对照、逐项功能映射、丢弃项与原因、**25 条游戏内测试清单**、已知风险、**19 条坑**、**§7 渲染修复记录（第二轮）**、**§8 发布记录（第三轮）**。
- 日志规范：`../03-规范与文档/模组日志规范.md`（**写代码前必读第一部**）
- 跨会话记忆：`memory/2026-10.md` 的 `#[dimensionalpockets-1.0.5]` 节（含 2026-10-10 渲染修复补充）；坑见 `context_cache.md` 的 `#[pit-134]`~`#[pit-140]`
- 备份仓库：`J:\AItempback\DimensionalPockets-1.20.1\`（tag `1.0.5` 首版 / `1.0.5-2` 渲染修复 / `1.0.5-3` 署名+发布）；**同时是 GitHub 远端 `origin`** → https://github.com/ls2023wsw/DimensionalPockets-1.20.1
- 上级地图：`../WORKSPACE_MAP.md`
