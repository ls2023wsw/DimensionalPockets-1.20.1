package net.gtn.dimensionalpocket.common.core.pocket;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

import net.gtn.dimensionalpocket.common.ModBlocks;
import net.gtn.dimensionalpocket.common.block.BlockDimensionalPocketWall;
import net.gtn.dimensionalpocket.common.core.utils.CoordSet;
import net.gtn.dimensionalpocket.common.core.utils.TeleportDirection;
import net.gtn.dimensionalpocket.common.core.utils.Utils;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.gtn.dimensionalpocket.common.tileentity.TileDimensionalPocketWallConnector;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 一个「口袋」的完整模型。
 *
 * <p>1.7.10 origin: {@code net.gtn.dimensionalpocket.common.core.pocket.Pocket}. 1.20.1 落地要点：
 * <ul>
 *   <li>{@code ForgeDirection} -&gt; {@link Direction}；{@code int blockDim} -&gt; {@link ResourceKey}&lt;{@link Level}&gt;。</li>
 *   <li>存档 JSON（{@code teleportRegistry.json}）走 Gson 反射字段；方块实体 NBT 走 {@link #writeToNbt(CompoundTag)}
 *       / {@link #readFromNbt(CompoundTag)}，两条通道都保留（报告 01 §0.2）。</li>
 *   <li>房间外壳**不再由本类写方块**：改由 {@code ChunkGeneratorPocket.fillFromNoise} 铺（契约 §4.5）。
 *       {@link #generatePocketRoom(String)} 只登记 {@code isGenerated} / creator / 默认连接器。</li>
 *   <li>修正了上游 3 个 bug（报告 01 §0.3）：{@code creator} 不再写进 {@code generated} 键、
 *       {@code chunkCoords} 一定写进 NBT、{@code getNBT()} 不再自递归（改成每次现算，不做惰性缓存）。</li>
 * </ul>
 */
public class Pocket {

    // NBT CONSTANTS START //
    private static final String NBT_DIMENSIONAL_POCKET_KEY = "pocketData";
    private static final String NBT_FLOW_STATE_MAP_KEY = "stateMap";
    private static final String NBT_CONNECTOR_MAP_KEY = "connectorMap";

    private static final String NBT_GENERATED_KEY = "generated";
    private static final String NBT_BLOCK_DIMENSION_KEY = "blockDim";
    private static final String NBT_CHUNK_COORDS_KEY = "chunkCoords";
    private static final String NBT_BLOCK_COORDS_KEY = "blockCoords";
    private static final String NBT_SPAWN_COORDS_KEY = "spawnCoords";
    private static final String NBT_SPAWN_COORDS_YAW_KEY = "spawnCoordsYaw";
    private static final String NBT_SPAWN_COORDS_PITCH_KEY = "spawnCoordsPitch";
    private static final String NBT_CREATOR_KEY = "creator";
    // NBT CONSTANTS END //

    /** 默认出生点（房间局部坐标），见报告 01 §1.2。 */
    private static final CoordSet DEFAULT_SPAWN_COORDS = new CoordSet(7, 1, 7);

    /**
     * 存档 JSON 的 {@code connectors} 字段。
     *
     * <p>契约 §4.7：为避免 Gson 泛型 Map key 的坑，键用 {@code String}（{@link Direction#getName()}）。
     * 读的时候同时兼容 1.7.10 存档里的大写枚举名（{@link #sideKeyInNbt} / {@link #connectorAt}）。
     */
    @SerializedName("connectors")
    private Map<String, CoordSet> connectorMap;

    /** 存档 JSON 的 {@code sideStates} 字段，键同 {@code connectors}，值 = 枚举 {@code name()}。 */
    @SerializedName("sideStates")
    private Map<String, PocketSideState> stateMap;

    @SerializedName("generated")
    private boolean isGenerated = false;

    @SerializedName("blockDim")
    private ResourceKey<Level> blockDim;

    @SerializedName("chunkCoords")
    private CoordSet chunkCoords;

    @SerializedName("blockCoords")
    private CoordSet blockCoords;

    @SerializedName("spawnCoords")
    private CoordSet spawnCoords;

    @SerializedName("spawnCoordsYaw")
    private float spawnYaw;

    @SerializedName("spawnCoordsPitch")
    private float spawnPitch;

    @SerializedName("creator")
    private String creator;

    /** 0.10.0 之前的旧字段名，保留以便读老存档（上游注释：renamed to spawnCoords）。 */
    @Deprecated
    private CoordSet spawnSet;

    private Map<String, CoordSet> getConnectorMap() {
        if (connectorMap == null) {
            connectorMap = new LinkedHashMap<>();
        }
        return connectorMap;
    }

    private Map<String, PocketSideState> getSideStateMap() {
        if (stateMap == null) {
            stateMap = new LinkedHashMap<>();
        }
        return stateMap;
    }

    /** 只给 Gson / NBT 反序列化用（上游同名私有构造器）。 */
    private Pocket() {
        // only used for reading from nbt / json
    }

    public Pocket(CoordSet chunkCoords, ResourceKey<Level> blockDim, CoordSet blockCoords) {
        // 报告 01 §0.3 bug 2：上游先 setBlockDim() 触发 getNBT() 惰性建表，此时 chunkCoords 还没赋值，
        // 于是 chunkCoords 键永远写不进 NBT。这里改成最先赋值，并且 getNBT() 每次都现算，双保险。
        this.chunkCoords = chunkCoords == null ? null : chunkCoords.copy();
        setBlockDim(blockDim);
        setBlockCoords(blockCoords);
        setSpawnInPocket(DEFAULT_SPAWN_COORDS.copy(), 0f, 0f);
    }

    /**
     * 登记一个已生成（或即将由 {@code ChunkGeneratorPocket} 生成）的口袋房间。
     *
     * <p>1.20.1 不再直写 {@code ExtendedBlockStorage}（报告 01 §2.5 / 契约 §4.5）：房间外壳由区块生成器铺，
     * 这里只置 {@code isGenerated}、记 creator、登记 6 个默认连接器。
     */
    public void generatePocketRoom(String creatorName) {
        if (isGenerated) {
            ModLog.trace("pocket", "generate_room", "reason=already_generated actual=true expected=false chunk={}", chunkCoords);
            return;
        }

        ServerLevel pocketLevel = pocketLevel("generate_room");
        if (pocketLevel == null) {
            return;
        }
        if (chunkCoords == null) {
            ModLog.deny("pocket", "generate_room", "reason=chunk_coords_missing actual=null expected=int[3] action=aborted");
            return;
        }

        // root = chunkCoords << 4（报告 01 §1.1 / §3.3），生成后的校验点 = root + (1,0,1)
        BlockPos root = new BlockPos(chunkCoords.x * 16, chunkCoords.y * 16, chunkCoords.z * 16);
        Block wallBlock = ModBlocks.DIMENSIONAL_POCKET_WALL.get();
        boolean shellPresent = wallBlock != null && pocketLevel.getBlockState(root.offset(1, 0, 1)).is(wallBlock);

        isGenerated = true;
        if (!shellPresent) {
            // 上游在这里是「自己写外壳所以一定存在」。1.20.1 外壳归区块生成器管，
            // 传送发生在目标 chunk 生成之前，所以这里可能还不存在——只留痕，不阻塞登记。
            ModLog.deny("pocket", "generate_room",
                    "reason=shell_not_generated_yet actual=absent expected={} root={} action=registered_anyway",
                    wallBlock == null ? "dimensional_pocket_wall(unregistered)" : wallBlock, root);
        }

        if (creatorName != null && !creatorName.isEmpty()) {
            creator = creatorName;
        }

        generateDefaultConnectors();

        ModLog.step("pocket", "generate_room", "chunk={} generated={} creator={} connectors={}",
                chunkCoords, isGenerated, creator, getConnectorMap().size());
    }

    public void resetFlowStates() {
        getSideStateMap().clear();
        ModLog.step("pocket", "reset_flow_states", "chunk={} states=0", chunkCoords);
    }

    public PocketSideState getFlowState(Direction side) {
        if (side == null) {
            return PocketSideState.NONE;
        }
        PocketSideState state = getSideStateMap().get(side.getName());
        if (state == null) {
            // 兼容 1.7.10 存档里的大写枚举名键
            state = getSideStateMap().get(side.name());
        }
        return state == null ? PocketSideState.NONE : state;
    }

    public void setFlowState(Direction side, PocketSideState flowState) {
        if (side == null || flowState == null) {
            ModLog.deny("pocket", "set_flow_state", "reason=null_argument actual={} expected=side+state chunk={}",
                    side == null ? "side=null" : "state=null", chunkCoords);
            return;
        }
        if (pocketLevel("set_flow_state") == null) {
            return;
        }

        if (flowState != PocketSideState.NONE) {
            getSideStateMap().put(side.getName(), flowState);
        } else {
            getSideStateMap().remove(side.getName());
            getSideStateMap().remove(side.name());
        }

        // 上游的两个副作用必须保留（报告 02 §18）：通知客户端重画 overlay + 通知邻居重新查询能力
        markConnectorForUpdate(side);
        markSourceBlockForUpdate();

        ModLog.state("pocket", "set_flow_state", "side={} state={} chunk={}", side, flowState, chunkCoords);
    }

    public void markConnectorForUpdate(Direction side) {
        if (side == null) {
            ModLog.deny("pocket", "mark_connector", "reason=null_side actual=null expected=one_of_6_directions chunk={}", chunkCoords);
            return;
        }
        ServerLevel pocketLevel = pocketLevel("mark_connector");
        if (pocketLevel == null) {
            return;
        }
        // 注意：这里刻意不走 getConnectorCoords()，否则会触发 generateDefaultConnectors() 形成回环
        CoordSet connectorCoords = connectorAt(side);
        if (connectorCoords == null) {
            ModLog.deny("pocket", "mark_connector", "reason=no_connector_for_side side={} actual=null expected=coords chunk={}",
                    side, chunkCoords);
            return;
        }

        BlockPos pos = connectorCoords.toBlockPos();
        if (!pocketLevel.hasChunkAt(pos)) {
            ModLog.deny("pocket", "mark_connector", "reason=chunk_not_loaded pos={} actual=unloaded expected=loaded", pos);
            return;
        }

        BlockState state = pocketLevel.getBlockState(pos);
        pocketLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        pocketLevel.updateNeighborsAt(pos, state.getBlock());
        ModLog.trace("pocket", "mark_connector", "side={} pos={} {}", side, pos, ModLog.st(state));
    }

    public void markSourceBlockForUpdate() {
        if (!isSourceBlockPlaced()) {
            ModLog.trace("pocket", "mark_source_block", "reason=source_block_not_placed actual=absent expected=dimensional_pocket blockDim={} blockCoords={}",
                    blockDim, blockCoords);
            return;
        }

        ServerLevel blockLevel = blockLevel("mark_source_block");
        if (blockLevel == null || blockCoords == null) {
            return;
        }

        BlockPos pos = blockCoords.toBlockPos();
        if (!blockLevel.hasChunkAt(pos)) {
            ModLog.deny("pocket", "mark_source_block", "reason=chunk_not_loaded pos={} actual=unloaded expected=loaded", pos);
            return;
        }

        BlockState state = blockLevel.getBlockState(pos);
        blockLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        blockLevel.updateNeighborsAt(pos, state.getBlock());
        ModLog.trace("pocket", "mark_source_block", "pos={} {}", pos, ModLog.st(state));
    }

    /**
     * 登记 6 个默认连接器（房间局部坐标，报告 01 §1.2）：DOWN (7,0,7) / UP (7,15,7) / NORTH (7,7,0)
     * / SOUTH (7,7,15) / WEST (0,7,7) / EAST (15,7,7)。
     *
     * <p>上游这里走 {@code setConnectorForSide()}，而 1.20.1 的外壳由区块生成器铺、传送时可能还没生成，
     * 严格检查会让 6 个默认连接器一个都登记不上。因此默认连接器只要求「坐标登记」，
     * 方块状态在目标格确实是外壳时才写；玩家驱动的 {@link #setConnectorForSide} 保持上游的严格校验。
     */
    private void generateDefaultConnectors() {
        ServerLevel pocketLevel = pocketLevel("generate_default_connectors");
        if (pocketLevel == null) {
            return;
        }
        if (chunkCoords == null) {
            ModLog.deny("pocket", "generate_default_connectors", "reason=chunk_coords_missing actual=null expected=int[3] action=aborted");
            return;
        }

        CoordSet root = chunkCoords.toBlockCoords();
        for (Direction side : Utils.VALID_DIRECTIONS) {
            CoordSet connectorCoords;
            switch (side) {
                case DOWN:
                    connectorCoords = root.copy().addCoordSet(new CoordSet(7, 0, 7));
                    break;
                case UP:
                    connectorCoords = root.copy().addCoordSet(new CoordSet(7, 15, 7));
                    break;
                case NORTH:
                    connectorCoords = root.copy().addCoordSet(new CoordSet(7, 7, 0));
                    break;
                case SOUTH:
                    connectorCoords = root.copy().addCoordSet(new CoordSet(7, 7, 15));
                    break;
                case WEST:
                    connectorCoords = root.copy().addCoordSet(new CoordSet(0, 7, 7));
                    break;
                case EAST:
                    connectorCoords = root.copy().addCoordSet(new CoordSet(15, 7, 7));
                    break;
                default:
                    // 1.20.1 的 Direction 只有 6 个值，这里不可达（上游的 ForgeDirection.VALID_DIRECTIONS 可能含 UNKNOWN）
                    continue;
            }
            registerConnector(pocketLevel, side, connectorCoords);
        }

        markSourceBlockForUpdate();
    }

    public CoordSet getConnectorCoords(Direction side) {
        if (side == null) {
            ModLog.deny("pocket", "get_connector_coords", "reason=null_side actual=null expected=one_of_6_directions chunk={}", chunkCoords);
            return null;
        }
        Map<String, CoordSet> cMap = getConnectorMap();
        if (cMap.size() < 6) {
            // 上游语义：连接器还没建全就补建
            generateDefaultConnectors();
        }

        CoordSet connectorCoords = connectorAt(side);
        return connectorCoords == null ? null : connectorCoords.copy();
    }

    /**
     * 尝试把某个面的连接器设到新坐标。返回是否成功。
     *
     * <p>玩家路径（下界水晶移动连接器）：目标格必须是 {@code dimensional_pocket_wall}，与上游一致。
     */
    public boolean setConnectorForSide(Direction side, CoordSet connectorCoords) {
        if (side == null || connectorCoords == null) {
            ModLog.deny("pocket", "set_connector", "reason=null_argument actual={} expected=side+coords chunk={}",
                    side == null ? "side=null" : "coords=null", chunkCoords);
            return false;
        }
        ServerLevel pocketLevel = pocketLevel("set_connector");
        if (pocketLevel == null) {
            return false;
        }

        BlockPos pos = connectorCoords.toBlockPos();
        BlockState state = pocketLevel.getBlockState(pos);
        Block wallBlock = ModBlocks.DIMENSIONAL_POCKET_WALL.get();
        if (wallBlock == null || !state.is(wallBlock)) {
            ModLog.deny("pocket", "set_connector",
                    "reason=target_is_not_pocket_wall side={} pos={} actual={} expected={} chunk={}",
                    side, pos, ModLog.st(state),
                    wallBlock == null ? "dimensional_pocket_wall(unregistered)" : ModLog.st(wallBlock.defaultBlockState()),
                    chunkCoords);
            return false;
        }

        if (getConnectorMap().size() == 6) {
            // 上游注释：这个检查是为了防止首次创建连接器时无限递归
            CoordSet oldCoords = connectorAt(side);
            if (oldCoords != null) {
                BlockEntity te = pocketLevel.getBlockEntity(oldCoords.toBlockPos());
                if (te instanceof TileDimensionalPocketWallConnector connector) {
                    connector.invalidateConnector();
                } else {
                    ModLog.trace("pocket", "set_connector", "reason=old_connector_has_no_connector_te side={} old={} actual={}",
                            side, oldCoords, te == null ? "no_block_entity" : te.getClass().getSimpleName());
                }
            }
        }

        registerConnector(pocketLevel, side, connectorCoords);
        ModLog.step("pocket", "set_connector", "side={} pos={} chunk={}", side, pos, chunkCoords);
        return true;
    }

    /**
     * 把连接器坐标登记进 {@code connectorMap}，并在目标格确实是外壳方块时写上
     * {@code BlockDimensionalPocketWall.CONNECTOR} 方块状态（取代上游的 metadata 1，契约 §4.4）。
     */
    private void registerConnector(ServerLevel pocketLevel, Direction side, CoordSet connectorCoords) {
        getConnectorMap().put(side.getName(), connectorCoords.copy());

        BlockPos pos = connectorCoords.toBlockPos();
        BlockState state = pocketLevel.getBlockState(pos);
        Block wallBlock = ModBlocks.DIMENSIONAL_POCKET_WALL.get();
        if (wallBlock == null) {
            ModLog.deny("pocket", "register_connector",
                    "reason=wall_block_not_registered actual=null expected=dimensional_pocket_wall side={} pos={}", side, pos);
            return;
        }
        if (!state.is(wallBlock)) {
            ModLog.deny("pocket", "register_connector",
                    "reason=target_is_not_pocket_wall side={} pos={} actual={} expected={} action=coords_registered_only",
                    side, pos, ModLog.st(state), ModLog.st(wallBlock.defaultBlockState()));
            return;
        }

        BlockState connectorState = state.setValue(BlockDimensionalPocketWall.CONNECTOR, Boolean.TRUE);
        if (connectorState != state) {
            pocketLevel.setBlock(pos, connectorState, Block.UPDATE_ALL);
        }
        pocketLevel.sendBlockUpdated(pos, connectorState, connectorState, Block.UPDATE_ALL);
        pocketLevel.updateNeighborsAt(pos, connectorState.getBlock());
    }

    /**
     * 玩家进入口袋：落点 = {@code chunkCoords << 4 + spawnCoords}（报告 01 §2.4）。
     */
    public void teleportTo(ServerPlayer player) {
        if (player == null || player.level().isClientSide()) {
            ModLog.deny("pocket", "teleport_to", "reason=not_server_player actual={} expected=server_player chunk={}",
                    player == null ? "player=null" : "side=client", chunkCoords);
            return;
        }

        ServerLevel blockLevel = blockLevel("teleport_to");
        if (blockLevel == null || blockCoords == null) {
            ModLog.deny("pocket", "teleport_to", "reason=source_level_or_coords_missing actual={} expected=loaded_level+coords blockDim={} chunk={}",
                    blockLevel == null ? "level=null" : "coords=null", blockDim, chunkCoords);
            return;
        }

        TeleportDirection teleportSide = TeleportDirection.getValidTeleportLocation(blockLevel, blockCoords.x, blockCoords.y, blockCoords.z);
        if (teleportSide == TeleportDirection.UNKNOWN) {
            player.displayClientMessage(Component.translatable("info.exit.blocked").withStyle(ChatFormatting.ITALIC), false);
            ModLog.deny("pocket", "teleport_to", "reason=exit_blocked actual=UNKNOWN expected=valid_direction blockCoords={} chunk={}",
                    blockCoords, chunkCoords);
            return;
        }

        CoordSet spawn = getSpawnCoords();
        if (chunkCoords == null || spawn == null) {
            ModLog.deny("pocket", "teleport_to", "reason=target_coords_missing actual={} expected=chunk_coords+spawn_coords",
                    chunkCoords == null ? "chunkCoords=null" : "spawnCoords=null");
            return;
        }

        ResourceKey<Level> fromDim = player.level().dimension();
        CoordSet tempSet = chunkCoords.copy().asBlockCoords().addCoordSet(spawn);

        PocketTeleporter teleporter = new PocketTeleporter(fromDim, tempSet, spawnYaw, spawnPitch);

        generatePocketRoom(player.getGameProfile().getName());

        if (!Reference.POCKET_LEVEL.equals(fromDim)) {
            PocketTeleporter.transfer(player, Reference.POCKET_LEVEL, teleporter);
        } else {
            // 上游同维度分支 placeInPortal(player, 0, 0, 0, 0)：同维度不能走 changeDimension（报告 01 §14.1）
            player.connection.teleport(tempSet.x + 0.5D, tempSet.y + 1.0D, tempSet.z + 0.5D, spawnYaw, spawnPitch);
            player.resetFallDistance();
        }

        ModLog.step("pocket", "teleport_to", "player={} fromDim={} target={} side={}",
                player.getGameProfile().getName(), fromDim.location(), tempSet, teleportSide);
    }

    /**
     * 玩家离开口袋：落点 = {@code blockCoords + side - (0,1,0)}，即站在 DP 方块正上方（报告 01 §2.4）。
     */
    public void teleportFrom(ServerPlayer player) {
        if (player == null || player.level().isClientSide()) {
            ModLog.deny("pocket", "teleport_from", "reason=not_server_player actual={} expected=server_player chunk={}",
                    player == null ? "player=null" : "side=client", chunkCoords);
            return;
        }

        if (!isSourceBlockPlaced()) {
            player.displayClientMessage(Component.translatable("info.trapped.1").withStyle(ChatFormatting.ITALIC), false);
            ModLog.deny("pocket", "teleport_from", "reason=source_block_not_placed actual=absent expected=dimensional_pocket blockDim={} blockCoords={}",
                    blockDim, blockCoords);
            return;
        }

        ServerLevel blockLevel = blockLevel("teleport_from");
        if (blockLevel == null || blockCoords == null) {
            ModLog.deny("pocket", "teleport_from", "reason=source_level_or_coords_missing actual={} expected=loaded_level+coords blockDim={}",
                    blockLevel == null ? "level=null" : "coords=null", blockDim);
            return;
        }

        TeleportDirection teleportSide = TeleportDirection.getValidTeleportLocation(blockLevel, blockCoords.x, blockCoords.y, blockCoords.z);
        if (teleportSide == TeleportDirection.UNKNOWN) {
            player.displayClientMessage(Component.translatable("info.trapped.2").withStyle(ChatFormatting.ITALIC), false);
            ModLog.deny("pocket", "teleport_from", "reason=exit_blocked actual=UNKNOWN expected=valid_direction blockCoords={}", blockCoords);
            return;
        }

        CoordSet tempBlockSet = blockCoords.copy().addCoordSet(teleportSide.toCoordSet());
        tempBlockSet.y += -1;

        PocketTeleporter teleporter = new PocketTeleporter(Reference.POCKET_LEVEL, tempBlockSet,
                player.getYRot(), player.getXRot());

        if (!Reference.POCKET_LEVEL.equals(blockDim)) {
            PocketTeleporter.transfer(player, blockDim, teleporter);
        } else {
            player.connection.teleport(tempBlockSet.x + 0.5D, tempBlockSet.y + 1.0D, tempBlockSet.z + 0.5D,
                    player.getYRot(), player.getXRot());
            player.resetFallDistance();
        }

        ModLog.step("pocket", "teleport_from", "player={} toDim={} target={} side={}",
                player.getGameProfile().getName(), blockDim, tempBlockSet, teleportSide);
    }

    public boolean isSourceBlockPlaced() {
        BlockState state = getSourceBlockState();
        if (state == null) {
            return false;
        }
        Block pocketBlock = ModBlocks.DIMENSIONAL_POCKET.get();
        if (pocketBlock == null) {
            ModLog.deny("pocket", "is_source_block_placed", "reason=pocket_block_not_registered actual=null expected=dimensional_pocket");
            return false;
        }
        return state.is(pocketBlock);
    }

    /** 1.20.1 取代上游 {@code getBlock()}（报告 01 §2.5）。维度未加载时返回 {@code null}。 */
    public BlockState getSourceBlockState() {
        ServerLevel blockLevel = blockLevel("get_source_block_state");
        if (blockLevel == null || blockCoords == null) {
            return null;
        }
        return blockLevel.getBlockState(blockCoords.toBlockPos());
    }

    public ResourceKey<Level> getBlockDim() {
        return blockDim;
    }

    public void setBlockDim(ResourceKey<Level> blockDim) {
        if (blockDim == null) {
            ModLog.deny("pocket", "set_block_dim", "reason=null_dimension actual=null expected=dimension_key action=ignored chunk={}", chunkCoords);
            return;
        }
        this.blockDim = blockDim;
    }

    public CoordSet getBlockCoords() {
        if (blockCoords == null) {
            ModLog.deny("pocket", "get_block_coords", "reason=block_coords_missing actual=null expected=int[3] chunk={}", chunkCoords);
            return null;
        }
        return blockCoords.copy();
    }

    public void setBlockCoords(CoordSet blockCoords) {
        if (blockCoords == null) {
            ModLog.deny("pocket", "set_block_coords", "reason=null_coords actual=null expected=int[3] action=ignored chunk={}", chunkCoords);
            return;
        }
        this.blockCoords = blockCoords.copy();
    }

    /** 兼容 0.10.0 之前的 {@code spawnCoords} 旧字段名（上游 {@code getSpawnCoords()}）。 */
    public CoordSet getSpawnCoords() {
        if (spawnCoords == null && spawnSet != null) {
            spawnCoords = spawnSet;
            spawnSet = null;
        }
        return spawnCoords;
    }

    public void setSpawnInPocket(CoordSet spawnCoords, float spawnYaw, float spawnPitch) {
        if (spawnCoords == null) {
            ModLog.deny("pocket", "set_spawn_in_pocket", "reason=null_coords actual=null expected=int[3] action=ignored chunk={}", chunkCoords);
            return;
        }
        this.spawnCoords = spawnCoords.copy();
        this.spawnYaw = spawnYaw;
        this.spawnPitch = spawnPitch;
    }

    public float getSpawnYaw() {
        return spawnYaw;
    }

    public float getSpawnPitch() {
        return spawnPitch;
    }

    public CoordSet getChunkCoords() {
        if (chunkCoords == null) {
            ModLog.deny("pocket", "get_chunk_coords", "reason=chunk_coords_missing actual=null expected=int[3] blockDim={} blockCoords={}",
                    blockDim, blockCoords);
            return null;
        }
        return chunkCoords.copy();
    }

    public boolean isGenerated() {
        return isGenerated;
    }

    public String getCreator() {
        return creator;
    }

    /**
     * 房间局部坐标 -&gt; 连接器所在面。
     *
     * <p>判定顺序**逐字**照抄上游（报告 01 §1.3）：x==0-&gt;WEST，x==15-&gt;EAST，y==0-&gt;DOWN，
     * y==15-&gt;UP，z==0-&gt;NORTH，z==15-&gt;SOUTH。1.20.1 的 {@link Direction} 没有 {@code UNKNOWN}，
     * 都不命中时返回 {@code null}（调用方需判空）。
     */
    public static Direction getSideForConnector(CoordSet coordSet) {
        if (coordSet == null) {
            ModLog.deny("pocket", "side_for_connector", "reason=null_coords actual=null expected=int[3]");
            return null;
        }

        if (coordSet.x == 0) {
            return Direction.WEST;
        }
        if (coordSet.x == 15) {
            return Direction.EAST;
        }
        if (coordSet.y == 0) {
            return Direction.DOWN;
        }
        if (coordSet.y == 15) {
            return Direction.UP;
        }
        if (coordSet.z == 0) {
            return Direction.NORTH;
        }
        if (coordSet.z == 15) {
            return Direction.SOUTH;
        }

        ModLog.trace("pocket", "side_for_connector", "reason=not_on_room_shell actual={} expected=one_axis_at_0_or_15", coordSet);
        return null;
    }

    public void writeToNbt(CompoundTag tag) {
        tag.put(NBT_DIMENSIONAL_POCKET_KEY, getNBT());
    }

    public static Pocket readFromNbt(CompoundTag tag) {
        if (tag == null) {
            ModLog.deny("pocket", "read_nbt", "reason=null_tag actual=null expected=compound_tag");
            return null;
        }

        // this is for staying compatible with older saves prior to version 0.10.0
        CoordSet oldVersionChunkCoords = CoordSet.readFromNBT(tag);
        if (oldVersionChunkCoords != null) {
            Pocket legacyPocket = PocketRegistry.getPocket(oldVersionChunkCoords);
            if (legacyPocket == null) {
                ModLog.warn("pocket", "read_nbt", "reason=legacy_coords_not_registered actual={} expected=registered_pocket key=coordSet",
                        oldVersionChunkCoords);
            }
            return legacyPocket;
        }

        CompoundTag pocketTag = tag.getCompound(NBT_DIMENSIONAL_POCKET_KEY);

        Pocket pocket = new Pocket();

        pocket.chunkCoords = CoordSet.readFromNBT(pocketTag, NBT_CHUNK_COORDS_KEY);
        pocket.blockDim = readBlockDim(pocketTag);
        pocket.blockCoords = CoordSet.readFromNBT(pocketTag, NBT_BLOCK_COORDS_KEY);

        pocket.isGenerated = pocketTag.getBoolean(NBT_GENERATED_KEY);
        pocket.spawnCoords = CoordSet.readFromNBT(pocketTag, NBT_SPAWN_COORDS_KEY);
        pocket.spawnYaw = pocketTag.getFloat(NBT_SPAWN_COORDS_YAW_KEY);
        pocket.spawnPitch = pocketTag.getFloat(NBT_SPAWN_COORDS_PITCH_KEY);

        pocket.creator = pocketTag.getString(NBT_CREATOR_KEY);
        if (pocket.creator.isEmpty()) {
            pocket.creator = null;
        }

        // basic values must be read out first, so that they wont get lost on NBT generation
        // while putting in the states (上游注释)
        CompoundTag stateMapTag = pocketTag.getCompound(NBT_FLOW_STATE_MAP_KEY);
        CompoundTag connectorMapTag = pocketTag.getCompound(NBT_CONNECTOR_MAP_KEY);
        for (Direction side : Utils.VALID_DIRECTIONS) {
            String stateKey = sideKeyInNbt(stateMapTag, side);
            if (stateKey != null) {
                pocket.getSideStateMap().put(side.getName(), PocketSideState.byName(stateMapTag.getString(stateKey)));
            }

            String connectorKey = sideKeyInNbt(connectorMapTag, side);
            if (connectorKey != null) {
                CoordSet connectorCoords = CoordSet.readFromNBT(connectorMapTag, connectorKey);
                if (connectorCoords == null) {
                    ModLog.deny("pocket", "read_nbt",
                            "reason=connector_coords_malformed side={} key={} actual={} expected=int[3]",
                            side, connectorKey, ModLog.clip(String.valueOf(connectorMapTag.get(connectorKey)), 60));
                } else {
                    pocket.getConnectorMap().put(side.getName(), connectorCoords);
                }
            }
        }

        if (pocket.chunkCoords == null) {
            // 上游 bug 2 写出来的老 NBT 就是这种情况，只告警不抛（客户端渲染数据可以退化）
            ModLog.warn("pocket", "read_nbt",
                    "reason=chunk_coords_missing actual=null expected=int[3] blockDim={} blockCoords={}",
                    pocket.blockDim, pocket.blockCoords);
        }

        return pocket;
    }

    /**
     * 每次调用都从字段现算一份 NBT。
     *
     * <p>上游是惰性缓存（{@code transient NBTTagCompound}），这直接导致报告 01 §0.3 的两个 bug：
     * ① 构造函数里 {@code setBlockDim()} 提前触发建表，{@code chunkCoords} 还没赋值就永久丢字段；
     * ② 建表过程中调用 {@code getNBT().setFloat(...)} 造成自递归。
     * 现算的语义对所有消费方等价（没有任何调用方读回本对象自己的 NBT），同时两个 bug 从根上消失。
     */
    private CompoundTag getNBT() {
        CompoundTag nbtTagCompound = new CompoundTag();

        CompoundTag stateMapTag = new CompoundTag();
        for (Map.Entry<String, PocketSideState> entry : getSideStateMap().entrySet()) {
            stateMapTag.putString(entry.getKey(), entry.getValue().name());
        }
        nbtTagCompound.put(NBT_FLOW_STATE_MAP_KEY, stateMapTag);

        CompoundTag connectorMapTag = new CompoundTag();
        for (Map.Entry<String, CoordSet> entry : getConnectorMap().entrySet()) {
            entry.getValue().writeToNBT(connectorMapTag, entry.getKey());
        }
        nbtTagCompound.put(NBT_CONNECTOR_MAP_KEY, connectorMapTag);

        nbtTagCompound.putBoolean(NBT_GENERATED_KEY, isGenerated);

        if (blockDim != null) {
            nbtTagCompound.putString(NBT_BLOCK_DIMENSION_KEY, blockDim.location().toString());
        }

        if (chunkCoords != null) {
            // 报告 01 §0.3 bug 2：这个键必须写进去
            chunkCoords.writeToNBT(nbtTagCompound, NBT_CHUNK_COORDS_KEY);
        }

        if (blockCoords != null) {
            blockCoords.writeToNBT(nbtTagCompound, NBT_BLOCK_COORDS_KEY);
        }

        CoordSet spawn = getSpawnCoords();
        if (spawn != null) {
            spawn.writeToNBT(nbtTagCompound, NBT_SPAWN_COORDS_KEY);
        }

        nbtTagCompound.putFloat(NBT_SPAWN_COORDS_YAW_KEY, spawnYaw);
        nbtTagCompound.putFloat(NBT_SPAWN_COORDS_PITCH_KEY, spawnPitch);

        if (creator != null && !creator.isEmpty()) {
            // 报告 01 §0.3 bug 1：上游把 creator 写进了 NBT_GENERATED_KEY，这里写 NBT_CREATOR_KEY
            nbtTagCompound.putString(NBT_CREATOR_KEY, creator);
        }

        return nbtTagCompound;
    }

    private static ResourceKey<Level> readBlockDim(CompoundTag pocketTag) {
        if (pocketTag.contains(NBT_BLOCK_DIMENSION_KEY, Tag.TAG_INT)) {
            return dimensionKeyFromLegacyId(pocketTag.getInt(NBT_BLOCK_DIMENSION_KEY));
        }

        String dimensionKey = pocketTag.getString(NBT_BLOCK_DIMENSION_KEY);
        ResourceLocation location = dimensionKey.isEmpty() ? null : ResourceLocation.tryParse(dimensionKey);
        if (location == null) {
            ModLog.warn("pocket", "read_nbt", "reason=block_dim_unreadable actual={} expected=namespace:path", dimensionKey);
            return null;
        }
        return ResourceKey.create(Registries.DIMENSION, location);
    }

    /**
     * 1.7.10 存档 / 老 NBT 里的数字维度 id -&gt; {@link ResourceKey}（33 是上游的口袋维度 id）。
     */
    static ResourceKey<Level> dimensionKeyFromLegacyId(int dimensionId) {
        switch (dimensionId) {
            case 0:
                return Level.OVERWORLD;
            case -1:
                return Level.NETHER;
            case 1:
                return Level.END;
            case 33:
                return Reference.POCKET_LEVEL;
            default:
                ModLog.warn("pocket", "legacy_dimension_id",
                        "reason=unknown_numeric_dimension_id actual={} expected=0|-1|1|33 fallback=minecraft:overworld", dimensionId);
                return Level.OVERWORLD;
        }
    }

    /**
     * 连接器 / 面状态表里某个方向的键。
     *
     * <p>写入统一用 {@link Direction#getName()}（契约 §4.7）；读取同时接受 1.7.10 存档的大写枚举名。
     *
     * @return 命中的键，都没有则 {@code null}
     */
    private static String sideKeyInNbt(CompoundTag tag, Direction side) {
        if (tag.contains(side.getName())) {
            return side.getName();
        }
        if (tag.contains(side.name())) {
            return side.name();
        }
        return null;
    }

    /** 不触发 {@code generateDefaultConnectors()} 的查表（避免回环）。 */
    private CoordSet connectorAt(Direction side) {
        CoordSet connectorCoords = getConnectorMap().get(side.getName());
        if (connectorCoords == null) {
            connectorCoords = getConnectorMap().get(side.name());
        }
        return connectorCoords;
    }

    /**
     * 1.20.1 取代上游 {@code Utils.enforceServer()}（报告 01 §12.3）：口袋维度只存在于服务端。
     *
     * @return 口袋维度；不可用时返回 {@code null}（已留痕）
     */
    private ServerLevel pocketLevel(String action) {
        ServerLevel level = PocketRegistry.getPocketLevel();
        if (level == null) {
            ModLog.denyRepeated("pocket." + action + ".no_pocket_level", 200, "pocket", action,
                    "reason=no_pocket_level actual=null expected=server_level chunk={}", chunkCoords);
        } else {
            ModLog.expect(!level.isClientSide(), "pocket", action,
                    "reason=client_side_level actual=client expected=server chunk={}", chunkCoords);
        }
        return level;
    }

    /**
     * 入口方块所在维度。
     *
     * <p>上游 {@code getBlockWorld()} 的 1.20.1 等价物（报告 01 §2.5），**可能返回 {@code null}**：
     * 维度未加载（Mystcraft / GalactiCraft 之类的世界）时调用方必须判空。
     */
    private ServerLevel blockLevel(String action) {
        if (blockDim == null) {
            ModLog.deny("pocket", action, "reason=block_dim_missing actual=null expected=dimension_key chunk={}", chunkCoords);
            return null;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            ModLog.deny("pocket", action, "reason=no_server actual=null expected=server_instance blockDim={}", blockDim.location());
            return null;
        }
        ServerLevel level = server.getLevel(blockDim);
        if (level == null) {
            ModLog.warn("pocket", action,
                    "reason=dimension_not_loaded actual=null expected=loaded_level blockDim={} (Mystcraft or GalactiCraft world?)",
                    blockDim.location());
        }
        return level;
    }
}
