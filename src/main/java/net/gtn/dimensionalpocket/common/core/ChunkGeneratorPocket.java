package net.gtn.dimensionalpocket.common.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.jetbrains.annotations.Nullable;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.gtn.dimensionalpocket.common.ModBlocks;
import net.gtn.dimensionalpocket.common.core.pocket.Pocket;
import net.gtn.dimensionalpocket.common.core.pocket.PocketRegistry;
import net.gtn.dimensionalpocket.common.core.utils.CoordSet;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Chunk generator of the pocket level: an entirely empty (air only) world whose only content is the
 * hollow 16x16x16 shell of every pocket room mapped to the chunk being generated.
 *
 * <p>1.7.10 origin: {@code common/core/ChunkGeneratorPocket.java} (96 lines). It built a bare
 * {@code new Chunk(worldObj, new Block[] {}, new byte[] {}, x, z)}, filled a 256 entry biome array with
 * {@code biomeID = 99}, called {@code generateSkylightMap()} and left the room shell to
 * {@code Pocket.generatePocketRoom}, which wrote straight into the {@code ExtendedBlockStorage}.
 *
 * <p>1.20.1 translation (report 01 §8.3, contract §4.5):
 * <ul>
 *   <li>{@code IChunkProvider} -&gt; {@link ChunkGenerator} + {@link #CODEC}; the datapack
 *       {@code data/dimensionalpockets/dimension/pocket.json} references the codec as
 *       {@code dimensionalpockets:pocket}.</li>
 *   <li>No manual chunk creation and no {@code generateSkylightMap()}: the 1.20.1 chunk system hands
 *       the generator an empty {@link ChunkAccess} (all sections are pre-allocated by
 *       {@code ChunkAccess}'s constructor) and the light engine owns lighting.</li>
 *   <li>The biome array is gone; the biome comes from {@code biome_source}
 *       ({@code minecraft:fixed} + {@code dimensionalpockets:pocket}).</li>
 *   <li>The room shell is placed here, in {@link #fillFromNoise}: it is the only deterministic hook that
 *       runs before the chunk is handed out and it needs no block updates (the 1.7.10
 *       {@code world.markBlockForUpdate} per block has no 1.20.1 equivalent, and none is needed while
 *       generating).</li>
 *   <li>{@code canSave() = false} has no 1.20.1 equivalent - the pocket level is written to
 *       {@code <world>/dimensions/dimensionalpockets/pocket/} like every other level. The room shell is
 *       deterministic, so it can always be rebuilt from {@code PocketRegistry}.</li>
 * </ul>
 *
 * <p>All "nothing happens here" overrides log through {@link ModLog#trace} so that an unexpectedly empty
 * pocket chunk can be diagnosed without guessing (project rule: no silent returns).
 */
public class ChunkGeneratorPocket extends ChunkGenerator {

    /**
     * Codec of this generator, registered as {@code dimensionalpockets:pocket} (contract §2). The id must
     * match {@code generator.type} in {@code data/dimensionalpockets/dimension/pocket.json}.
     */
    public static final Codec<ChunkGeneratorPocket> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
    ).apply(instance, ChunkGeneratorPocket::new));

    /** Height of the pocket level in blocks. Contract §4.10 pins {@code height = 80} (5 sections). */
    public static final int POCKET_HEIGHT = 80;

    /**
     * Bottom of the pocket level. Must stay {@code 0}: only then {@code sectionY == blockY >> 4} and
     * {@code worldY == chunkCoords.y << 4}, which is what the 1.7.10 room placement assumed.
     */
    public static final int POCKET_MIN_Y = 0;

    /** Edge length of one pocket room in blocks (the room exactly fills one section). */
    public static final int ROOM_SIZE = 16;

    /** Sections the pocket level has: {@code 80 / 16 = 5}, so a room may start at {@code chunkCoords.y} 0..4. */
    public static final int SECTION_COUNT = POCKET_HEIGHT / ROOM_SIZE;

    /** Shell blocks of one room: 6 faces x 14 x 14, edges and corners skipped (report 01 §1.1). */
    public static final int ROOM_SHELL_BLOCKS = 1176;

    /**
     * Noise settings of the dimension json. Only kept because contract §3.7 requires the second
     * constructor; the pocket level is void, so no noise setting is ever read.
     */
    @Nullable
    private final Holder<NoiseGeneratorSettings> settings;

    /** Codec constructor. */
    public ChunkGeneratorPocket(BiomeSource biomeSource) {
        this(biomeSource, null);
    }

    public ChunkGeneratorPocket(BiomeSource biomeSource, @Nullable Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource);
        this.settings = settings;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /**
     * Generates the chunk: no terrain at all, but the hollow shell of every pocket room mapped to this
     * column. Direct port of {@code Pocket.generatePocketRoom} (1.7.10 {@code Pocket.java:130-146}).
     */
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random,
            StructureManager structureManager, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        List<Pocket> rooms = roomsInColumn(chunkPos.x, chunkPos.z);

        if (rooms.isEmpty()) {
            // The normal case: a pocket column is pure air unless PocketRegistry handed it out.
            ModLog.trace("chunkgen", "fill", "reason=no_pocket_in_column column=({},{}) action=air_only",
                    chunkPos.x, chunkPos.z);
            return CompletableFuture.completedFuture(chunk);
        }

        BlockState wall = ModBlocks.DIMENSIONAL_POCKET_WALL.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (Pocket pocket : rooms) {
            CoordSet chunkCoords = pocket.getChunkCoords();
            if (chunkCoords == null) {
                ModLog.deny("chunkgen", "fill",
                        "reason=null_chunk_coords actual=null expected=CoordSet column=({},{})",
                        chunkPos.x, chunkPos.z);
                continue;
            }

            int sectionY = chunkCoords.y;
            if (sectionY < 0 || sectionY >= SECTION_COUNT) {
                ModLog.deny("chunkgen", "fill",
                        "reason=section_out_of_range actual={} expected=0..{} column=({},{}) roomChunk={}",
                        sectionY, SECTION_COUNT - 1, chunkPos.x, chunkPos.z, chunkCoords);
                continue;
            }

            // 1.7.10: worldX = chunkCoords.x * 16, worldY = chunkCoords.y * 16, worldZ = chunkCoords.z * 16.
            // min_y == 0 is what makes sectionY == chunkCoords.y and worldY == sectionY << 4.
            int rootX = chunkPos.getMinBlockX();
            int rootY = sectionY << 4;
            int rootZ = chunkPos.getMinBlockZ();
            int placed = 0;

            for (int x = 0; x < ROOM_SIZE; x++) {
                for (int y = 0; y < ROOM_SIZE; y++) {
                    for (int z = 0; z < ROOM_SIZE; z++) {
                        boolean flagX = x == 0 || x == ROOM_SIZE - 1;
                        boolean flagY = y == 0 || y == ROOM_SIZE - 1;
                        boolean flagZ = z == 0 || z == ROOM_SIZE - 1;

                        // Upstream Pocket.java:138 verbatim (including its comment "almost halves the time"):
                        // keep only the positions where exactly one coordinate is 0 or 15, i.e. the six faces
                        // without their edges and corners -> 6 * 14 * 14 = 1176 blocks.
                        if (!(flagX || flagY || flagZ) || flagX && (flagY || flagZ) || flagY && flagZ) {
                            continue;
                        }

                        chunk.setBlockState(cursor.set(rootX + x, rootY + y, rootZ + z), wall, false);
                        placed++;
                    }
                }
            }

            ModLog.step("chunkgen", "room",
                    "column=({},{}) sectionY={} root=({},{},{}) placed={} expected={} block={}",
                    chunkPos.x, chunkPos.z, sectionY, rootX, rootY, rootZ, placed, ROOM_SHELL_BLOCKS, ModLog.st(wall));
            ModLog.expect(placed == ROOM_SHELL_BLOCKS, "chunkgen", "room",
                    "reason=shell_block_count actual={} expected={} column=({},{}) sectionY={}",
                    placed, ROOM_SHELL_BLOCKS, chunkPos.x, chunkPos.z, sectionY);
        }

        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * Every pocket room mapped to this chunk column.
     *
     * <p>{@link PocketRegistry#getPocketForColumn(int, int)} is the fast path, but report 01 §3.4 (its last
     * line) is a hard conclusion: one column can hold two rooms at different {@code chunkCoords.y} - the
     * first pocket of a world is allocated with {@code y = 0}, every later one with {@code y = 4}, and the
     * allocation spiral returns to the origin column (the report's own derived sequence ends with the 5th
     * pocket at {@code (0,4,0)} while the 1st sits at {@code (0,0,0)}). A single-Pocket lookup would
     * silently drop one of the two shells, so the column is swept over {@link PocketRegistry#allPockets()}
     * as well.
     */
    private static List<Pocket> roomsInColumn(int chunkX, int chunkZ) {
        List<Pocket> rooms = new ArrayList<>(2);
        Set<CoordSet> seen = new HashSet<>();

        addRoom(rooms, seen, PocketRegistry.getPocketForColumn(chunkX, chunkZ), chunkX, chunkZ);

        Collection<Pocket> all = PocketRegistry.allPockets();
        if (all == null) {
            // Repeated, not plain deny: this is evaluated for every generated chunk.
            ModLog.denyRepeated("chunkgen.rooms.null_registry", 200, "chunkgen", "rooms",
                    "reason=null_pocket_registry actual=null expected=Collection column=({},{})", chunkX, chunkZ);
            return rooms;
        }
        for (Pocket pocket : all) {
            addRoom(rooms, seen, pocket, chunkX, chunkZ);
        }
        return rooms;
    }

    /** Adds {@code pocket} to {@code rooms} if it is a room of the given column and was not added yet. */
    private static void addRoom(List<Pocket> rooms, Set<CoordSet> seen, Pocket pocket, int chunkX, int chunkZ) {
        if (pocket == null) {
            ModLog.trace("chunkgen", "rooms", "reason=null_pocket_in_registry column=({},{})", chunkX, chunkZ);
            return;
        }

        CoordSet chunkCoords = pocket.getChunkCoords();
        if (chunkCoords == null) {
            // trace, not deny: this is evaluated for every pocket of every generated chunk, and a registry
            // entry without chunk coords is a registry problem rather than a per-chunk decision.
            ModLog.trace("chunkgen", "rooms", "reason=null_chunk_coords column=({},{})", chunkX, chunkZ);
            return;
        }

        if (chunkCoords.x != chunkX || chunkCoords.z != chunkZ) {
            return;
        }

        // Copy on purpose: the set must not follow a later mutation of the pocket's own CoordSet.
        if (seen.add(chunkCoords.copy())) {
            rooms.add(pocket);
        } else {
            ModLog.trace("chunkgen", "rooms", "reason=room_already_collected column=({},{}) room={}",
                    chunkX, chunkZ, chunkCoords);
        }
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState random,
            ChunkAccess chunk) {
        // 1.7.10 had no surface pass: the provider only ever produced air.
        ModLog.trace("chunkgen", "buildSurface", "reason=no_surface actual=none expected=none column=({},{})",
                chunk.getPos().x, chunk.getPos().z);
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomeManager,
            StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        // 1.7.10 generated no caves or canyons in the pocket level.
        ModLog.trace("chunkgen", "applyCarvers", "reason=no_carvers actual=none expected=none step={} column=({},{})",
                step, chunk.getPos().x, chunk.getPos().z);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // Upstream BiomeHelper disabled spawning completely (empty spawnable list, spawning chance 0),
        // and the pocket biome json has empty spawners.
        ModLog.trace("chunkgen", "spawnOriginalMobs",
                "reason=no_mob_spawning actual=none expected=none column=({},{})",
                region.getCenter().x, region.getCenter().z);
    }

    @Override
    public int getGenDepth() {
        // Height of the level in blocks (384 for the vanilla overworld). 1.7.10 had no equivalent, its
        // world height was always 256.
        ModLog.trace("chunkgen", "getGenDepth", "height={} sections={}", POCKET_HEIGHT, SECTION_COUNT);
        return POCKET_HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        // The pocket level has no water, so sea level is meaningless; report 01 §8.3 pins 0.
        ModLog.trace("chunkgen", "getSeaLevel", "sea_level={} reason=no_water", POCKET_MIN_Y);
        return POCKET_MIN_Y;
    }

    @Override
    public int getMinY() {
        ModLog.trace("chunkgen", "getMinY", "min_y={}", POCKET_MIN_Y);
        return POCKET_MIN_Y;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        // No terrain, so the bottom of the world is the honest answer for a column of pure air.
        ModLog.trace("chunkgen", "getBaseHeight", "reason=no_terrain actual={} expected={} x={} z={} type={}",
                POCKET_MIN_Y, level.getMinBuildHeight(), x, z, type);
        return POCKET_MIN_Y;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        // Same reasoning as getBaseHeight, but the column is materialised as air instead of an empty
        // array: an empty array would throw as soon as anything samples it.
        ModLog.trace("chunkgen", "getBaseColumn", "reason=no_terrain actual=air expected=air x={} z={} height={}",
                x, z, level.getHeight());
        BlockState[] column = new BlockState[level.getHeight()];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        return new NoiseColumn(level.getMinBuildHeight(), column);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState random, BlockPos pos) {
        // 1.7.10 had no debug screen hook for chunk providers; nothing to report.
        ModLog.trace("chunkgen", "addDebugScreenInfo", "reason=no_debug_info actual=none expected=none pos={}",
                ModLog.pos(pos));
    }

    @Override
    public WeightedRandomList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome,
            StructureManager structureManager, MobCategory category, BlockPos pos) {
        // 1.7.10: getPossibleCreatures() always returned an empty list.
        ModLog.trace("chunkgen", "getMobsAt", "reason=no_spawners actual=none expected=none category={} pos={}",
                category, ModLog.pos(pos));
        return WeightedRandomList.create();
    }

    @Override
    public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel level, HolderSet<Structure> targets,
            BlockPos pos, int radius, boolean skipExistingChunks) {
        // 1.7.10: findClosestStructure() always returned null.
        ModLog.trace("chunkgen", "findNearestMapStructure",
                "reason=no_structures actual=null expected=null pos={} radius={} targets={}",
                ModLog.pos(pos), radius, targets.size());
        return null;
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState state,
            StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager templateManager) {
        // 1.7.10: recreateStructures() was empty, no structure ever generated in the pocket level.
        ModLog.trace("chunkgen", "createStructures", "reason=no_structures actual=none expected=none column=({},{})",
                chunk.getPos().x, chunk.getPos().z);
    }

    @Override
    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk) {
        // Nothing was ever created, so there is nothing to reference.
        ModLog.trace("chunkgen", "createReferences", "reason=no_structures actual=none expected=none column=({},{})",
                chunk.getPos().x, chunk.getPos().z);
    }
}
