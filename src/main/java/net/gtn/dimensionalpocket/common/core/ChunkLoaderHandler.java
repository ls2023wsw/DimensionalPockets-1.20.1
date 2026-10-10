package net.gtn.dimensionalpocket.common.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.gtn.dimensionalpocket.common.core.pocket.Pocket;
import net.gtn.dimensionalpocket.common.core.pocket.PocketRegistry;
import net.gtn.dimensionalpocket.common.core.utils.CoordSet;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Keeps the chunk of every existing pocket room loaded even when nobody is inside it.
 *
 * <p>1.7.10 origin: {@code common/core/ChunkLoaderHandler.java} (157 lines) - a
 * {@code ForgeChunkManager.LoadingCallback} that requested one {@code Type.NORMAL} ticket per chunk
 * column (the column key was the room's chunk coords with {@code y} forced to 0), stored the rooms of
 * that column in {@code TicketWrapper.loadedRooms} and released the ticket once the last room was gone.
 *
 * <p>1.20.1 translation (contract §4.6, report 01 §10.4): {@code ForgeChunkManager} is not used at all.
 * {@link ServerLevel#setChunkForced} is vanilla, only knows about (x, z) and persists itself to
 * {@code <world>/forcedchunks}, therefore:
 * <ul>
 *   <li>the ticket modData (the column {@link CoordSet} written into the ticket) is gone - a forced chunk
 *       is already keyed by the column only, so rooms that share a column share the forced chunk
 *       automatically;</li>
 *   <li>the loaded range is unchanged: exactly one chunk per pocket room (the room's own column), never a
 *       3x3 neighbourhood;</li>
 *   <li>upstream's {@code ticketsLoaded} / {@code revalidateTicket} have no counterpart: vanilla reloads
 *       forced chunks on its own after a restart. The in-memory bookkeeping below only exists to decide
 *       when a column may be un-forced again, and is rebuilt by
 *       {@link PocketRegistry#initChunkLoading()} on every server start.</li>
 * </ul>
 *
 * <p>Gated by {@link DPConfig#keepPocketRoomsChunkLoaded()}, exactly like the 1.7.10
 * {@code Reference.KEEP_POCKET_ROOMS_CHUNK_LOADED} flag.
 */
public final class ChunkLoaderHandler {

    private ChunkLoaderHandler() {
    }

    /**
     * Column key ({@link ChunkPos#asLong(int, int)}) to the chunk coords (y included) of the rooms known
     * to live in that column. Same role as the 1.7.10 {@code ticketMap} + {@code loadedRooms}.
     */
    private static final Map<Long, Set<CoordSet>> LOADED_ROOMS = new HashMap<>();

    /**
     * Forces the chunk of {@code pocket}'s column to stay loaded. Called when a pocket is created (or on
     * every server start through {@link PocketRegistry#initChunkLoading()}).
     */
    public static void addPocketToChunkLoader(Pocket pocket) {
        if (!DPConfig.keepPocketRoomsChunkLoaded()) {
            ModLog.deny("chunkload", "add",
                    "reason=config_disabled actual=false expected=true option=general.keepPocketRoomsChunkLoaded");
            return;
        }

        if (pocket == null) {
            ModLog.deny("chunkload", "add", "reason=null_pocket actual=null expected=Pocket");
            return;
        }

        CoordSet chunkCoords = pocket.getChunkCoords();
        if (chunkCoords == null) {
            ModLog.deny("chunkload", "add", "reason=null_chunk_coords actual=null expected=CoordSet");
            return;
        }

        ServerLevel pocketLevel = PocketRegistry.getPocketLevel();
        if (pocketLevel == null) {
            ModLog.deny("chunkload", "add",
                    "reason=pocket_level_not_loaded actual=null expected=ServerLevel column=({},{})",
                    chunkCoords.x, chunkCoords.z);
            return;
        }

        long columnKey = ChunkPos.asLong(chunkCoords.x, chunkCoords.z);
        Set<CoordSet> rooms = LOADED_ROOMS.computeIfAbsent(columnKey, key -> new HashSet<>());
        boolean firstRoomInColumn = rooms.isEmpty();

        // Copy on purpose: the bookkeeping must not follow a later mutation of the pocket's own CoordSet.
        if (!rooms.add(chunkCoords.copy())) {
            // 1.7.10 logged "already marked as loaded" and refreshed the already existing ticket.
            ModLog.trace("chunkload", "add", "reason=room_already_marked column=({},{}) room={} rooms={}",
                    chunkCoords.x, chunkCoords.z, chunkCoords, rooms.size());
            return;
        }

        if (firstRoomInColumn) {
            pocketLevel.setChunkForced(chunkCoords.x, chunkCoords.z, true);
            ModLog.step("chunkload", "add", "column=({},{}) rooms={} dim={} action=force_chunk",
                    chunkCoords.x, chunkCoords.z, rooms.size(), ModLog.dim(pocketLevel));
        } else {
            // A forced chunk is keyed by the column, so the second room in the same column is free.
            ModLog.trace("chunkload", "add", "column=({},{}) rooms={} room={} action=share_forced_chunk",
                    chunkCoords.x, chunkCoords.z, rooms.size(), chunkCoords);
        }
    }

    /**
     * Drops {@code pocket} from the bookkeeping and un-forces its column once no other room needs it.
     * Called when a pocket is removed from the world.
     */
    public static void removePocketFromChunkLoader(Pocket pocket) {
        if (pocket == null) {
            ModLog.deny("chunkload", "remove", "reason=null_pocket actual=null expected=Pocket");
            return;
        }

        CoordSet chunkCoords = pocket.getChunkCoords();
        if (chunkCoords == null) {
            ModLog.deny("chunkload", "remove", "reason=null_chunk_coords actual=null expected=CoordSet");
            return;
        }

        long columnKey = ChunkPos.asLong(chunkCoords.x, chunkCoords.z);
        Set<CoordSet> rooms = LOADED_ROOMS.get(columnKey);
        if (rooms == null) {
            // 1.7.10: "Something tried to remove a loaded pocket from a chunk that was never loaded before".
            ModLog.deny("chunkload", "remove",
                    "reason=column_not_tracked actual=null expected=tracked_column column=({},{}) keepLoaded={}",
                    chunkCoords.x, chunkCoords.z, DPConfig.keepPocketRoomsChunkLoaded());
            return;
        }

        if (!rooms.remove(chunkCoords.copy())) {
            ModLog.deny("chunkload", "remove",
                    "reason=room_not_marked actual=absent expected=marked column=({},{}) room={} rooms={}",
                    chunkCoords.x, chunkCoords.z, chunkCoords, rooms.size());
        }

        if (!rooms.isEmpty()) {
            ModLog.trace("chunkload", "remove", "column=({},{}) rooms={} room={} action=keep_forced",
                    chunkCoords.x, chunkCoords.z, rooms.size(), chunkCoords);
            return;
        }

        LOADED_ROOMS.remove(columnKey);

        ServerLevel pocketLevel = PocketRegistry.getPocketLevel();
        if (pocketLevel == null) {
            ModLog.deny("chunkload", "remove",
                    "reason=pocket_level_not_loaded actual=null expected=ServerLevel column=({},{}) action=skip_unforce",
                    chunkCoords.x, chunkCoords.z);
            return;
        }

        pocketLevel.setChunkForced(chunkCoords.x, chunkCoords.z, false);
        ModLog.step("chunkload", "remove", "column=({},{}) rooms=0 dim={} action=unforce_chunk",
                chunkCoords.x, chunkCoords.z, ModLog.dim(pocketLevel));
    }

    /**
     * Server stop: forget the in-memory bookkeeping only. The forced chunks themselves are left alone on
     * purpose - vanilla persists them in {@code <world>/forcedchunks} and reloads them after a restart,
     * which is exactly what the 1.7.10 ticket mechanism used to do through its ticket modData.
     */
    public static void clearTicketMap() {
        int columns = LOADED_ROOMS.size();
        LOADED_ROOMS.clear();
        ModLog.step("chunkload", "clear",
                "columns={} action=forget_in_memory_map note=forced_chunks_stay_persisted", columns);
    }
}
