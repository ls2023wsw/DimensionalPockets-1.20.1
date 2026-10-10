package net.gtn.dimensionalpocket.client;

import net.gtn.dimensionalpocket.client.renderer.item.ItemPocketRenderer;
import net.gtn.dimensionalpocket.client.renderer.tile.TileRendererPocket;
import net.gtn.dimensionalpocket.client.renderer.tile.TileRendererPocketWall;
import net.gtn.dimensionalpocket.common.ModBlockEntities;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client side registration entry point: the two block entity renderers and the client setup hook.
 *
 * <p>1.7.10 origin: {@code client/ClientProxy#initClientSide()} / {@code postInitClientSide()}, plus
 * the (dropped) {@code ShaderHelper.initShaders()} call.
 *
 * <p>Everything here is on the <b>mod</b> event bus and guarded by {@code Dist.CLIENT}, so a dedicated
 * server never loads this class.
 *
 * <p><b>Item rendering</b>: 1.7.10 registered it with {@code MinecraftForgeClient.registerItemRenderer}.
 * 1.20.1 has no such call, and Forge 47.4.0 has no {@code RegisterClientExtensionsEvent} either (that
 * event arrived in 1.20.2+). The 1.20.1 hook is
 * {@code Item#initializeClient(Consumer<IClientItemExtensions>)}, implemented on the item itself; the
 * extension object lives in {@link ItemPocketRenderer#EXTENSIONS} and the pocket block item (owned by
 * the block slice) hands it out from there.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    private ClientSetup() {
    }

    /**
     * 1.7.10 bound the two tile entity renderers with
     * {@code ClientRegistry.bindTileEntitySpecialRenderer}; 1.20.1 has no separate registration event
     * for them, they go into {@link EntityRenderersEvent.RegisterRenderers}.
     */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.DIMENSIONAL_POCKET.get(), TileRendererPocket::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WALL_CONNECTOR.get(), TileRendererPocketWall::new);
        ModLog.step("ClientSetup", "register_renderers",
                "pocket={} wallConnector={} result=OK",
                ModBlockEntities.DIMENSIONAL_POCKET.getId(), ModBlockEntities.WALL_CONNECTOR.getId());
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // No MenuScreens / key mappings are needed: the guide book is a pure client Screen opened with
        // Minecraft#setScreen (contract §4.1) and there is no key binding in this port.
        event.enqueueWork(() -> ModLog.step("ClientSetup", "client_setup",
                "itemRenderer=ItemPocketRenderer.EXTENSIONS side=CLIENT result=OK"));
    }
}
