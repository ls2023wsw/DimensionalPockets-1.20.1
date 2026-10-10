package net.gtn.dimensionalpocket.common.tileentity;

import net.gtn.dimensionalpocket.common.ModBlockEntities;
import net.gtn.dimensionalpocket.common.block.BlockDimensionalPocketWall;
import net.gtn.dimensionalpocket.common.core.pocket.Pocket;
import net.gtn.dimensionalpocket.common.core.pocket.PocketRegistry;
import net.gtn.dimensionalpocket.common.core.pocket.PocketSideState;
import net.gtn.dimensionalpocket.common.core.utils.CoordSet;
import net.gtn.dimensionalpocket.common.core.utils.Utils;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 口袋房间外壳上「连接器」那一格的方块实体。1.7.10 对应物 705 行的
 * {@code common/tileentity/TileDimensionalPocketWallConnector.java}。
 *
 * <p>它是**反方向**的穿墙端：机器在房间内贴着它时，能量/流体/物品会被转发到房间外那台
 * DP 方块旁边的机器。每 20 tick 自检「自己还是不是该面的合法连接器」，不是就把自己降级成普通外壳。
 *
 * <p>与 {@link TileDimensionalPocket} 的三处关键差异：
 * <ol>
 *   <li>{@code getPocket()} 只用自己所在**区块**坐标查（只查不建，查不到返回 null）；</li>
 *   <li>面状态的键取 {@code side.getOpposite()}（房间面的外法线 = 朝房间内的反方向）；</li>
 *   <li>转发目标在「DP 方块所在维度」，坐标 = {@code p.getBlockCoords() + side.getOpposite()}。</li>
 * </ol>
 */
public class TileDimensionalPocketWallConnector extends TileDP {

    /** 1.7.10: {@code updateEntity} 里的 {@code counter > 20} 自检周期。 */
    private static final int VALIDATION_INTERVAL_TICKS = 20;

    private int counter;

    private final LazyOptional<IEnergyStorage>[] energyCaps = new LazyOptional[6];
    private final LazyOptional<IFluidHandler>[] fluidCaps = new LazyOptional[6];
    private final LazyOptional<IItemHandler>[] itemCaps = new LazyOptional[6];

    public TileDimensionalPocketWallConnector(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WALL_CONNECTOR.get(), pos, state);
        createCapabilities();
    }

    // ------------------------------------------------------------------
    // pocket 绑定（1.7.10: getPocket）—— 只查不建
    // ------------------------------------------------------------------

    /**
     * 连接器位于口袋房间内部，它的区块坐标**就是** pocket 的 {@code chunkCoords}。
     * 查不到就返回 null（连接器无法凭空创建口袋，所有能力转发会因此静默失效）。
     */
    @Nullable
    @Override
    public Pocket getPocket() {
        if (level == null) {
            return null;
        }
        if (level.isClientSide()) {
            return pocket;
        }
        return PocketRegistry.getPocket(getCoordSet().toChunkCoords());
    }

    // ------------------------------------------------------------------
    // tick：20 tick 自检（1.7.10: updateEntity）
    // ------------------------------------------------------------------

    /**
     * 1.7.10 里首 tick 会把 metadata 写成 {@code CONNECTOR_META}；1.20.1 不能「先放 meta 0 再改成 1」
     * （那会重建方块实体），改由放置方一次性给出 {@code CONNECTOR=true} 的 BlockState，这里不再改状态。
     */
    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (++counter <= VALIDATION_INTERVAL_TICKS) {
            return;
        }
        counter = 0;

        Pocket pocket = getPocket();
        if (pocket == null) {
            return;
        }

        CoordSet tileCoords = getCoordSet();
        Direction wallSide = Pocket.getSideForConnector(tileCoords.toChunkOffset());
        CoordSet connectorCoords = wallSide == null ? null : pocket.getConnectorCoords(wallSide);

        if (!tileCoords.equals(connectorCoords)) {
            ModLog.state("tile", "connector_check",
                    "self_invalid {} face={} actual={} expected={}",
                    ModLog.pos(worldPosition), wallSide == null ? "none" : wallSide.getName(), tileCoords, connectorCoords);
            invalidateConnector();
        }
    }

    /**
     * 1.7.10: {@code invalidateConnector()} —— 把这一格从「连接器」降级成普通外壳。
     *
     * <p>上游是 {@code setBlockMetadataWithNotify(..., 0, 3)} + {@code invalidate()}。1.20.1 的
     * {@code BlockState#hasBlockEntity()} 只看「方块是否 implements EntityBlock」，换状态**不会**
     * 自动摘掉方块实体，所以这里先 {@code removeBlock}（连同 BE 一起移除）再放回降级后的状态，
     * 否则降级后 BE 会永久残留在方块上（每 20 tick 空转 + 渲染器继续画）。
     *
     * <p>被 {@code Pocket.setConnectorForSide} 主动调用一次，旧格自己的 20 tick 自检还会二次确认（双保险）。
     */
    public void invalidateConnector() {
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockState state = getBlockState();
        if (!state.hasProperty(BlockDimensionalPocketWall.CONNECTOR)) {
            ModLog.error("tile", "invalidate_connector",
                    "reason=missing_connector_property {} actual={} expected=BlockDimensionalPocketWall[connector]",
                    ModLog.pos(worldPosition), ModLog.st(state));
            return;
        }
        if (!state.getValue(BlockDimensionalPocketWall.CONNECTOR)) {
            ModLog.trace("tile", "invalidate_connector",
                    "reason=already_plain_wall {} actual=false expected=true", ModLog.pos(worldPosition));
            return;
        }

        BlockState demoted = state.setValue(BlockDimensionalPocketWall.CONNECTOR, false);
        level.removeBlock(worldPosition, false);
        level.setBlock(worldPosition, demoted, Block.UPDATE_ALL);

        ModLog.state("tile", "invalidate_connector",
                "connector_demoted {} st={}->{}", ModLog.pos(worldPosition), ModLog.st(state), ModLog.st(demoted));
    }

    // ------------------------------------------------------------------
    // 邻居变化（1.7.10: onNeighbourBlockChanged / onNeighbourTileChanged）
    // 上游两者都只是 markSourceBlockForUpdate()，由 BlockDimensionalPocketWall 派发。
    // ------------------------------------------------------------------

    public void onNeighbourChanged() {
        if (level == null || level.isClientSide()) {
            return;
        }
        Pocket pocket = getPocket();
        if (pocket == null) {
            return;
        }
        pocket.markSourceBlockForUpdate();
        Utils.invalidateCapabilities(level, worldPosition);
    }

    // ------------------------------------------------------------------
    // 穿墙转发目标（1.7.10: getDimPocketNeighbourTileEntity）
    // ------------------------------------------------------------------

    /**
     * 目标 = 「DP 方块所在维度」里 DP 方块坐标再朝外一格（方向为 {@code side.getOpposite()}）。
     *
     * <p>上游先检查 DP 方块那一格是否存在（= 源方块必须已放置），再检查邻居格。
     */
    @Nullable
    @Override
    protected BlockEntity findTransferTarget(Direction side) {
        if (level == null || level.isClientSide()) {
            return null;
        }
        Pocket pocket = getPocket();
        if (pocket == null) {
            return null;
        }
        if (level.getServer() == null) {
            return null;
        }
        ServerLevel targetLevel = level.getServer().getLevel(pocket.getBlockDim());
        if (targetLevel == null) {
            ModLog.denyRepeated("cap.no_target_level", 200, "tile", "find_target",
                    "reason=source_dimension_not_loaded {} actual=null expected=ServerLevel {}",
                    pocket.getBlockDim().location(), ModLog.face(side));
            return null;
        }

        CoordSet blockCoords = pocket.getBlockCoords();
        if (blockCoords == null) {
            return null;
        }
        if (!targetLevel.isLoaded(blockCoords.toBlockPos())) {
            // DP 方块那一格没加载/不存在：上游的「check if a DP is placed」
            return null;
        }
        CoordSet neighbourCoords = blockCoords.copy().addDirection(side.getOpposite());
        if (!targetLevel.isLoaded(neighbourCoords.toBlockPos())) {
            return null;
        }
        return targetLevel.getBlockEntity(neighbourCoords.toBlockPos());
    }

    /** 连接器侧的键取对面：面状态存的始终是「房间面的外法线」。 */
    @Override
    protected Direction flowStateKey(Direction side) {
        return side.getOpposite();
    }

    // ------------------------------------------------------------------
    // 渲染包围盒（1.7.10: getRenderBoundingBox）
    // ------------------------------------------------------------------

    /**
     * 把渲染包围盒从 1 格扩到**整个口袋房间内部**：自己落在房间的哪个面上，该轴就不扩
     * （{@code offset == 0 || 15}），另两个轴各扩 15 格。这样墙上的 TESR 能跨整个 16³ 空间画粒子场。
     */
    @Override
    public AABB getRenderBoundingBox() {
        CoordSet offset = getCoordSet().toChunkOffset();

        boolean ignoreX = offset.x == 0 || offset.x == 15;
        boolean ignoreY = offset.y == 0 || offset.y == 15;
        boolean ignoreZ = offset.z == 0 || offset.z == 15;

        int ox = offset.x - 1;
        int oy = offset.y - 1;
        int oz = offset.z - 1;

        return new AABB(
                worldPosition.getX() - (ignoreX ? 0 : ox),
                worldPosition.getY() - (ignoreY ? 0 : oy),
                worldPosition.getZ() - (ignoreZ ? 0 : oz),
                worldPosition.getX() + (ignoreX ? 1 : 14 - ox),
                worldPosition.getY() + (ignoreY ? 1 : 14 - oy),
                worldPosition.getZ() + (ignoreZ ? 1 : 14 - oz));
    }

    // ------------------------------------------------------------------
    // Forge 能力：唯一区别是门控键取 side.getOpposite()，流体同样不门控
    // ------------------------------------------------------------------

    private void createCapabilities() {
        for (Direction side : Direction.values()) {
            int i = side.get3DDataValue();
            energyCaps[i] = LazyOptional.of(() -> new ForwardingEnergy(side));
            fluidCaps[i] = LazyOptional.of(() -> new ForwardingFluid(side));
            itemCaps[i] = LazyOptional.of(() -> new ForwardingItems(side));
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (side != null) {
            int i = side.get3DDataValue();
            Pocket pocket = getPocket();
            if (pocket != null && pocket.getFlowState(flowStateKey(side)) == PocketSideState.ENERGY) {
                if (cap == ForgeCapabilities.ENERGY) {
                    return energyCaps[i].cast();
                }
                if (cap == ForgeCapabilities.ITEM_HANDLER) {
                    return itemCaps[i].cast();
                }
            }
            if (cap == ForgeCapabilities.FLUID_HANDLER) {
                return fluidCaps[i].cast();
            }
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        for (int i = 0; i < 6; i++) {
            energyCaps[i].invalidate();
            fluidCaps[i].invalidate();
            itemCaps[i].invalidate();
        }
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        createCapabilities();
    }

    /** 把某个面的能量调用转发到 DP 方块外侧那台机器。 */
    private final class ForwardingEnergy implements IEnergyStorage {

        private final Direction side;

        private ForwardingEnergy(Direction side) {
            this.side = side;
        }

        @Nullable
        private IEnergyStorage target() {
            BlockEntity target = findTransferTarget(side);
            if (target == null) {
                return null;
            }
            return target.getCapability(ForgeCapabilities.ENERGY, side).orElse(null);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            IEnergyStorage handler = target();
            return handler == null ? 0 : handler.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            IEnergyStorage handler = target();
            return handler == null ? 0 : handler.extractEnergy(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            IEnergyStorage handler = target();
            return handler == null ? 0 : handler.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            IEnergyStorage handler = target();
            return handler == null ? 0 : handler.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    /** 把某个面的流体调用转发到 DP 方块外侧那台机器（上游不检查面状态）。 */
    private final class ForwardingFluid implements IFluidHandler {

        private final Direction side;

        private ForwardingFluid(Direction side) {
            this.side = side;
        }

        @Nullable
        private IFluidHandler target() {
            BlockEntity target = findTransferTarget(side);
            if (target == null) {
                return null;
            }
            return target.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
        }

        @Override
        public int getTanks() {
            IFluidHandler handler = target();
            return handler == null ? 0 : handler.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            IFluidHandler handler = target();
            return handler == null ? FluidStack.EMPTY : handler.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler handler = target();
            return handler == null ? 0 : handler.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            IFluidHandler handler = target();
            return handler != null && handler.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            IFluidHandler handler = target();
            return handler == null ? 0 : handler.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler handler = target();
            return handler == null ? FluidStack.EMPTY : handler.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler handler = target();
            return handler == null ? FluidStack.EMPTY : handler.drain(maxDrain, action);
        }
    }

    /** 把某个面的物品调用转发到 DP 方块外侧那台机器（上游不打包槽位）。 */
    private final class ForwardingItems implements IItemHandler {

        private final Direction side;

        private ForwardingItems(Direction side) {
            this.side = side;
        }

        @Nullable
        private IItemHandler target() {
            BlockEntity target = findTransferTarget(side);
            if (target == null) {
                return null;
            }
            return target.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
        }

        @Override
        public int getSlots() {
            IItemHandler handler = target();
            return handler == null ? 0 : handler.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            IItemHandler handler = target();
            return handler == null ? ItemStack.EMPTY : handler.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            IItemHandler handler = target();
            return handler == null ? stack : handler.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            IItemHandler handler = target();
            return handler == null ? ItemStack.EMPTY : handler.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            IItemHandler handler = target();
            return handler == null ? 0 : handler.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            IItemHandler handler = target();
            return handler != null && handler.isItemValid(slot, stack);
        }
    }
}
