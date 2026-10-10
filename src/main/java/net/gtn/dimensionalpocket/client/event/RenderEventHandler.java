package net.gtn.dimensionalpocket.client.event;

import net.gtn.dimensionalpocket.common.lib.Reference;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Marks "a GUI is currently being rendered" so the particle field stays quiet inside GUIs.
 *
 * <p>1.7.10 origin: {@code client/event/RenderEventHandler.java}
 * ({@code GuiScreenEvent.DrawScreenEvent.Pre/Post}). The 1.20.1 names are
 * {@link ScreenEvent.Render.Pre} / {@link ScreenEvent.Render.Post} (报告 03 §2.4).
 *
 * <p>The flag is only half of the signal: the block entity renderers additionally check
 * {@code Minecraft.getInstance().screen != null}, which is the reliable test on the 1.20.1 item
 * rendering path. Both are kept, exactly as the mapping report recommends.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RenderEventHandler {

    /** 1.7.10: {@code public static boolean isRenderingGUI}. */
    public static boolean isRenderingGUI = false;

    private RenderEventHandler() {
    }

    @SubscribeEvent
    public static void preGuiRenderTick(ScreenEvent.Render.Pre event) {
        isRenderingGUI = true;
    }

    @SubscribeEvent
    public static void postGuiRenderTick(ScreenEvent.Render.Post event) {
        isRenderingGUI = false;
    }
}
