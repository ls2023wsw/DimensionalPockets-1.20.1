package net.gtn.dimensionalpocket.client.event;

import net.gtn.dimensionalpocket.client.commands.RenderTweakCommand;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Global client tick counter (the animation clock of the particle field) plus two client side
 * interceptions.
 *
 * <p>1.7.10 origin: {@code client/event/ClientEventHandler.java}. All of these are <b>forge bus</b>
 * events in 1.20.1 (the old {@code FMLCommonHandler.instance().bus()} was merged into
 * {@code MinecraftForge.EVENT_BUS}), hence {@code Bus.FORGE}.
 *
 * <p>Changes against 1.7.10:
 * <ul>
 *   <li>{@code MouseEvent} is gone; the "travel staff does nothing inside a pocket" check moved to
 *       {@link InputEvent.InteractionKeyMappingTriggered}.</li>
 *   <li>{@code ItemStack#getUnlocalizedName()} is gone; the staff is matched by its registry name
 *       ({@code enderio:travel_staff} - the exact id depends on the pack, 报告 03 §2.2).</li>
 *   <li>{@code doesGuiPauseGame()} became {@link Screen#isPauseScreen()}, which keeps the upstream
 *       "the field freezes while the game is paused" behaviour.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEventHandler {

    /** 1.7.10: {@code public static int gameTicks}. Widened to {@code long} so it cannot wrap. */
    public static long gameTicks = 0;

    /** EnderIO's travel staff, the one item 1.7.10 explicitly disabled inside a pocket. */
    private static final ResourceLocation ENDERIO_TRAVEL_STAFF = new ResourceLocation("enderio", "travel_staff");

    private static final long TRAVEL_STAFF_WARNING_DELAY_MS = 3000L;
    private static long lastTravelStaffWarningTime = 0L;

    private ClientEventHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (screen == null || !screen.isPauseScreen()) {
            gameTicks++;
        }
    }

    /**
     * 1.7.10 cancelled the mouse press; 1.20.1 cancels the interaction the key mapping would trigger.
     * Only "use item" is relevant for the travel staff.
     */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || !player.isShiftKeyDown()) {
            return;
        }
        if (player.level().dimension() != Reference.POCKET_LEVEL) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return;
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (!ENDERIO_TRAVEL_STAFF.equals(itemId)) {
            return;
        }

        event.setCanceled(true);
        event.setSwingHand(false);
        long now = System.currentTimeMillis();
        if (now > lastTravelStaffWarningTime + TRAVEL_STAFF_WARNING_DELAY_MS) {
            lastTravelStaffWarningTime = now;
            player.displayClientMessage(Component.translatable("functionality.disabled.in.pocket")
                    .withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY), false);
        }
        ModLog.step("ClientEvent", "travel_staff_blocked",
                "reason=inside_pocket item={} player={} result=CANCEL", itemId, player.getScoreboardName());
    }

    /**
     * {@code dptweak} is a pure client command (it only touches client rendering config), so it is
     * registered on the client command dispatcher. This event lives on the <b>forge</b> bus, which is
     * why the handler is here and not in {@code ClientSetup} (a mod bus subscriber would never see it).
     */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        RenderTweakCommand.register(event.getDispatcher());
        ModLog.step("ClientEvent", "register_commands", "commands={} result=OK", RenderTweakCommand.commandNames());
    }
}
