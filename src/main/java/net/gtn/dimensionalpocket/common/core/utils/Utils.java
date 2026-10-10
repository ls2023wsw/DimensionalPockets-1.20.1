package net.gtn.dimensionalpocket.common.core.utils;

import java.util.EnumMap;

import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ICapabilityProviderImpl;
import net.minecraftforge.common.Tags;

/**
 * 杂项工具集合（口袋 / 方块实体 / 渲染层都要用）。
 *
 * <p>1.7.10 origin: {@code net.gtn.dimensionalpocket.common.core.utils.Utils}。1.20.1 落地（报告 01 §12.3）：
 * <ul>
 *   <li>{@code EnumMap<ForgeDirection, Colour> FD_COLOURS} + 六向颜色 + alpha 7.0 原样保留（渲染层要用）。</li>
 *   <li>{@code getDirectionFromBitMask} 的映射表原样保留（报告 01 §1.3），不命中返回 {@code null}
 *       （1.20.1 没有 {@code ForgeDirection.UNKNOWN}）。</li>
 *   <li>{@code getPlayerPersistTag} 退化成一行 {@code player.getPersistentData()}
 *       （Forge 1.20.1 的 {@code Entity#getPersistentData()} 对玩家自动持久化且已按 mod 隔离）。</li>
 *   <li>{@code generateItem} 用 1.20.1 组件 API 写 {@code display.Name} / {@code display.Lore}。</li>
 *   <li>{@code isOreDictItem(stack, "stickWood")} -&gt; {@code stack.is(Tags.Items.RODS_WOODEN)}（{@code forge:rods/wooden}）。</li>
 * </ul>
 *
 * <p><b>刻意不移植</b>（契约 §5 / 报告 01 §12.3）：{@code enforceServer}/{@code enforceClient}（依赖 client-only
 * 的 {@code Minecraft.getMinecraft()}）、{@code createChatLink}/{@code getColourByName}（只被已丢弃的 VersionChecker 用）、
 * {@code getInventory}（1.20.1 的物品搬运走 {@code ForgeCapabilities.ITEM_HANDLER}，不再有 {@code IInventory}）、
 * {@code capitalizeString}（全仓库没有调用点）。
 */
public final class Utils {

    /**
     * 1.20.1 取代 {@code ForgeDirection.VALID_DIRECTIONS}：**顺序**沿用上游
     * {@code {DOWN, UP, NORTH, SOUTH, WEST, EAST}}（报告 01 §16.1 第 3 条，上游不依赖顺序，但保持一致更省心）。
     */
    public static final Direction[] VALID_DIRECTIONS = {
            Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    /** 六个面的显示颜色（渲染层用）。上游 alpha 被强制成 {@code 7.0}。 */
    public static final EnumMap<Direction, Colour> FD_COLOURS = new EnumMap<>(Direction.class);

    static {
        // @formatter:off
        double alpha = 7.0;
        Colour purple = Colour.PURPLE.copy();
        purple.a = alpha;
        Colour orange = Colour.ORANGE.copy();
        orange.a = alpha;
        Colour yellow = Colour.YELLOW.copy();
        yellow.a = alpha;
        Colour blue = Colour.BLUE.copy();
        blue.a = alpha;
        Colour green = Colour.GREEN.copy();
        green.a = alpha;
        Colour red = Colour.RED.copy();
        red.a = alpha;

        FD_COLOURS.put(Direction.DOWN,  purple);
        FD_COLOURS.put(Direction.UP,    orange);
        FD_COLOURS.put(Direction.NORTH, yellow);
        FD_COLOURS.put(Direction.SOUTH, blue);
        FD_COLOURS.put(Direction.WEST,  green);
        FD_COLOURS.put(Direction.EAST,  red);
        // @formatter:on
    }

    private Utils() {}

    /**
     * 上游的位掩码 -&gt; 方向映射（报告 01 §1.3）：0-&gt;SOUTH，1-&gt;WEST，2-&gt;NORTH，3-&gt;EAST。
     *
     * @return 不命中返回 {@code null}（1.20.1 没有 {@code ForgeDirection.UNKNOWN}）
     */
    public static Direction getDirectionFromBitMask(int num) {
        switch (num) {
            case 0:
                return Direction.SOUTH;
            case 1:
                return Direction.WEST;
            case 2:
                return Direction.NORTH;
            case 3:
                return Direction.EAST;
            default:
                return null;
        }
    }

    /**
     * 玩家的持久化 NBT 根（1.20.1 已按 mod 隔离，不用再套两层复合标签）。
     */
    public static CompoundTag getPlayerPersistTag(Player player) {
        if (player == null) {
            ModLog.deny("utils", "player_persist_tag", "reason=null_player actual=null expected=player");
            return new CompoundTag();
        }
        return player.getPersistentData();
    }

    /**
     * 往物品的 {@code display} 标签写名字与 lore（上游 {@code generateItem}，Thanks to oku）。
     *
     * <p>1.20.1 的 {@code Name}/{@code Lore} 必须是 JSON 组件：名字走 {@code setHoverName}，
     * lore 走 {@code display.Lore} 的 {@code ListTag<StringTag>}（每行一个组件 JSON，前缀灰色，与上游一致）。
     *
     * @param forceCleanName true 时名字前面加 {@code RESET} 样式（上游 {@code EnumChatFormatting.RESET}）
     */
    public static ItemStack generateItem(ItemStack itemStack, String name, boolean forceCleanName, String... loreStrings) {
        if (itemStack == null || itemStack.isEmpty()) {
            ModLog.deny("utils", "generate_item", "reason=empty_or_null_stack actual={} expected=non_empty_stack",
                    itemStack == null ? "null" : "EMPTY");
            return itemStack;
        }

        if (loreStrings != null && loreStrings.length > 0) {
            ListTag lore = new ListTag();
            for (String s : loreStrings) {
                if (s != null) {
                    lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(s).withStyle(ChatFormatting.GRAY))));
                }
            }
            if (!lore.isEmpty()) {
                itemStack.getOrCreateTagElement("display").put("Lore", lore);
            }
        }

        if (name != null) {
            MutableComponent component = Component.literal(name);
            itemStack.setHoverName(forceCleanName ? component.withStyle(ChatFormatting.RESET) : component);
        }

        return itemStack;
    }

    /**
     * 在世界里丢一个物品（上游 {@code spawnItemStack}）。
     *
     * @param delayBeforePickup 拾取延迟 tick（上游 {@code EntityItem.delayBeforeCanPickup}）
     */
    public static void spawnItemStack(ItemStack itemStack, Level level, double x, double y, double z, int delayBeforePickup) {
        if (itemStack == null || itemStack.isEmpty()) {
            ModLog.deny("utils", "spawn_item", "reason=empty_or_null_stack actual={} expected=non_empty_stack",
                    itemStack == null ? "null" : "EMPTY");
            return;
        }
        if (level == null) {
            ModLog.deny("utils", "spawn_item", "reason=null_level actual=null expected=level item={}", ModLog.item(itemStack));
            return;
        }

        ItemEntity entityItem = new ItemEntity(level, x, y, z, itemStack);
        entityItem.setPickUpDelay(delayBeforePickup);
        level.addFreshEntity(entityItem);
    }

    /**
     * 「口袋扳手」判定（隐藏玩法，报告 02 §15.3）：木棍 + 自定义名恰好是 {@code Pocket Wrench}。
     *
     * <p>纯判定方法，**刻意不打日志**（每次右键方块都会被调用，留痕会刷屏；调用方自己记分支结果）。
     */
    public static boolean isItemPocketWrench(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (!stack.is(Tags.Items.RODS_WOODEN)) {
            return false;
        }
        if (!stack.hasCustomHoverName()) {
            return false;
        }
        return "Pocket Wrench".equalsIgnoreCase(stack.getHoverName().getString());
    }

    /**
     * 让某一格方块实体重新收集能力（capability），使之前发给邻居的 {@code LazyOptional} 失效。
     *
     * <p>1.20.1 Forge 47.4.0 **没有** {@code Level#invalidateCapabilities(BlockPos)}（报告 02 §19.4 的
     * 建议在本机映射 jar 与 sources jar 里都查不到，0 命中）。这里用等价且安全的写法：先
     * {@code invalidateCaps()} 让已经发出去的 {@code LazyOptional} 失效，立刻 {@code reviveCaps()}
     * 重新收集 —— 只调前者会把 provider 永久置为 invalid，能力会彻底消失。
     */
    public static void invalidateCapabilities(Level level, BlockPos pos) {
        if (level == null || pos == null || level.isClientSide()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            blockEntity.invalidateCaps();
            blockEntity.reviveCaps();
        }
    }
}
