package net.gtn.dimensionalpocket.common.block.event;

import net.gtn.dimensionalpocket.common.ModBlocks;
import net.gtn.dimensionalpocket.common.core.DPConfig;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps the pocket room shell indestructible.
 *
 * <p>1.7.10 origin: {@code common/block/event/BlockEventHandler.java} - cancelled
 * {@code BlockEvent.BreakEvent} for the pocket wall unless the config allowed breaking it.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class BlockEventHandler {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        BlockState state = event.getState();
        if (state.getBlock() != ModBlocks.DIMENSIONAL_POCKET_WALL.get()) {
            return;
        }

        if (DPConfig.canBreakPocketWallInCreative()) {
            ModLog.trace("block", "break_guard", "result=allowed reason=config_canBreakPocketWallInCreative pos={}",
                    ModLog.pos(event.getPos()));
            return;
        }

        event.setCanceled(true);
        ModLog.deny("block", "break_guard", "result=denied reason=pocket_wall_protected pos={} player={}",
                ModLog.pos(event.getPos()), ModLog.player(event.getPlayer()));
    }
}
