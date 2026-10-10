package net.gtn.dimensionalpocket.common.tileentity;

import net.gtn.dimensionalpocket.common.core.pocket.Pocket;
import net.gtn.dimensionalpocket.common.core.utils.CoordSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 两个方块实体的公共基类。1.7.10 对应物 {@code common/tileentity/TileDP.java}
 * （{@code TileAbstract} 的 {@code getDescriptionPacket} / {@code onDataPacket} / {@code markForUpdate}）。
 *
 * <p>上游的 {@code isUsableByPlayer} 是死代码（两个子类都没调用，其中一个还直接返回 false），未移植。
 *
 * <p>同步机制照上游：客户端渲染需要的全部数据（连接器位置、6 面状态、房间 chunk 坐标、creator）
 * 都通过「方块实体描述包」送过去 —— 服务端 {@code saveAdditional} 把整个 {@link Pocket} 写进
 * {@code pocketData} 子键，客户端 {@link #load} 再反序列化成 {@link #pocket}。
 */
public abstract class TileDP extends BlockEntity {

    /** 1.7.10: {@code TileDimensionalPocket.TAG_CUSTOM_DP_NAME}。 */
    protected static final String TAG_CUSTOM_DP_NAME = "customDPName";

    /** 客户端持有的 Pocket 副本（由描述包反序列化）。服务端永远为 null，走 PocketRegistry。 */
    @Nullable
    protected Pocket pocket;

    /** 方块自定义名（来自物品的 {@code display.Name}），进掉落物 tooltip。 */
    @Nullable
    protected String customName;

    protected TileDP(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public CoordSet getCoordSet() {
        return new CoordSet(worldPosition);
    }

    /** 服务端：DP 方块按自身坐标 getOrCreate，连接器按自身区块坐标只查不建。 */
    @Nullable
    public abstract Pocket getPocket();

    /** 1.7.10: {@code TileAbstract.markForUpdate()} → 给追踪该区块的客户端发描述包。 */
    public void markForUpdate() {
        if (level == null) {
            return;
        }
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /**
     * 面状态的键。
     *
     * <p>1.7.10 的两条路径在这一点上不同：外部 DP 方块用**被访问的面本身**，内部连接器用
     * **它的对面**（因为面状态的键始终是「房间某个面的外法线」，而连接器位于房间内表面）。
     */
    protected Direction flowStateKey(Direction side) {
        return side;
    }

    /**
     * 穿墙转发的目标方块实体：该面穿透到另一侧之后，与连接器/DP 方块相邻的那一格。
     *
     * @return 目标方块实体，目标世界未加载 / 目标不存在 / 客户端时返回 {@code null}
     */
    @Nullable
    protected abstract BlockEntity findTransferTarget(Direction side);

    // ------------------------------------------------------------------
    // 双向同步
    // ------------------------------------------------------------------

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            load(tag);
        }
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        Pocket current = getPocket();
        if (current != null) {
            current.writeToNbt(tag);
        }
        if (customName != null) {
            tag.putString(TAG_CUSTOM_DP_NAME, customName);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // 只有客户端反序列化 Pocket；服务端的权威数据在 PocketRegistry（存档 JSON）里。
        if (level != null && level.isClientSide) {
            pocket = Pocket.readFromNbt(tag);
        }
        String name = tag.getString(TAG_CUSTOM_DP_NAME);
        if (!name.isEmpty()) {
            customName = name;
        }
    }
}
