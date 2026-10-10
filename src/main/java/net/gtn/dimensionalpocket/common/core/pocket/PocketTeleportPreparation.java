package net.gtn.dimensionalpocket.common.core.pocket;

import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * A short countdown that runs before the actual teleport happens, so the client can play its
 * stream effect while the player is still standing where they were.
 *
 * <p>1.7.10 origin: {@code common/core/pocket/PocketTeleportPreparation.java} (47 lines). The
 * countdown is started with {@code ticksToTake = 15} by
 * {@code TileDimensionalPocket#prepareTeleportIntoPocket}.
 */
public class PocketTeleportPreparation {

    public enum Direction {
        INTO_POCKET,
        OUT_OF_POCKET
    }

    private int ticksBeforeTeleport;
    private final Player player;
    private final Pocket pocket;
    private final Direction direction;

    public PocketTeleportPreparation(Player player, int ticksBeforeTeleport, Pocket pocket, Direction direction) {
        this.player = player;
        this.ticksBeforeTeleport = ticksBeforeTeleport;
        this.pocket = pocket;
        this.direction = direction;
    }

    public Direction getDirection() {
        return direction;
    }

    public int getTicksBeforeTeleport() {
        return ticksBeforeTeleport;
    }

    /**
     * @return true when this preparation is finished and can be dropped by the caller.
     */
    public boolean doPrepareTick() {
        if (ticksBeforeTeleport < 0) {
            ModLog.trace("teleport", "prepare", "result=finished reason=already_done actual={} expected=0_or_more",
                    ticksBeforeTeleport);
            return true;
        }

        ticksBeforeTeleport--;

        if (ticksBeforeTeleport <= 0) {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                ModLog.deny("teleport", "prepare", "reason=not_server_player actual={} expected=ServerPlayer direction={}",
                        player == null ? "null" : player.getClass().getName(), direction);
                return true;
            }

            if (direction == Direction.INTO_POCKET) {
                pocket.teleportTo(serverPlayer);
            } else {
                pocket.teleportFrom(serverPlayer);
            }
            return true;
        }

        return false;
    }
}
