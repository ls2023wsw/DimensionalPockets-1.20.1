package net.gtn.dimensionalpocket.common.block;

import net.gtn.dimensionalpocket.common.ModBlockEntities;
import net.gtn.dimensionalpocket.common.ModItems;
import net.gtn.dimensionalpocket.common.core.DPConfig;
import net.gtn.dimensionalpocket.common.core.pocket.Pocket;
import net.gtn.dimensionalpocket.common.core.pocket.PocketRegistry;
import net.gtn.dimensionalpocket.common.core.utils.CoordSet;
import net.gtn.dimensionalpocket.common.core.utils.Utils;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.gtn.dimensionalpocket.common.tileentity.TileDimensionalPocketWallConnector;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 口袋房间的外壳方块。1.7.10 对应物 {@code common/block/BlockDimensionalPocketWall.java}。
 *
 * <p>默认**不可破坏、不可被爆炸破坏、不进创造栏**；{@code meta == 1} 的「连接器」格承载
 * {@link TileDimensionalPocketWallConnector}；空手潜行右键任何一格外壳都能从口袋里出来。
 *
 * <p>1.20.1 的 {@code BlockEntityType} 不看 metadata，所以上游的 {@code CONNECTOR_META = 1}
 * 换成方块状态 {@link #CONNECTOR}：{@code newBlockEntity} 只在 {@code CONNECTOR=true} 时建 TE，
 * 降级（{@code invalidateConnector}）就是把它设回 false。
 */
public class BlockDimensionalPocketWall extends Block implements EntityBlock {

    /** 1.7.10: {@code BlockDimensionalPocketWall.CONNECTOR_META == 1} 的 1.20.1 等价物。 */
    public static final BooleanProperty CONNECTOR = BooleanProperty.create("connector");

    /** 1.7.10: {@code setResistance(6000000.0F)}。 */
    private static final float WALL_EXPLOSION_RESISTANCE = 6000000.0F;

    public BlockDimensionalPocketWall() {
        super(Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.ANVIL)
                .requiresCorrectToolForDrops()
                // setBlockUnbreakable() 的语义 = strength(-1.0F, ...)
                .strength(-1.0F, WALL_EXPLOSION_RESISTANCE)
                .lightLevel(state -> 1)
                .pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(CONNECTOR, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONNECTOR);
    }

    // ------------------------------------------------------------------
    // 方块实体
    // ------------------------------------------------------------------

    /** 1.7.10: {@code hasTileEntity(meta) → meta == CONNECTOR_META}。 */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(CONNECTOR) ? new TileDimensionalPocketWallConnector(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        if (type != ModBlockEntities.WALL_CONNECTOR.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof TileDimensionalPocketWallConnector connector) {
                connector.tick();
            }
        };
    }

    /** 1.7.10: {@code getRenderType() == -1}（不走方块模型，只有 TESR）。 */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    // ------------------------------------------------------------------
    // 放置 / 中键取方块：只有口袋维度 + 调试开关才允许
    // ------------------------------------------------------------------

    /** 1.7.10: {@code canPlaceBlockAt} = 口袋维度 && {@code CAN_BREAK_POCKET_WALL_IN_CREATIVE}。 */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        if (level.dimension() == Reference.POCKET_LEVEL && DPConfig.canBreakPocketWallInCreative()) {
            return defaultBlockState();
        }
        ModLog.deny("block", "wall_place",
                "reason=not_allowed actual=dim:{} canBreak:{} expected=dim:{} canBreak:true {}",
                level.dimension().location(), DPConfig.canBreakPocketWallInCreative(),
                Reference.POCKET_LEVEL_ID, ModLog.pos(context.getClickedPos()));
        return null;
    }

    /** 1.7.10: {@code getPickBlock} 在未开调试开关时返回 null（1.20.1 → {@code ItemStack.EMPTY}）。 */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        if (!DPConfig.canBreakPocketWallInCreative()) {
            return ItemStack.EMPTY;
        }
        return super.getCloneItemStack(state, target, level, pos, player);
    }

    // ------------------------------------------------------------------
    // 右键（1.7.10: onBlockActivated）
    // ------------------------------------------------------------------

    /**
     * 上游全分支（对 metadata 0 / 1 都生效，别给非连接器格加限制）：
     * <ul>
     *   <li>手上有「口袋扳手」（铁砧改名的木棍）→ 借用 {@code ItemNetherCrystal} 的逻辑，消费右键；</li>
     *   <li>手上有别的物品 → 不处理（PASS）；</li>
     *   <li>空手但不潜行 → 不处理（PASS）；</li>
     *   <li>空手潜行 + 身在口袋维度 → {@code pocket.teleportFrom(player)} 出去。</li>
     * </ul>
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        ItemStack equipped = player.getMainHandItem();
        if (!equipped.isEmpty()) {
            if (Utils.isItemPocketWrench(equipped)) {
                ModItems.NETHER_CRYSTAL.get().onItemUseFirst(equipped, new UseOnContext(player, hand, hit));
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            ModLog.trace("block", "wall_use", "reason=holding_item {} {} {}",
                    ModLog.player(player), ModLog.pos(pos), ModLog.item(equipped));
            return InteractionResult.PASS;
        }

        if (!player.isShiftKeyDown()) {
            ModLog.trace("block", "wall_use", "reason=not_sneaking {} {}", ModLog.player(player), ModLog.pos(pos));
            return InteractionResult.PASS;
        }

        if (level.dimension() != Reference.POCKET_LEVEL) {
            ModLog.trace("block", "wall_use", "reason=not_in_pocket_dim {} actual={} expected={}",
                    ModLog.pos(pos), level.dimension().location(), Reference.POCKET_LEVEL_ID);
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            ModLog.deny("block", "wall_use",
                    "reason=not_a_server_player {} actual={} expected=ServerPlayer",
                    ModLog.pos(pos), player.getClass().getName());
            return InteractionResult.PASS;
        }
        Pocket pocket = PocketRegistry.getPocket(new CoordSet(pos).toChunkCoords());
        if (pocket == null) {
            ModLog.deny("block", "wall_use",
                    "reason=no_pocket_for_chunk {} actual=null expected=Pocket", ModLog.pos(pos));
            return InteractionResult.PASS;
        }

        // 上游 hack：口袋里往外传送时强制取消潜行，避免传送后立刻二次触发。
        if (!pocket.getBlockDim().equals(Reference.POCKET_LEVEL)) {
            player.setShiftKeyDown(false);
        }

        pocket.teleportFrom(serverPlayer);
        ModLog.step("block", "wall_use", "teleport_out {} {} pocket={}",
                ModLog.player(player), ModLog.pos(pos), pocket.getChunkCoords());
        return InteractionResult.SUCCESS;
    }
    /**
     * 1.7.10: {@code BlockAbstract.onNeighbourChange} → 连接器 TE 的
     * {@code onNeighbourBlockChanged} / {@code onNeighbourTileChanged} → {@code markSourceBlockForUpdate()}。
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide()) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof TileDimensionalPocketWallConnector connector) {
            connector.onNeighbourChanged();
        }
    }
}
