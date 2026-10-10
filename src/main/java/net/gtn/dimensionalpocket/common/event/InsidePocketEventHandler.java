package net.gtn.dimensionalpocket.common.event;

import net.gtn.dimensionalpocket.common.lib.Reference;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Stops ender based teleporting from working inside the pocket dimension.
 *
 * <p>1.7.10 origin: {@code common/event/InsidePocketEventHandler.java} - cancelled
 * {@code EnderTeleportEvent} whenever the entity stood in the pocket dimension.
 *
 * <p>1.20.1: {@code EnderTeleportEvent} no longer exists, Forge split it into
 * {@link EntityTeleportEvent.EnderPearl} and {@link EntityTeleportEvent.EnderEntity}. Both are
 * handled here so the original behaviour (pearls <i>and</i> endermen) is preserved.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class InsidePocketEventHandler {

    @SubscribeEvent
    public static void onEnderPearlTeleport(EntityTeleportEvent.EnderPearl event) {
        if (isInsidePocket(event)) {
            event.setCanceled(true);
            ModLog.deny("event", "block_ender_teleport", "kind=ender_pearl entity={} reason=inside_pocket",
                    ModLog.entity(event.getEntity()));
        }
    }

    @SubscribeEvent
    public static void onEnderEntityTeleport(EntityTeleportEvent.EnderEntity event) {
        if (isInsidePocket(event)) {
            event.setCanceled(true);
            ModLog.deny("event", "block_ender_teleport", "kind=ender_entity entity={} reason=inside_pocket",
                    ModLog.entity(event.getEntity()));
        }
    }

    private static boolean isInsidePocket(EntityTeleportEvent event) {
        return event.getEntity().level().dimension().equals(Reference.POCKET_LEVEL);
    }
}
