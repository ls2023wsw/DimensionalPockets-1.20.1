package net.gtn.dimensionalpocket.common.items;

import java.util.List;

import net.gtn.dimensionalpocket.common.core.DPConfig;
import net.gtn.dimensionalpocket.common.core.pocket.Pocket;
import net.gtn.dimensionalpocket.common.core.pocket.PocketRegistry;
import net.gtn.dimensionalpocket.common.core.utils.CoordSet;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;

/**
 * 末影水晶。1.7.10 对应物 {@code common/items/ItemEndCrystal.java}。
 *
 * <p>两个作用：①{@code dimensional_pocket} 的合成材料（靠 {@code getCraftingRemainingItem} 扣「可合成次数」）；
 * ②在口袋房间内右键把玩家当前位置设为该口袋的出生点（不消耗物品、不掉耐久）。
 */
public class ItemEndCrystal extends ItemDP {

    public ItemEndCrystal(Item.Properties properties) {
        // 1.7.10: setMaxDurability(CRAFTINGS_PER_CRYSTAL) + setNoRepair() + setEffect()
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
        // 1.7.10: setEffect()
        return true;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        // 1.7.10: setNoRepair()
        return false;
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        int max = DPConfig.craftingsPerCrystal();
        return !(max > 0 && stack.getDamageValue() >= max);
    }

    /** 唯一扣减点：合成台把材料替换成「容器物品」时调用。 */
    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        int max = DPConfig.craftingsPerCrystal();
        if (max == 0) {
            return new ItemStack(this);
        }
        int damage = stack.getDamageValue() + 1;
        if (damage >= max) {
            // 水晶碎掉：上游返回 null，1.20.1 用 ItemStack.EMPTY
            ModLog.step("item", "crystal_break", "item=end_crystal damage={} max={}", damage, max);
            return ItemStack.EMPTY;
        }
        ItemStack result = new ItemStack(this, stack.getCount());
        result.setDamageValue(damage);
        return result;
    }

    // ------------------------------------------------------------------
    // 右键：在口袋维度里设置出生点
    // ------------------------------------------------------------------

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.level().dimension() != Reference.POCKET_LEVEL) {
            ModLog.trace("item", "end_crystal_use",
                    "reason=not_in_pocket_dim {} {} actual={} expected={}",
                    ModLog.player(player), ModLog.dim(level), level.dimension().location(), Reference.POCKET_LEVEL_ID);
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide()) {
            player.swing(hand);
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        CoordSet coordSet = new CoordSet(player);
        Pocket pocket = PocketRegistry.getPocket(coordSet.toChunkCoords());
        if (pocket == null) {
            ModLog.deny("item", "end_crystal_use",
                    "reason=no_pocket_for_chunk {} chunk={} actual=null expected=Pocket",
                    ModLog.player(player), coordSet.toChunkCoords());
            return InteractionResultHolder.pass(stack);
        }

        pocket.setSpawnInPocket(coordSet.toChunkOffset(), player.getYRot(), player.getXRot());
        player.displayClientMessage(
                Component.translatable("info.spawn.set.in.pocket").withStyle(ChatFormatting.ITALIC), false);
        ModLog.step("item", "end_crystal_use", "spawn_set {} {} chunk={}",
                ModLog.player(player), ModLog.dim(level), pocket.getChunkCoords());
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    // ------------------------------------------------------------------
    // tooltip（1.7.10: addInformation）
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
            tooltip.add(Component.translatable("info.tooltip.endCrystal.shift"));
        }
    }
}
