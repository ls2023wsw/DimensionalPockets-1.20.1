package net.gtn.dimensionalpocket.client.event;

import net.gtn.dimensionalpocket.client.renderer.tile.TileRendererPocket;
import net.gtn.dimensionalpocket.common.ModItems;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Local player tick: decides whether the per face indicators / state overlays are drawn.
 *
 * <p>1.7.10 origin: {@code client/event/ClientPlayerTickEventHandler.java}. Everything that needed a
 * different mod was dropped (报告 03 §2.3):
 * <ul>
 *   <li>{@code hideStuffFromNEI()} - NEI does not exist in 1.20.1 and the pocket wall block has no
 *       item form, so there is nothing to hide.</li>
 *   <li>{@code VersionChecker.checkUpToDate()} - the port has no outbound network calls at all.</li>
 *   <li>the "here is the wiki" chat link - {@code Reference} has no wiki URL in the port and the
 *       language file has no {@code info.wiki.*} / {@code click.me} keys.</li>
 * </ul>
 * The one thing that stays is the shared render switch: holding a nether crystal turns the face
 * markers on.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientPlayerTickEventHandler {

    private ClientPlayerTickEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player != Minecraft.getInstance().player) {
            return;
        }

        // 1.7.10: TileRendererPocket.doIndicateSides = equippedItem == ModItems.netherCrystal
        ItemStack equipped = player.getMainHandItem();
        TileRendererPocket.doIndicateSides = !equipped.isEmpty() && equipped.is(ModItems.NETHER_CRYSTAL.get());
    }
}
