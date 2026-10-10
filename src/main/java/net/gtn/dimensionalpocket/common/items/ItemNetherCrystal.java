package net.gtn.dimensionalpocket.common.items;

import java.util.List;

import net.gtn.dimensionalpocket.common.ModBlocks;
import net.gtn.dimensionalpocket.common.core.DPConfig;
import net.gtn.dimensionalpocket.common.core.pocket.Pocket;
import net.gtn.dimensionalpocket.common.core.pocket.PocketRegistry;
import net.gtn.dimensionalpocket.common.core.pocket.PocketSideState;
import net.gtn.dimensionalpocket.common.core.utils.CoordSet;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.gtn.dimensionalpocket.common.tileentity.TileDimensionalPocket;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;

/**
 * 地狱水晶。1.7.10 对应物 {@code common/items/ItemNetherCrystal.java}。
 *
 * <p>①{@code dimensional_pocket} 的合成材料；②右键口袋方块 / 墙方块切换该面的 {@code PocketSideState}；
 * ③潜行右键墙方块把该面的 Connector 移到这一格。**都不消耗物品、不掉耐久**（耐久只在合成台扣）。
 *
 * <p>两个分支的「面」来源不同（上游事实，别搞混）：
 * <ul>
 *   <li>房间内的墙方块：由该格在 16³ 房间内的偏移推出（{@code Pocket.getSideForConnector}）；</li>
 *   <li>房间外的 DP 方块：由玩家点击的那一面决定（{@code context.getClickedFace()}）。</li>
 * </ul>
 */
public class ItemNetherCrystal extends ItemDP {

    public ItemNetherCrystal(Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    // ------------------------------------------------------------------
    // 「可合成次数」= 物品耐久（itemDamage）。配置为 0 表示永不损坏。
    // ------------------------------------------------------------------

    @Override
    public int getMaxDamage(ItemStack stack) {
        return DPConfig.craftingsPerCrystal();
    }

    @Override
    public boolean canBeDepleted() {
        return DPConfig.craftingsPerCrystal() > 0;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return false;
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        int max = DPConfig.craftingsPerCrystal();
        return !(max > 0 && stack.getDamageValue() >= max);
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        int max = DPConfig.craftingsPerCrystal();
        if (max == 0) {
            return new ItemStack(this);
        }
        int damage = stack.getDamageValue() + 1;
        if (damage >= max) {
            ModLog.step("item", "crystal_break", "item=nether_crystal damage={} max={}", damage, max);
            return ItemStack.EMPTY;
        }
        ItemStack result = new ItemStack(this, stack.getCount());
        result.setDamageValue(damage);
        return result;
    }

    // ------------------------------------------------------------------
    // 右键方块（1.7.10: onItemUseFirst）
    // ------------------------------------------------------------------

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        boolean isPocketBlock = state.is(ModBlocks.DIMENSIONAL_POCKET.get());
        boolean isWallBlock = state.is(ModBlocks.DIMENSIONAL_POCKET_WALL.get());
        if (!isPocketBlock && !isWallBlock) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            // 上游：客户端只挥手并 return false，好让服务端也收到这次调用
            player.swing(context.getHand());
            return InteractionResult.PASS;
        }

        if (isWallBlock) {
            return useOnWall(player, level, pos);
        }
        return useOnPocketBlock(player, level, pos, context.getClickedFace());
    }

    /** 分支 A：房间内的墙方块。面由「格子在该区块内的偏移」推出。 */
    private InteractionResult useOnWall(Player player, Level level, BlockPos pos) {
        if (level.dimension() != Reference.POCKET_LEVEL) {
            ModLog.deny("item", "nether_crystal_use",
                    "reason=wall_outside_pocket_dim {} {} actual={} expected={}",
                    ModLog.player(player), ModLog.pos(pos), level.dimension().location(), Reference.POCKET_LEVEL_ID);
            return InteractionResult.PASS;
        }

        CoordSet coordSet = new CoordSet(pos);
        Pocket pocket = PocketRegistry.getPocket(coordSet.toChunkCoords());
        if (pocket == null) {
            ModLog.deny("item", "nether_crystal_use",
                    "reason=no_pocket_for_chunk {} chunk={} actual=null expected=Pocket",
                    ModLog.pos(pos), coordSet.toChunkCoords());
            return InteractionResult.PASS;
        }

        Direction wallSide = Pocket.getSideForConnector(coordSet.toChunkOffset());
        if (wallSide == null) {
            ModLog.deny("item", "nether_crystal_use",
                    "reason=not_on_room_face {} offset={} actual=null expected=one_of_6_directions",
                    ModLog.pos(pos), coordSet.toChunkOffset());
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            // 潜行：把该面的 Connector 移到这一格
            CoordSet oldConnector = pocket.getConnectorCoords(wallSide);
            if (coordSet.equals(oldConnector)) {
                ModLog.deny("item", "nether_crystal_use",
                        "reason=already_connector {} face={} actual={} expected=different_coords",
                        ModLog.pos(pos), ModLog.face(wallSide), oldConnector);
                return InteractionResult.PASS;
            }
            boolean moved = pocket.setConnectorForSide(wallSide, coordSet);
            ModLog.step("item", "nether_crystal_use", "connector_moved {} {} old={} new={} result={}",
                    ModLog.player(player), ModLog.face(wallSide), oldConnector, coordSet, moved);
        } else {
            cyclePocketSideState(pocket, wallSide, player);
        }
        return InteractionResult.SUCCESS;
    }

    /** 分支 B：房间外的 DP 方块。面由玩家点击的那一面决定。 */
    private InteractionResult useOnPocketBlock(Player player, Level level, BlockPos pos, Direction clickedFace) {
        if (!(level.getBlockEntity(pos) instanceof TileDimensionalPocket tile)) {
            ModLog.deny("item", "nether_crystal_use",
                    "reason=no_pocket_tile_entity {} actual={} expected=TileDimensionalPocket",
                    ModLog.pos(pos), ModLog.block(level, pos));
            return InteractionResult.PASS;
        }
        Pocket pocket = tile.getPocket();
        if (pocket == null) {
            ModLog.deny("item", "nether_crystal_use",
                    "reason=no_pocket_for_block {} actual=null expected=Pocket", ModLog.pos(pos));
            return InteractionResult.PASS;
        }
        cyclePocketSideState(pocket, clickedFace, player);
        return InteractionResult.SUCCESS;
    }

    /** 上游 {@code ItemNetherCrystal.cyclePocketSideState}：NONE ↔ ENERGY 二元切换。 */
    private static void cyclePocketSideState(Pocket pocket, Direction wallSide, Player player) {
        PocketSideState oldState = pocket.getFlowState(wallSide);
        PocketSideState newState = oldState.next();
        pocket.setFlowState(wallSide, newState);
        player.displayClientMessage(Component.translatable("info.pocket.side.state.set.to",
                wallSide.getName(), newState.translateName()), false);
        ModLog.state("item", "nether_crystal_use", "side_state {} face={} state={}->{}",
                ModLog.player(player), ModLog.face(wallSide), oldState.name(), newState.name());
    }

    // ------------------------------------------------------------------
    // tooltip
    // ------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int max = DPConfig.craftingsPerCrystal();
        int damage = stack.getDamageValue();
        if (damage > 0) {
            int remaining = max - damage;
            if (remaining == 1) {
                tooltip.add(Component.translatable("info.tooltip.crystal.craft.remaining.singular"));
            } else {
                tooltip.add(Component.translatable("info.tooltip.crystal.craft.remaining.plural", remaining));
            }
        }
        tooltip.add(Component.translatable("info.tooltip.default.shift"));
        if (FMLEnvironment.dist == Dist.CLIENT && net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("info.tooltip.netherCrystal.shift"));
        }
    }
}
