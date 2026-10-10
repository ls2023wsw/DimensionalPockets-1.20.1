package net.gtn.dimensionalpocket.common.tileentity;


import net.gtn.dimensionalpocket.common.ModBlockEntities;
import net.gtn.dimensionalpocket.common.ModBlocks;
import net.gtn.dimensionalpocket.common.ModItems;
import net.gtn.dimensionalpocket.common.core.ChunkLoaderHandler;
import net.gtn.dimensionalpocket.common.core.pocket.Pocket;
import net.gtn.dimensionalpocket.common.core.pocket.PocketRegistry;
import net.gtn.dimensionalpocket.common.core.pocket.PocketSideState;
import net.gtn.dimensionalpocket.common.core.pocket.PocketTeleportPreparation;
import net.gtn.dimensionalpocket.common.core.utils.CoordSet;
import net.gtn.dimensionalpocket.common.core.utils.Utils;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 口袋方块（正常维度那一个）的方块实体。1.7.10 对应物 815 行的
 * {@code common/tileentity/TileDimensionalPocket.java}。
 *
 * <p>职责：①把自己当作「口袋的入口」去注册/绑定 {@link Pocket}；②把某个面进来的
 * Forge Energy / 流体 / 物品**穿墙转发**到口袋房间里对应连接器旁边的机器；
 * ③把 {@link Pocket} 的全部数据塞进描述包同步给客户端渲染；④右键准备传送。
 *
 * <p>未移植（按移植决策）：OpenComputers（{@code SidedEnvironment} / {@code @Optional.Interface}）、
 * {@code IWormhole}（{@code de.cdmp.api.wormhole}）、DPAnalytics 的 4 个 *Transfered* 计数、
 * {@code IInventory} / {@code ISidedInventory} 的槽位打包（{@code slot = innerSlot << 3 | side}）
 * —— 1.20.1 用 {@link IItemHandler} 按面返回不同实例，天然不需要打包。
 */
public class TileDimensionalPocket extends TileDP {

    /** 1.7.10: {@code prepareTeleportIntoPocket} 里的 {@code ticksToTake = 15}。 */
    private static final int TELEPORT_PREP_TICKS = 15;


    /** 进口袋的 15 tick 准备过程（服务端）。 */
    @Nullable
    private PocketTeleportPreparation telePrep;

    private final LazyOptional<IEnergyStorage>[] energyCaps = new LazyOptional[6];
    private final LazyOptional<IFluidHandler>[] fluidCaps = new LazyOptional[6];
    private final LazyOptional<IItemHandler>[] itemCaps = new LazyOptional[6];

    public TileDimensionalPocket(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DIMENSIONAL_POCKET.get(), pos, state);
        createCapabilities();
    }

    // ------------------------------------------------------------------
    // tick（1.7.10: updateEntity）
    // ------------------------------------------------------------------

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (telePrep != null && telePrep.doPrepareTick()) {
            telePrep = null;
        }
    }

    // ------------------------------------------------------------------
    // pocket 绑定（1.7.10: getPocket）
    // ------------------------------------------------------------------

    /**
     * 服务端按**自己方块的坐标**在 registry 里反查，找不到就新建一个 Pocket。
     * 客户端返回描述包反序列化出来的副本。TE 里没有任何「口袋索引」字段。
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
        if (level instanceof ServerLevel serverLevel) {
            return PocketRegistry.getOrCreatePocket(serverLevel, getCoordSet());
        }
        ModLog.deny("tile", "get_pocket",
                "reason=not_a_server_level {} {} actual={} expected=ServerLevel",
                ModLog.pos(worldPosition), ModLog.side(level), level.getClass().getName());
        return null;
    }

    // ------------------------------------------------------------------
    // 放置 / 右键
    // ------------------------------------------------------------------

    /**
     * 1.7.10: {@code onBlockAdded} —— 放置分支。
     *
     * <p><b>修掉了上游会崩服的 {@code throw new RuntimeException("YOU DESERVED THIS!")}</b>：
     * 物品 NBT 指向一个 registry 里不存在的口袋（存档被改过 / 数据损坏）时，上游直接崩游戏，
     * 这里改成 {@code ModLog.error} + 安全失败（跳过重新绑定，按新口袋流程继续）。
     *
     * <p>被放置的物品从玩家主手取：1.20.1 的 {@code BlockItem} 在 {@code setPlacedBy} 之后才
     * {@code shrink(1)}，此刻主手还是那一份带 NBT 的栈（与上游 {@code itemStack} 参数同源）。
     */
    public void onBlockAdded(@Nullable Player placer) {
        if (level == null || level.isClientSide()) {
            return;
        }

        ItemStack heldStack = placer == null ? ItemStack.EMPTY : placer.getMainHandItem();

        if (heldStack.hasTag()) {
            CompoundTag itemCompound = heldStack.getTag();
            CoordSet chunkSet = CoordSet.readFromNBT(itemCompound);

            if (chunkSet == null) {
                ModLog.error("tile", "block_added",
                        "reason=malformed_coord_set_nbt {} {} actual={} expected=int[3]",
                        ModLog.pos(worldPosition), ModLog.item(heldStack),
                        itemCompound.contains("coordSet") ? "wrong_type" : "missing");
            } else if (PocketRegistry.getPocket(chunkSet) == null) {
                ModLog.error("tile", "block_added",
                        "reason=item_points_to_unknown_pocket {} {} chunkSet={} expected=known_pocket",
                        ModLog.pos(worldPosition), ModLog.item(heldStack), chunkSet);
            } else {
                // 把同一个 Pocket 重新绑到新位置/新维度：房间内容（箱子、连接器、出生点、面状态）全部保留
                PocketRegistry.updatePocket(chunkSet, level.dimension(), getCoordSet());
                ModLog.step("tile", "block_added", "pocket_rebound {} chunkSet={} dim={} blockCoords={}",
                        ModLog.pos(worldPosition), chunkSet, level.dimension().location(), getCoordSet());

                if (heldStack.hasCustomHoverName()) {
                    customName = heldStack.getHoverName().getString();
                }
            }
        }

        Pocket pocket = getPocket();
        if (pocket == null) {
            ModLog.error("tile", "block_added",
                    "reason=no_pocket_after_placement {} actual=null expected=Pocket", ModLog.pos(worldPosition));
            return;
        }

        pocket.generatePocketRoom(placer == null ? null : placer.getName().getString());
        ChunkLoaderHandler.addPocketToChunkLoader(pocket);
        ModLog.step("tile", "block_added", "pocket_ready {} chunkCoords={} creator={}",
                ModLog.pos(worldPosition), pocket.getChunkCoords(), pocket.getCreator());
    }

    /**
     * 1.7.10: {@code onActivated} —— **右键唯一入口**（上游对潜行与否不作区分）。
     *
     * <ul>
     *   <li>手上有物品且是「口袋扳手」→ 借用 {@link net.gtn.dimensionalpocket.common.items.ItemNetherCrystal} 的逻辑；</li>
     *   <li>手上有任何物品（含扳手）→ 消费右键但不进口袋；</li>
     *   <li>空手 → 起 15 tick 的传送准备。</li>
     * </ul>
     */
    /** 契约要求的右键入口。没有点击上下文时退化为「朝玩家那一面」。 */
    public void onActivated(Player player) {
        onActivated(player, null);
    }

    /**
     * 带点击面的重载。1.20.1 里只有 {@code Block#use} 拿得到 {@code BlockHitResult}，
     * 方块侧把它传进来，服务端就不必重建射线（1.7.10 的 {@code onBlockActivated} 本来就带 side/hitVec）。
     */
    public void onActivated(Player player, @Nullable BlockHitResult hit) {
        if (player == null) {
            return;
        }

        ItemStack equipped = player.getMainHandItem();
        if (!equipped.isEmpty()) {
            if (Utils.isItemPocketWrench(equipped)) {
                ModItems.NETHER_CRYSTAL.get().onItemUseFirst(equipped, buildUseContext(player, hit));
                ModLog.step("tile", "activate", "pocket_wrench_used {} {}", ModLog.player(player), ModLog.pos(worldPosition));
            } else {
                ModLog.trace("tile", "activate", "reason=holding_item {} {} {}",
                        ModLog.player(player), ModLog.pos(worldPosition), ModLog.item(equipped));
            }
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            prepareTeleportIntoPocket(serverPlayer);
        } else {
            // 客户端粒子（上游 UtilsFX.createPlayerStream）属渲染切片 S5，这里不做。
            ModLog.trace("tile", "activate", "client_side_activate {} {}", ModLog.player(player), ModLog.pos(worldPosition));
        }
    }

    /** 用方块侧传来的点击上下文；缺失时退化为「朝玩家那一面」。 */
    private UseOnContext buildUseContext(Player player, @Nullable BlockHitResult hit) {
        if (hit != null && hit.getBlockPos().equals(worldPosition)) {
            return new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        }
        Direction fallback = player.getDirection().getOpposite();
        ModLog.warn("tile", "activate",
                "reason=no_click_context {} actual={} expected={} fallback_face={}",
                ModLog.player(player), hit == null ? "null" : ModLog.pos(hit.getBlockPos()),
                ModLog.pos(worldPosition), fallback);
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(worldPosition), fallback, worldPosition, false));
    }

    /** 1.7.10: {@code prepareTeleportIntoPocket}（客户端粒子部分归 S5）。 */
    public void prepareTeleportIntoPocket(ServerPlayer player) {
        Pocket pocket = getPocket();
        if (pocket == null) {
            ModLog.deny("tile", "prepare_teleport",
                    "reason=no_pocket {} {} actual=null expected=Pocket",
                    ModLog.player(player), ModLog.pos(worldPosition));
            return;
        }
        telePrep = new PocketTeleportPreparation(player, TELEPORT_PREP_TICKS, pocket,
                PocketTeleportPreparation.Direction.INTO_POCKET);
        ModLog.step("tile", "prepare_teleport", "started {} {} ticks={} pocket={}",
                ModLog.player(player), ModLog.pos(worldPosition), TELEPORT_PREP_TICKS, pocket.getChunkCoords());
    }

    // ------------------------------------------------------------------
    // 掉落物（1.7.10: generateItemStackOnRemoval）—— 必须抄全
    // ------------------------------------------------------------------

    /**
     * 生成「拆下来的口袋」。物品 NBT 就是这个方块的**存档身份**：
     * {@code coordSet}（int[3]，口袋的区块坐标）= 口袋索引；{@code display.Name} = 自定义名；
     * {@code display.Lore} 只是展示。
     */
    public ItemStack generateItemStackOnRemoval() {
        Pocket pocket = getPocket();
        if (pocket == null) {
            ModLog.deny("tile", "generate_drop",
                    "reason=no_pocket {} actual=null expected=Pocket", ModLog.pos(worldPosition));
            return ItemStack.EMPTY;
        }

        CoordSet chunkSet = pocket.getChunkCoords();
        if (chunkSet == null) {
            ModLog.deny("tile", "generate_drop",
                    "reason=pocket_without_chunk_coords {} actual=null expected=CoordSet", ModLog.pos(worldPosition));
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(ModBlocks.DIMENSIONAL_POCKET.get());
        chunkSet.writeToNBT(stack.getOrCreateTag());

        CoordSet blockSet = chunkSet.toBlockCoords();
        String creator = pocket.getCreator();
        String creatorLore = creator == null ? null : "Creator: §3§o" + creator;

        // 1.7.10: Utils.generateItem(itemStack, customName, forceCleanName = false, 第一行 lore, creatorLore)
        // （名字写 display.Name，lore 每行前缀灰色；creatorLore 为 null 时该行不写）
        return Utils.generateItem(stack, customName, false,
                "~ Pocket §e" + blockSet.x + "," + blockSet.y + "," + blockSet.z + "§8 ~", creatorLore);
    }


    // ------------------------------------------------------------------
    // 邻居变化（1.7.10: onNeighbourBlockChanged / onNeighbourTileChanged）
    // 由 BlockDimensionalPocket.neighborChanged 派发
    // ------------------------------------------------------------------

    public void onNeighbourBlockChanged() {
        if (level == null || level.isClientSide()) {
            return;
        }
        Pocket pocket = getPocket();
        if (pocket == null) {
            return;
        }
        for (Direction side : Direction.values()) {
            pocket.markConnectorForUpdate(side);
        }
        // 1.20.1 新增的必要动作：邻居换了方块/方块实体后，Forge 的能力缓存（LazyOptional）不会自动失效
        Utils.invalidateCapabilities(level, worldPosition);
    }

    public void onNeighbourTileChanged(BlockPos neighbourPos) {
        if (level == null || level.isClientSide()) {
            return;
        }
        Pocket pocket = getPocket();
        if (pocket == null) {
            return;
        }
        int ox = neighbourPos.getX() - worldPosition.getX();
        int oy = neighbourPos.getY() - worldPosition.getY();
        int oz = neighbourPos.getZ() - worldPosition.getZ();
        for (Direction side : Direction.values()) {
            if (side.getStepX() == ox && side.getStepY() == oy && side.getStepZ() == oz) {
                pocket.markConnectorForUpdate(side);
            }
        }
        Utils.invalidateCapabilities(level, worldPosition);
    }

    // ------------------------------------------------------------------
    // 穿墙转发目标（1.7.10: getFrameConnectorNeighborTileEntity）
    // ------------------------------------------------------------------

    /**
     * 目标 = 口袋维度里「该面的连接器格」再朝房间内退一格。
     *
     * <p>边界检查对应上游的两次 {@code blockExists}；1.20.1 用 {@code ServerLevel#isLoaded}，
     * 目标区块没加载时返回 null（转发方按「没有能力」处理）。
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
        ServerLevel pocketLevel = PocketRegistry.getPocketLevel();
        if (pocketLevel == null) {
            ModLog.denyRepeated("cap.no_pocket_level", 200, "tile", "find_target",
                    "reason=pocket_level_not_loaded actual=null expected=ServerLevel {}", ModLog.face(side));
            return null;
        }

        CoordSet connectorCoords = pocket.getConnectorCoords(side);
        if (connectorCoords == null) {
            return null;
        }
        if (!pocketLevel.isLoaded(connectorCoords.toBlockPos())) {
            return null;
        }
        CoordSet insideCoords = connectorCoords.copy().addDirection(side.getOpposite());
        if (!pocketLevel.isLoaded(insideCoords.toBlockPos())) {
            return null;
        }
        return pocketLevel.getBlockEntity(insideCoords.toBlockPos());
    }

    // ------------------------------------------------------------------
    // Forge 能力（1.7.10: IEnergyHandler / IFluidHandler / ISidedInventory）
    //
    // 门控从「方法内 switch」变成「getCapability 返回空还是非空」：
    //   能量与物品受 PocketSideState.ENERGY 门控，**流体不门控**（上游事实，改了会改变已有存档行为）。
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

    /** 区块重载后必须把能力视图重建回来，否则这个方块会永久「没有能量/流体/物品能力」。 */
    @Override
    public void reviveCaps() {
        super.reviveCaps();
        createCapabilities();
    }

    /** 把某个面的能量调用转发到房间内那台机器。 */
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

    /** 把某个面的流体调用转发到房间内那台机器（上游不检查面状态）。 */
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

    /** 把某个面的物品调用转发到房间内那台机器（上游按面暴露同一个背包）。 */
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
