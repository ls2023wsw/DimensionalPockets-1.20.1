package net.gtn.dimensionalpocket.common;

import net.gtn.dimensionalpocket.common.core.DPConfig;
import net.gtn.dimensionalpocket.common.core.utils.Utils;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 服务端代理 + 事件宿主。
 *
 * <p>1.7.10 对应物 {@code common/CommonProxy.java}。删掉的：{@code IGuiHandler} 两方法
 * （本 mod 没有服务端容器，指南书是纯客户端 Screen）、{@code registerTileEntities()}
 * （→ {@link ModBlockEntities}）、{@code InterModConfigHandler.initComms()}
 * （Thaumcraft / TConstruct 互操作，按移植决策丢弃）、{@code CraftingEventHandler} 注册
 * （纯 analytics，整体丢弃）。
 *
 * <p>客户端侧由 S5 的 {@code ClientProxy} 继承本类。
 */
public class CommonProxy {

    /** 1.7.10: {@code CommonProxy.GIVEN_INFO_BOOK}。只发一次信息书的持久标记键。 */
    private static final String GIVEN_INFO_BOOK = "givenInfoBook";

    /**
     * 1.7.10: {@code @SubscribeEvent onEntityJoin(EntityJoinWorldEvent)}。
     *
     * <p>1.20.1 事件名是 {@code EntityJoinLevelEvent}；上游靠 {@code world.isRemote} 守卫，
     * 这里用 {@code level.isClientSide()}。
     */
    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        checkIfSpawnWithBook(player);
    }

    /**
     * 1.7.10: {@code CommonProxy.checkIfSpawnWithBook}。
     *
     * <p>1.7.10 走 {@code Utils.getPlayerPersistTag(player)}（= {@code PlayerPersisted → modId} 子标签）；
     * 1.20.1 由 S1 的 {@link Utils#getPlayerPersistTag(Player)} 提供同一层语义。
     */
    protected static void checkIfSpawnWithBook(Player player) {
        if (!DPConfig.shouldSpawnWithBook()) {
            ModLog.deny("proxy", "spawn_book",
                    "reason=disabled_by_config actual=false expected=config shouldSpawnWithBook=true {} {}",
                    ModLog.player(player), ModLog.dim(player.level()));
            return;
        }

        CompoundTag persistTag = Utils.getPlayerPersistTag(player);
        if (persistTag == null) {
            ModLog.deny("proxy", "spawn_book",
                    "reason=no_persist_tag actual=null expected=CompoundTag {} {}",
                    ModLog.player(player), ModLog.dim(player.level()));
            return;
        }

        if (persistTag.getBoolean(GIVEN_INFO_BOOK)) {
            ModLog.trace("proxy", "spawn_book", "reason=already_given key={} {}",
                    GIVEN_INFO_BOOK, ModLog.player(player));
            return;
        }

        ItemStack infoBook = new ItemStack(ModItems.INFO_BOOK.get());
        if (!player.getInventory().add(infoBook)) {
            // 1.7.10: 背包塞不下就掉一个 EntityItem 并 delayBeforeCanPickup = 0；
            // 1.20.1 新建的 ItemEntity 默认 pickupDelay = 10，必须显式清零才是同一行为。
            ItemEntity dropped = player.drop(infoBook, false);
            if (dropped != null) {
                dropped.setNoPickUpDelay();
            }
            ModLog.step("proxy", "spawn_book", "inventory_full_dropped {} {}",
                    ModLog.player(player), ModLog.item(infoBook));
        } else {
            ModLog.step("proxy", "spawn_book", "given {} {}", ModLog.player(player), ModLog.item(infoBook));
        }

        persistTag.putBoolean(GIVEN_INFO_BOOK, true);
    }
}
