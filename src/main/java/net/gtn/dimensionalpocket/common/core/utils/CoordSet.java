package net.gtn.dimensionalpocket.common.core.utils;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A mutable triple of block coordinates with the usual helpers.
 *
 * <p>1.7.10 origin: {@code net.gtn.dimensionalpocket.oc.common.utils.CoordSet}. API-translated to
 * 1.20.1: {@code ForgeDirection} -&gt; {@link Direction}, {@code TileEntity} -&gt; {@link BlockEntity},
 * {@code IBlockAccess} -&gt; {@link BlockGetter}, {@code EntityPlayer} -&gt; {@link Player},
 * {@code ChunkCoordinates} -&gt; {@link BlockPos}.
 */
public class CoordSet implements Cloneable {

    public int x, y, z;

    public CoordSet() {
        this(0, 0, 0);
    }

    public CoordSet(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public CoordSet(int[] array) throws ArrayIndexOutOfBoundsException {
        this(array[0], array[1], array[2]);
    }

    public CoordSet(BlockPos pos) {
        this(pos.getX(), pos.getY(), pos.getZ());
    }

    public CoordSet(BlockEntity blockEntity) throws NullPointerException {
        this(blockEntity.getBlockPos());
    }

    /**
     * Floors the player's position.
     */
    public CoordSet(Player player) {
        this(player, RoundingMethod.FLOOR);
    }

    public CoordSet(Player player, RoundingMethod roundingMethod) {
        switch (roundingMethod) {
            case CEILING:
                x = (int) Math.ceil(player.getX());
                y = (int) Math.ceil(player.getY());
                z = (int) Math.ceil(player.getZ());
                break;
            case ROUND:
                x = (int) Math.round(player.getX());
                y = (int) Math.round(player.getY());
                z = (int) Math.round(player.getZ());
                break;
            case FLOOR:
            default:
                x = (int) Math.floor(player.getX());
                y = (int) Math.floor(player.getY());
                z = (int) Math.floor(player.getZ());
        }
    }

    public enum RoundingMethod {
        FLOOR,
        CEILING,
        ROUND
    }

    public BlockPos toBlockPos() {
        return new BlockPos(x, y, z);
    }

    public boolean withinRange(CoordSet tempSet, int range) {
        return getDistanceSq(tempSet) <= range * range;
    }

    public double getDistanceSq(CoordSet tempSet) {
        double x2 = x - tempSet.x;
        double y2 = y - tempSet.y;
        double z2 = z - tempSet.z;
        return x2 * x2 + y2 * y2 + z2 * z2;
    }

    public float getDistance(CoordSet tempSet) {
        return (float) Math.sqrt(getDistanceSq(tempSet));
    }

    public CoordSet addCoordSet(CoordSet coordSet) {
        x += coordSet.x;
        y += coordSet.y;
        z += coordSet.z;
        return this;
    }

    public CoordSet addDirection(Direction direction) {
        x += direction.getStepX();
        y += direction.getStepY();
        z += direction.getStepZ();
        return this;
    }

    public boolean isAdjacent(CoordSet coordSet) {
        for (Direction direction : Direction.values()) {
            CoordSet tempSet = coordSet.copy().addDirection(direction);
            if (tempSet.equals(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    public BlockEntity getBlockEntity(BlockGetter world) {
        return world.getBlockEntity(toBlockPos());
    }

    public BlockEntity getBlockEntityFromDirection(BlockGetter world, Direction direction) {
        return copy().addDirection(direction).getBlockEntity(world);
    }

    public boolean hasBlockEntity(BlockGetter world) {
        return getBlockEntity(world) != null;
    }

    public boolean isAirBlock(BlockGetter world) {
        return world.getBlockState(toBlockPos()).isAir();
    }

    public boolean setBlockToAir(Level world) {
        return world.removeBlock(toBlockPos(), false);
    }

    // ------------------------------------------------------------------ NBT

    public void writeToNBT(CompoundTag tag) {
        writeToNBT(tag, "coordSet");
    }

    public void writeToNBT(CompoundTag tag, String key) {
        tag.putIntArray(key, asArray());
    }

    public static CoordSet readFromNBT(CompoundTag tag) {
        return readFromNBT(tag, "coordSet");
    }

    public static CoordSet readFromNBT(CompoundTag tag, String key) {
        int[] coords = tag.getIntArray(key);
        if (coords.length == 3) {
            return new CoordSet(coords);
        }
        return null;
    }

    // ------------------------------------------------------------------ bytes / strings

    public void writeBytes(ByteBuf bytes) {
        bytes.writeInt(x);
        bytes.writeInt(y);
        bytes.writeInt(z);
    }

    public static CoordSet readBytes(ByteBuf bytes) {
        int x = bytes.readInt();
        int y = bytes.readInt();
        int z = bytes.readInt();
        return new CoordSet(x, y, z);
    }

    public static CoordSet readFromString(String loc) {
        if (loc == null || !loc.matches("-?\\d*:-?\\d*:-?\\d*")) {
            return null;
        }
        String[] coords = loc.split(":");
        try {
            return new CoordSet(Integer.parseInt(coords[0]), Integer.parseInt(coords[1]), Integer.parseInt(coords[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public int[] asArray() {
        return new int[] { x, y, z };
    }

    public boolean equals(int x, int y, int z) {
        return this.x == x && this.y == y && this.z == z;
    }

    @Override
    public int hashCode() {
        int hash = x;
        hash *= 31 + y;
        hash *= 31 + z;
        return hash;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CoordSet)) {
            return false;
        }
        return ((CoordSet) other).equals(x, y, z);
    }

    public String toPacketString() {
        return x + ":" + y + ":" + z;
    }

    @Override
    public String toString() {
        return "[" + toPacketString() + "]";
    }

    @Override
    public CoordSet clone() {
        try {
            return (CoordSet) super.clone();
        } catch (CloneNotSupportedException e) {
            // should not happen, CoordSet only holds primitives
        }
        return new CoordSet(x, y, z);
    }

    public CoordSet copy() {
        return clone();
    }

    // ------------------------------------------------------------------ chunk maths

    public CoordSet asChunkCoords() {
        x = x >> 4;
        y = y >> 4;
        z = z >> 4;
        return this;
    }

    public CoordSet toChunkCoords() {
        return copy().asChunkCoords();
    }

    public CoordSet asBlockCoords() {
        x = x << 4;
        y = y << 4;
        z = z << 4;
        return this;
    }

    public CoordSet toBlockCoords() {
        return copy().asBlockCoords();
    }

    public CoordSet asChunkOffset() {
        x = x < 0 ? x % 16 + 16 & 15 : x % 16;
        y = y < 0 ? y % 16 + 16 & 15 : y % 16;
        z = z < 0 ? z % 16 + 16 & 15 : z % 16;
        return this;
    }

    public CoordSet toChunkOffset() {
        return copy().asChunkOffset();
    }
}
