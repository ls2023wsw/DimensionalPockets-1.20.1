package net.gtn.dimensionalpocket.common.core.pocket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.gtn.dimensionalpocket.common.ModBlocks;
import net.gtn.dimensionalpocket.common.block.BlockDimensionalPocketWall;
import net.gtn.dimensionalpocket.common.core.ChunkLoaderHandler;
import net.gtn.dimensionalpocket.common.core.DPConfig;
import net.gtn.dimensionalpocket.common.core.utils.CoordSet;
import net.gtn.dimensionalpocket.common.core.utils.Utils;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 全局口袋注册表：内存里维护「口袋 chunk 坐标 -&gt; {@link Pocket}」映射（{@code backLinkMap}），
 * 给新口袋分配互不重叠的 chunk 坐标（螺旋推进），并把整张表 + 生成进度持久化到存档。
 *
 * <p>1.7.10 origin: {@code net.gtn.dimensionalpocket.common.core.pocket.PocketRegistry}。
 * 1.20.1 落地要点（报告 01 §3.7）：
 * <ul>
 *   <li>{@code getWorldForPockets()} -&gt; {@link #getPocketLevel()}，返回 {@code ServerLevel} 且**可能为 null**。</li>
 *   <li>{@code Utils.enforceServer()} 删除，改成「拿不到口袋维度就留痕返回」。</li>
 *   <li>{@code int blockDim} -&gt; {@link ResourceKey}&lt;{@link Level}&gt;，比较用 {@code equals}。</li>
 *   <li>{@code pocketChunkSpacing = 20}、{@code currentChunk} 初值 {@code (0,0,0)}、
 *       {@code nextPocketCoordsDirection} 初值 {@code NORTH} 全部原样保留；
 *       「新世界第一个口袋 y=0、第二个起 y=4」这个上游不一致也**刻意保留**（报告 01 §1.2）。</li>
 * </ul>
 */
public final class PocketRegistry {

    /** 键 = 口袋的 chunkCoords（含 y，{@code CoordSet} 的 equals/hashCode 三轴全参与）。 */
    private static Map<CoordSet, Pocket> backLinkMap = new HashMap<>();

    /** 相邻口袋在 x/z 上相隔的区块数（上游值 20，报告 01 §1.2）。 */
    private static final int pocketChunkSpacing = 20;

    private static PocketGenParameters pocketGenParameters = new PocketGenParameters();

    private PocketRegistry() {}

    /**
     * 口袋维度（1.20.1 取代上游 {@code getWorldForPockets()}）。
     *
     * @return 口袋维度，未加载 / 无服务端时返回 {@code null}（已留痕，调用方必须判空）
     */
    public static ServerLevel getPocketLevel() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            ModLog.denyRepeated("pocket_registry.pocket_level.no_server", 200, "pocket_registry", "pocket_level",
                    "reason=no_server actual=null expected=server_instance dim={}", Reference.POCKET_LEVEL_ID);
            return null;
        }

        ServerLevel level = server.getLevel(Reference.POCKET_LEVEL);
        if (level == null) {
            ModLog.denyRepeated("pocket_registry.pocket_level.not_loaded", 200, "pocket_registry", "pocket_level",
                    "reason=level_not_loaded actual=null expected=loaded_level dim={}", Reference.POCKET_LEVEL_ID);
            return null;
        }

        ModLog.resetRepeated("pocket_registry.pocket_level.no_server");
        ModLog.resetRepeated("pocket_registry.pocket_level.not_loaded");
        return level;
    }

    /**
     * 按口袋 chunk 坐标查口袋（含 y）。
     *
     * @return 命中则返回，未注册返回 {@code null}
     */
    public static Pocket getPocket(CoordSet chunkCoords) {
        if (chunkCoords == null) {
            ModLog.deny("pocket_registry", "get_pocket", "reason=null_chunk_coords actual=null expected=int[3]");
            return null;
        }
        Pocket pocket = backLinkMap.get(chunkCoords);
        if (pocket == null) {
            ModLog.trace("pocket_registry", "get_pocket", "reason=not_registered actual=null expected=pocket chunk={}", chunkCoords);
        }
        return pocket;
    }

    /**
     * 按区块列 (x, z) 查该列的口袋 —— 区块生成器专用（契约 §3.5 新增方法，忽略 y）。
     *
     * <p>同一列**可能有两个房间**（y=0 与 y=4，报告 01 §3.4 的硬结论），这时返回 y 最小的那个；
     * 需要全部命中项时用 {@link #getPocketsForColumn(int, int)}。
     *
     * @return 命中则返回，该列没有口袋返回 {@code null}
     */
    public static Pocket getPocketForColumn(int chunkX, int chunkZ) {
        List<Pocket> pockets = getPocketsForColumn(chunkX, chunkZ);
        if (pockets.isEmpty()) {
            ModLog.trace("pocket_registry", "get_pocket_for_column", "reason=column_has_no_pocket actual=0 expected=1 chunkX={} chunkZ={}",
                    chunkX, chunkZ);
            return null;
        }
        return pockets.get(0);
    }

    /**
     * 按区块列 (x, z) 列出该列的**全部**口袋，按 chunkCoords.y 升序。
     *
     * <p>契约没覆盖的公开成员，但 {@code ChunkGeneratorPocket.fillFromNoise} 需要它：一个 chunk 列里
     * 最多有 2 个房间（y=0 与 y=4），只取一个会漏掉另一个。
     */
    public static List<Pocket> getPocketsForColumn(int chunkX, int chunkZ) {
        List<Pocket> result = new ArrayList<>();
        for (Map.Entry<CoordSet, Pocket> entry : backLinkMap.entrySet()) {
            CoordSet key = entry.getKey();
            if (key == null || entry.getValue() == null) {
                ModLog.deny("pocket_registry", "get_pockets_for_column",
                        "reason=broken_registry_entry actual={} expected=pocket+chunk_coords chunkX={} chunkZ={}",
                        key == null ? "key=null" : "value=null", chunkX, chunkZ);
                continue;
            }
            if (key.x == chunkX && key.z == chunkZ) {
                result.add(entry.getValue());
            }
        }
        result.sort((a, b) -> Integer.compare(a.getChunkCoords().y, b.getChunkCoords().y));
        return result;
    }

    /** 全部口袋的快照（供命令 / 区块加载等遍历用）。 */
    public static Collection<Pocket> allPockets() {
        return new ArrayList<>(backLinkMap.values());
    }

    /**
     * 按 (来源维度, 入口方块坐标) 查口袋，找不到就新建一个（幂等键不是 chunkCoords，报告 01 §3.6）。
     */
    public static Pocket getOrCreatePocket(ServerLevel world, CoordSet blockCoords) {
        if (world == null || blockCoords == null) {
            ModLog.deny("pocket_registry", "get_or_create_pocket", "reason=null_argument actual={} expected=level+coords",
                    world == null ? "world=null" : "coords=null");
            return null;
        }
        ModLog.expect(!world.isClientSide(), "pocket_registry", "get_or_create_pocket",
                "reason=client_side_level actual=client expected=server coords={}", blockCoords);

        ResourceKey<Level> sourceDim = world.dimension();
        for (Pocket pocket : backLinkMap.values()) {
            if (pocket == null) {
                ModLog.deny("pocket_registry", "get_or_create_pocket",
                        "reason=broken_registry_entry actual=null expected=pocket action=skipped dim={} blockCoords={}",
                        sourceDim.location(), blockCoords);
                continue;
            }
            if (Objects.equals(pocket.getBlockDim(), sourceDim) && Objects.equals(pocket.getBlockCoords(), blockCoords)) {
                ModLog.trace("pocket_registry", "get_or_create_pocket", "reason=hit actual=pocket expected=pocket dim={} blockCoords={} chunk={}",
                        sourceDim.location(), blockCoords, pocket.getChunkCoords());
                return pocket;
            }
        }

        // only update currentChunkCoords if pockets already exist（上游注释，报告 01 §1.2）
        // 新世界里 backLinkMap 为空 → 第一个口袋直接用 currentChunk = (0,0,0)，即 y=0
        if (!backLinkMap.isEmpty()) {
            pocketGenParameters.currentChunk = getNextPocketCoords(pocketGenParameters.currentChunk);
        }

        Pocket pocket = new Pocket(pocketGenParameters.currentChunk.copy(), sourceDim, blockCoords);
        backLinkMap.put(pocket.getChunkCoords(), pocket);

        saveData();

        ModLog.step("pocket_registry", "get_or_create_pocket", "created=true dim={} blockCoords={} chunk={} pockets={}",
                sourceDim.location(), blockCoords, pocket.getChunkCoords(), backLinkMap.size());
        return pocket;
    }

    /**
     * 把一个已有口袋重新绑到新的位置（拆下 DP 方块换个地方放，报告 02 §20.5）。
     */
    public static void updatePocket(CoordSet chunkCoords, ResourceKey<Level> newBlockDim, CoordSet newBlockCoords) {
        if (chunkCoords == null || newBlockDim == null || newBlockCoords == null) {
            ModLog.deny("pocket_registry", "update_pocket",
                    "reason=null_argument actual={} expected=chunk_coords+dimension+block_coords chunk={}",
                    chunkCoords == null ? "chunk=null" : (newBlockDim == null ? "dim=null" : "blockCoords=null"), chunkCoords);
            return;
        }

        Pocket link = backLinkMap.get(chunkCoords);
        if (link == null) {
            ModLog.deny("pocket_registry", "update_pocket", "reason=no_pocket_for_chunk actual=null expected=pocket chunk={}",
                    chunkCoords);
            return;
        }

        link.setBlockDim(newBlockDim);
        link.setBlockCoords(newBlockCoords);

        saveData();

        ModLog.step("pocket_registry", "update_pocket", "chunk={} newDim={} newBlockCoords={}", chunkCoords,
                newBlockDim.location(), newBlockCoords);
    }

    public static void saveData() {
        PocketConfig.saveBackLinkMap(backLinkMap);
        PocketConfig.savePocketGenParams(pocketGenParameters);
    }

    /**
     * 必须在 {@code ServerAboutToStartEvent}（{@code MinecraftServer.loadLevel()} 之前）调用，
     * 否则方块实体会先于注册表被读出来（报告 01 §1.6）。
     */
    public static void loadData() {
        backLinkMap = PocketConfig.loadBackLinkMap();
        pocketGenParameters = PocketConfig.loadPocketGenParams();
        if (pocketGenParameters == null) {
            pocketGenParameters = new PocketGenParameters();
            ModLog.deny("pocket_registry", "load_data", "reason=gen_params_null actual=null expected=params action=reset_to_default");
        }

        ModLog.step("pocket_registry", "load_data", "pockets={} currentChunk={} nextDirection={}",
                backLinkMap.size(), pocketGenParameters.currentChunk, pocketGenParameters.nextPocketCoordsDirection);
    }

    /**
     * 开服时把所有口袋的 6 个连接器方块的 {@code CONNECTOR} 状态补成 true（取代上游的 metadata 1）。
     */
    public static void validatePocketConnectors() {
        ServerLevel pocketLevel = getPocketLevel();
        if (pocketLevel == null) {
            ModLog.deny("pocket_registry", "validate_connectors", "reason=no_pocket_level actual=null expected=server_level action=aborted");
            return;
        }

        Block wallBlock = ModBlocks.DIMENSIONAL_POCKET_WALL.get();
        if (wallBlock == null) {
            ModLog.deny("pocket_registry", "validate_connectors",
                    "reason=wall_block_not_registered actual=null expected=dimensional_pocket_wall action=aborted");
            return;
        }

        int checked = 0;
        int enforced = 0;
        int mismatched = 0;
        for (Pocket pocket : backLinkMap.values()) {
            if (pocket == null) {
                continue;
            }
            for (Direction side : Utils.VALID_DIRECTIONS) {
                // 这个调用会在连接器还没建全时补建（上游注释）
                CoordSet coords = pocket.getConnectorCoords(side);
                if (coords == null) {
                    ModLog.deny("pocket_registry", "validate_connectors",
                            "reason=no_connector_coords side={} actual=null expected=int[3] chunk={}", side, pocket.getChunkCoords());
                    continue;
                }

                BlockPos pos = coords.toBlockPos();
                BlockState state = pocketLevel.getBlockState(pos);
                checked++;
                if (!state.is(wallBlock)) {
                    mismatched++;
                    ModLog.warn("pocket_registry", "validate_connectors",
                            "reason=connector_pos_is_not_pocket_wall pos={} actual={} expected={} chunk={}",
                            pos, ModLog.st(state), ModLog.st(wallBlock.defaultBlockState()), pocket.getChunkCoords());
                    continue;
                }
                if (!state.getValue(BlockDimensionalPocketWall.CONNECTOR)) {
                    pocketLevel.setBlock(pos, state.setValue(BlockDimensionalPocketWall.CONNECTOR, Boolean.TRUE), Block.UPDATE_ALL);
                    enforced++;
                }
            }
        }

        ModLog.step("pocket_registry", "validate_connectors", "pockets={} checked={} enforced={} mismatched={}",
                backLinkMap.size(), checked, enforced, mismatched);
    }

    /**
     * 开服时把所有口袋房间所在的 chunk 注册为强制加载（1.20.1 取代 ForgeChunkManager，契约 §4.6）。
     */
    public static void initChunkLoading() {
        if (!DPConfig.keepPocketRoomsChunkLoaded()) {
            ModLog.deny("pocket_registry", "init_chunk_loading",
                    "reason=config_disabled actual=false expected=true config=general.keepPocketRoomsChunkLoaded pockets={}",
                    backLinkMap.size());
            return;
        }

        int registered = 0;
        for (Pocket pocket : backLinkMap.values()) {
            if (pocket == null) {
                ModLog.deny("pocket_registry", "init_chunk_loading",
                        "reason=broken_registry_entry actual=null expected=pocket action=skipped");
                continue;
            }
            ChunkLoaderHandler.addPocketToChunkLoader(pocket);
            registered++;
        }

        ModLog.step("pocket_registry", "init_chunk_loading", "pockets={} registered={}", backLinkMap.size(), registered);
    }

    /**
     * 螺旋推进分配下一个口袋的 chunk 坐标（上游 {@code :42-71}，逐字复刻）。
     *
     * <p>要点（报告 01 §3.4）：
     * <ol>
     *   <li>{@code offset.x}/{@code offset.z} 乘 {@code pocketChunkSpacing}，{@code offset.y} 不乘。</li>
     *   <li>循环体至少执行一次，所以每次调用都会把 y 重置为 4。</li>
     *   <li>转向用 {@link #rotateClockwiseAroundUp(Direction)}（上游 {@code getRotation(UP)} 的等价物）。</li>
     * </ol>
     */
    private static CoordSet getNextPocketCoords(CoordSet currentCoords) {
        // 上游这里是 result = currentCoords（同一个对象），这里复制一份避免别名副作用；行为等价
        CoordSet result = currentCoords.copy();

        // step until an empty spot has been reached
        while (backLinkMap.containsKey(result)) {
            // create offset for next pocket
            CoordSet offset = new CoordSet().addDirection(pocketGenParameters.nextPocketCoordsDirection);
            offset.x *= pocketChunkSpacing;
            offset.z *= pocketChunkSpacing;

            // create result coordset
            result = currentCoords.copy().addCoordSet(offset);
            // needed to bring old saves to this height（上游注释）
            result.y = 4;
        }

        // create test offset to check for the next pockets direction
        Direction clockwiseTurn = rotateClockwiseAroundUp(pocketGenParameters.nextPocketCoordsDirection);
        CoordSet probeOffset = new CoordSet().addDirection(clockwiseTurn);
        probeOffset.x *= pocketChunkSpacing;
        probeOffset.z *= pocketChunkSpacing;

        // check if probed coordset is mapped to a pocket already
        CoordSet probeCoords = result.copy().addCoordSet(probeOffset);
        if (!backLinkMap.containsKey(probeCoords)) {
            pocketGenParameters.nextPocketCoordsDirection = clockwiseTurn;
        }

        return result;
    }

    /**
     * 1.7.10 {@code ForgeDirection.getRotation(ForgeDirection.UP)} 的等价物：绕 UP 轴顺时针 90°。
     *
     * <p><b>这是推断，不是从上游源码读到的</b>（报告 01 §16.1 第 1 条）：本机既没有 Forge 1.7.10 源码，
     * 上游 clone 里也没有 {@code ForgeDirection.java}。依据是：
     * ① 上游调用点的变量名就叫 {@code clockwiseTurn}；
     * ② 1.7.10 的 {@code getRotation(axis)} 是「this 与 axis 的叉积」，NORTH(0,0,-1) × UP(0,1,0) = EAST(1,0,0)，
     *    与「俯视顺时针」一致。
     *
     * <p>表内容：{@code NORTH -> EAST -> SOUTH -> WEST -> NORTH}。1.20.1 的 {@link Direction} 没有
     * {@code getRotation}，只能自己写。表选错不会破坏存档（老存档的 backLinkMap 从 JSON 读回），
     * 只会让**新世界**的房间分布与上游不同。
     */
    private static Direction rotateClockwiseAroundUp(Direction direction) {
        switch (direction) {
            case NORTH:
                return Direction.EAST;
            case EAST:
                return Direction.SOUTH;
            case SOUTH:
                return Direction.WEST;
            case WEST:
                return Direction.NORTH;
            default:
                // 螺旋游标只在水平方向转，UP/DOWN 不可达；真到了这里就原样返回并留痕
                ModLog.warn("pocket_registry", "rotate_clockwise",
                        "reason=non_horizontal_direction actual={} expected=NORTH|EAST|SOUTH|WEST fallback=unchanged", direction);
                return direction;
        }
    }
}
