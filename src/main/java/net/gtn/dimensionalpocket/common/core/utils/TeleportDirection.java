package net.gtn.dimensionalpocket.common.core.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 传送落点候选方向的枚举 —— 18 个「相邻 / 斜向 / 下方一格」的偏移，外加 {@code UNKNOWN}；
 * 提供「找第一个两格高都是空气的方向」的探测。
 *
 * <p>1.7.10 origin: {@code net.gtn.dimensionalpocket.common.core.utils.TeleportDirection}。
 * <b>19 个枚举值、偏移量、{@code VALID_DIRECTIONS} 的 18 项顺序全部逐字照抄</b>（报告 01 §1.4）：
 * 这是可观测行为，决定玩家从哪一侧出口袋，顺序错了玩家会从错误的方向出现。
 *
 * <p>注意 {@code DOWN = (0,-2,0)} 与 {@code UP = (0,1,0)} **故意不对称**，不要「顺手改整齐」。
 */
public enum TeleportDirection {

    //@formatter:off
    /** -Y  */
    DOWN(0, -2, 0),

    /** +Y  */
    UP(0, 1, 0),

    /** -Z  */
    NORTH(0, 0, -1), NORTH_MINUS_ONE(0, -1, -1),

    /** +Z  */
    SOUTH(0, 0, 1), SOUTH_MINUS_ONE(0, -1, 1),

    /** -X  */
    WEST(-1, 0, 0), WEST_MINUS_ONE(-1, -1, 0),

    /** +X  */
    EAST(1, 0, 0), EAST_MINUS_ONE(1, -1, 0),

    /** -Z -X */
    NORTHWEST(-1, 0, -1), NORTHWEST_MINUS_ONE(-1, -1, -1),

    /** -Z +X */
    NORTHEAST(1, 0, -1), NORTHEAST_MINUS_ONE(1, -1, -1),

    /** +Z -X */
    SOUTHWEST(-1, 0, 1), SOUTHWEST_MINUS_ONE(-1, -1, 1),

    /** +Z +X */
    SOUTHEAST(1, 0, 1), SOUTHEAST_MINUS_ONE(1, -1, 1),

    UNKNOWN(0, 0, 0);
    //@formatter:on

    /** 探测顺序，**决定优先落在哪个方向**（报告 01 §1.4，18 项，{@code UNKNOWN} 不在内）。 */
    public static final TeleportDirection[] VALID_DIRECTIONS = {
            UP, DOWN, NORTH, SOUTH, WEST, EAST,
            NORTH_MINUS_ONE, SOUTH_MINUS_ONE, WEST_MINUS_ONE, EAST_MINUS_ONE,
            NORTHWEST, NORTHEAST, SOUTHWEST, SOUTHEAST,
            NORTHWEST_MINUS_ONE, NORTHEAST_MINUS_ONE, SOUTHWEST_MINUS_ONE, SOUTHEAST_MINUS_ONE
    };

    public final int offsetX;
    public final int offsetY;
    public final int offsetZ;

    TeleportDirection(int x, int y, int z) {
        offsetX = x;
        offsetY = y;
        offsetZ = z;
    }

    /** 1.7.10: {@code world.isAirBlock(x, y, z) && world.isAirBlock(x, y + 1, z)} —— 必须两格都是空气。 */
    private static boolean isAir(Level level, BlockPos pos) {
        return level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above());
    }

    /**
     * 按 {@link #VALID_DIRECTIONS} 的顺序线性探测，返回第一个「两格高都是空气」的方向。
     *
     * @return 全不命中返回 {@link #UNKNOWN}
     */
    public static TeleportDirection getValidTeleportLocation(Level level, int x, int y, int z) {
        BlockPos origin = new BlockPos(x, y, z);
        for (TeleportDirection direction : VALID_DIRECTIONS) {
            if (isAir(level, origin.offset(direction.offsetX, direction.offsetY, direction.offsetZ))) {
                return direction;
            }
        }

        return UNKNOWN;
    }

    public CoordSet toCoordSet() {
        return new CoordSet(offsetX, offsetY, offsetZ);
    }
}
