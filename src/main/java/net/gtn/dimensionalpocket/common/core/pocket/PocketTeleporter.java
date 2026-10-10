package net.gtn.dimensionalpocket.common.core.pocket;

import java.util.function.Function;

import net.gtn.dimensionalpocket.common.core.utils.CoordSet;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;

/**
 * Places a player at an exact coordinate, without any portal search.
 *
 * <p>1.7.10 origin: {@code common/core/pocket/PocketTeleporter.java} (117 lines) - a stripped down
 * {@code Teleporter} whose only real job was {@code placeInPortal} writing the target position into
 * the player's network handler.
 *
 * <p>1.20.1 translation: {@code Teleporter#placeInPortal} does not exist any more. Forge's
 * {@link ITeleporter} replaces it, and the exact landing position is supplied by overriding
 * {@link #getPortalInfo} - that override is the direct 1.20.1 equivalent of the old
 * {@code placeInPortal} body ({@code x + 0.5}, {@code y + 1}, {@code z + 0.5}).
 */
public class PocketTeleporter implements ITeleporter {

    private final ResourceKey<Level> fromDim;
    private final CoordSet target;
    private final float spawnYaw;
    private final float spawnPitch;

    public PocketTeleporter(ResourceKey<Level> fromDim, CoordSet target, float spawnYaw, float spawnPitch) {
        this.fromDim = fromDim;
        this.target = target.copy();
        this.spawnYaw = spawnYaw;
        this.spawnPitch = spawnPitch;
    }

    public ResourceKey<Level> getFromDim() {
        return fromDim;
    }

    public CoordSet getTarget() {
        return target.copy();
    }

    public float getSpawnYaw() {
        return spawnYaw;
    }

    public float getSpawnPitch() {
        return spawnPitch;
    }

    /**
     * The exact block the player materialises on. Mirrors 1.7.10 {@code placeInPortal}'s offsets:
     * centre of the block on x/z, one block above it on y.
     */
    public Vec3 landingPosition() {
        return new Vec3(target.x + 0.5D, target.y + 1.0D, target.z + 0.5D);
    }

    @Override
    public PortalInfo getPortalInfo(Entity entity, ServerLevel destWorld,
            Function<ServerLevel, PortalInfo> defaultPortalInfo) {
        Vec3 pos = landingPosition();
        ModLog.step("teleport", "place", "dim={} pos={} yaw={} pitch={} from={}",
                ModLog.dim(destWorld), ModLog.exact(pos), spawnYaw, spawnPitch, fromDim.location());
        return new PortalInfo(pos, Vec3.ZERO, spawnYaw, spawnPitch);
    }

    @Override
    public boolean playTeleportSound(ServerPlayer player, ServerLevel sourceWorld, ServerLevel destWorld) {
        // 1.7.10 had no sound hook on this path; stay silent so the port behaves like the original.
        return false;
    }

    @Override
    public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw,
            Function<Boolean, Entity> repositionEntity) {
        // false = never try to build a portal at the destination, 1.7.10 never did.
        Entity placed = repositionEntity.apply(false);
        if (placed == null) {
            ModLog.error("teleport", "place", "result=failed entity={} to={}",
                    ModLog.entity(entity), ModLog.dim(destWorld));
            return null;
        }
        placed.resetFallDistance();
        return placed;
    }

    /**
     * Moves a player to {@code toDim} and materialises it at {@link #landingPosition()}.
     *
     * <p>1.7.10 called {@code ServerConfigurationManager#transferPlayerToDimension}; 1.20.1 uses
     * {@code ServerPlayer#changeDimension} for a real dimension change and a plain teleport when the
     * player is already inside the target dimension (the original called {@code placeInPortal}
     * directly in that case).
     */
    public static void transfer(ServerPlayer player, ResourceKey<Level> toDim, PocketTeleporter teleporter) {
        if (player == null) {
            ModLog.deny("teleport", "transfer", "reason=null_player actual=null expected=ServerPlayer");
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            ModLog.deny("teleport", "transfer", "reason=no_server actual=null expected=MinecraftServer");
            return;
        }

        ServerLevel dest = server.getLevel(toDim);
        if (dest == null) {
            ModLog.deny("teleport", "transfer", "reason=unknown_level actual={} expected=registered_level",
                    toDim.location());
            return;
        }

        ResourceKey<Level> from = player.level().dimension();
        Vec3 pos = teleporter.landingPosition();

        if (from.equals(toDim)) {
            player.teleportTo(dest, pos.x, pos.y, pos.z, teleporter.spawnYaw, teleporter.spawnPitch);
            player.resetFallDistance();
            ModLog.step("teleport", "transfer", "mode=same_dim dim={} pos={}", toDim.location(), ModLog.exact(pos));
            return;
        }

        Entity moved = player.changeDimension(dest, teleporter);
        if (moved == null) {
            ModLog.error("teleport", "transfer", "mode=change_dim result=null from={} to={}",
                    from.location(), toDim.location());
        } else {
            ModLog.step("teleport", "transfer", "mode=change_dim from={} to={} pos={}",
                    from.location(), toDim.location(), ModLog.exact(pos));
        }
    }
}
