package net.gtn.dimensionalpocket.common.block;

import java.util.List;

import net.gtn.dimensionalpocket.common.core.DPConfig;
import net.gtn.dimensionalpocket.common.tileentity.TileDimensionalPocket;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 口袋方块本体。1.7.10 对应物 {@code common/block/BlockDimensionalPocket.java}
 * （{@code BlockAbstract + IDismantleable + ITileProvider}）。
 *
 * <p>方块属性照 1.7.10 原样：{@code Material.anvil} + 硬度 4F + 自发光 1 + 抗爆
 * {@code DIMENSIONAL_POCKET_RESISTANCE}（默认 15.0F）+ 活塞推不动。
 * {@code getRenderType() == -1} → {@link RenderShape#INVISIBLE}。
 *
 * <p>{@code IDismantleable}（CoFH 拆解器）在 1.20.1 不存在，直接删：普通破坏已经给出
 * 带 {@code coordSet} NBT 的物品（见 {@link #getDrops}）。
 */
public class BlockDimensionalPocket extends Block implements EntityBlock {

    /** 1.7.10 的 {@code Reference.DIMENSIONAL_POCKET_RESISTANCE} 默认值。 */
    private static final float DEFAULT_EXPLOSION_RESISTANCE = 15.0F;

    public BlockDimensionalPocket() {
        super(Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.ANVIL)
                .requiresCorrectToolForDrops()
                .strength(4.0F, configuredExplosionResistance())
                .lightLevel(state -> 1)
                .pushReaction(PushReaction.BLOCK));
    }

    /**
     * 抗爆值来自配置。DeferredRegister 的 supplier 可能早于配置加载被调用（此时读配置会抛
     * {@code IllegalStateException}），因此这里兜底到 1.7.10 的默认值；运行时爆炸计算走
     * {@link #getExplosionResistance(BlockState, BlockGetter, BlockPos, Explosion)} 重新读配置，
     * 所以即使走了兜底也不会用错值。
     */
    private static float configuredExplosionResistance() {
        try {
            return DPConfig.dimensionalPocketResistance();
        } catch (RuntimeException e) {
            ModLog.warn("block", "resistance",
                    "reason=config_unavailable actual=threw expected=loaded fallback={}",
                    DEFAULT_EXPLOSION_RESISTANCE);
            return DEFAULT_EXPLOSION_RESISTANCE;
        }
    }

    // ------------------------------------------------------------------
    // 方块实体三件套
    // ------------------------------------------------------------------

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileDimensionalPocket(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        if (type != net.gtn.dimensionalpocket.common.ModBlockEntities.DIMENSIONAL_POCKET.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof TileDimensionalPocket tile) {
                tile.tick();
            }
        };
    }

    /** 1.7.10: {@code getRenderType() == -1}（不走方块模型，只有 TESR）。 */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    // ------------------------------------------------------------------
    // 放置 / 右键
    // ------------------------------------------------------------------

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof TileDimensionalPocket tile)) {
            ModLog.deny("block", "placed",
                    "reason=no_block_entity {} actual={} expected=TileDimensionalPocket",
                    ModLog.pos(pos), ModLog.block(level, pos));
            return;
        }
        tile.onBlockAdded(placer instanceof Player player ? player : null);
    }

    /**
     * 1.7.10: {@code BlockAbstract.onBlockActivated} → {@code TileDimensionalPocket.onActivated}。
     *
     * <p>注意上游**没有潜行分支**：空手右键与空手潜行右键行为完全相同（都是进入口袋）；
     * 手上有任何物品（含方块）都只是「消费右键但不进口袋」。
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            // 上游只看主手（player.getCurrentEquippedItem()）
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof TileDimensionalPocket tile)) {
            ModLog.deny("block", "use",
                    "reason=no_block_entity {} actual={} expected=TileDimensionalPocket",
                    ModLog.pos(pos), ModLog.block(level, pos));
            return InteractionResult.PASS;
        }
        tile.onActivated(player, hit);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide()) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof TileDimensionalPocket tile) {
            tile.onNeighbourBlockChanged();
            tile.onNeighbourTileChanged(fromPos);
        }
    }

    // ------------------------------------------------------------------
    // 破坏 / 掉落
    // ------------------------------------------------------------------

    /**
     * 掉落物由方块实体生成（带 {@code coordSet} NBT = 口袋索引）。1.7.10 里
     * {@code harvestBlock} 被重写成空、掉落由 {@code onBlockRemoval} 产生；1.20.1 的
     * {@code playerDestroy} 默认实现会走 {@code dropResources} → 本方法，所以玩家挖掘、
     * 其它 mod 拆解、{@code destroyBlock} 都会走到这里，不需要额外的 {@code onRemove} 逻辑
     * （放 {@code onRemove} 会在区块卸载/世界生成时刷物品）。
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof TileDimensionalPocket tile) {
            ItemStack drop = tile.generateItemStackOnRemoval();
            if (drop.isEmpty()) {
                ModLog.deny("block", "drops",
                        "reason=empty_drop actual=EMPTY expected=dimensional_pocket_with_coordSet");
                return List.of();
            }
            return List.of(drop);
        }
        ModLog.deny("block", "drops",
                "reason=no_block_entity actual={} expected=TileDimensionalPocket",
                blockEntity == null ? "null" : blockEntity.getType());
        return List.of();
    }

    /**
     * 1.7.10: {@code BlockAbstract.onBlockExploded} → {@code onBlockRemoval} → 掉出带 NBT 的口袋物品。
     * 1.20.1 的爆炸本身不产生掉落物，这里显式补上以对齐上游行为。
     */
    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        if (level.isClientSide()) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof TileDimensionalPocket tile) {
            Block.popResource(level, pos, tile.generateItemStackOnRemoval());
            ModLog.step("block", "exploded", "dropped_pocket_item {}", ModLog.pos(pos));
        }
    }

    /** 抗爆值运行时读配置，避免注册期读配置的时序问题。 */
    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return DPConfig.dimensionalPocketResistance();
    }

    /** 中键取方块：给出带 {@code coordSet} 的物品，否则复制出来的方块会丢掉口袋索引。 */
    @Override
    public ItemStack getCloneItemStack(BlockState state, net.minecraft.world.phys.HitResult target, BlockGetter level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof TileDimensionalPocket tile) {
            return tile.generateItemStackOnRemoval();
        }
        ModLog.deny("block", "clone_stack",
                "reason=no_block_entity {} actual=null expected=TileDimensionalPocket", ModLog.pos(pos));
        return super.getCloneItemStack(state, target, level, pos, player);
    }
}
