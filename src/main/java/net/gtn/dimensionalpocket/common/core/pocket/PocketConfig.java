package net.gtn.dimensionalpocket.common.core.pocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import net.gtn.dimensionalpocket.common.core.utils.CoordSet;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * {@link PocketRegistry} 的磁盘 IO 层：用 Gson 把 {@code backLinkMap} 与 {@code PocketGenParameters}
 * 读写到 {@code <worldDir>/dimpockets/*.json}（纯文本 JSON，**没有 GZIP / Base64**，报告 01 §0.1）。
 *
 * <p>1.7.10 origin: {@code net.gtn.dimensionalpocket.common.core.pocket.PocketConfig}。1.20.1 落地（报告 01 §4.4）：
 * <ul>
 *   <li>{@code server.isSinglePlayer()} / {@code getFolderName()} / {@code getFile()} 三个 API 在 1.13+ 全部移除，
 *       改用 {@code server.getWorldPath(LevelResource.ROOT)}（单人自动是 {@code saves/<世界名>}，
 *       服务器是 {@code level-name}，与上游语义一致）。</li>
 *   <li>{@code FileWriter}/{@code FileReader} -&gt; {@code Files.newBufferedWriter/newBufferedReader(..., UTF_8)}。</li>
 *   <li>{@code int blockDim} -&gt; 维度 key 字符串（字段名仍叫 {@code blockDim}），同时能读老存档里的数字 id。</li>
 *   <li>顶层仍是**数组**（{@code Pocket[]}），元素顺序无意义。</li>
 *   <li>{@code loadBackLinkMap()} 读失败时**抛异常中止开服**，不静默返回空表（否则新口袋会被分配到 (0,0,0)
 *       并覆盖旧房间）。文件不存在 / 空文件（新世界）不算失败。</li>
 * </ul>
 */
public final class PocketConfig {

    /** 老存档里的数字维度 id -&gt; 维度 key。 */
    private static final TypeAdapter<ResourceKey<Level>> DIMENSION_KEY_ADAPTER = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, ResourceKey<Level> value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.value(value.location().toString());
        }

        @Override
        public ResourceKey<Level> read(JsonReader in) throws IOException {
            JsonToken token = in.peek();
            if (token == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            if (token == JsonToken.NUMBER) {
                return Pocket.dimensionKeyFromLegacyId(in.nextInt());
            }

            String value = in.nextString();
            ResourceLocation location = ResourceLocation.tryParse(value);
            if (location == null) {
                ModLog.warn("pocket_config", "read_block_dim",
                        "reason=unparsable_resource_location actual={} expected=namespace:path fallback=minecraft:overworld", value);
                return Level.OVERWORLD;
            }
            return ResourceKey.create(Registries.DIMENSION, location);
        }
    };

    private static final TypeAdapterFactory DIMENSION_KEY_FACTORY = new TypeAdapterFactory() {
        @Override
        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (!ResourceKey.class.isAssignableFrom(type.getRawType())) {
                return null;
            }
            return (TypeAdapter<T>) DIMENSION_KEY_ADAPTER;
        }
    };

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapterFactory(DIMENSION_KEY_FACTORY)
            .create();

    private PocketConfig() {}

    /**
     * 解析 {@code <worldDir>/dimpockets/<fileName>.json}，不存在就建目录 + 建空文件（上游行为）。
     */
    private static Path resolveFile(String fileName) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("Dimensional Pockets: no server instance while resolving " + fileName);
        }

        Path file = server.getWorldPath(LevelResource.ROOT)
                .resolve(Reference.SAVE_DIR)
                .resolve(fileName + ".json");

        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                Files.createFile(file);
                ModLog.step("pocket_config", "create_file", "file={} action=created_empty", file);
            }
        } catch (IOException e) {
            ModLog.fail("pocket_config", "create_file", e, "file={} reason=io_error expected=created_or_existing", file);
            throw new IllegalStateException("Dimensional Pockets: could not create " + file, e);
        }

        return file;
    }

    public static void saveBackLinkMap(Map<CoordSet, Pocket> backLinkMap) {
        Pocket[] tempArray = backLinkMap == null ? new Pocket[0] : backLinkMap.values().toArray(new Pocket[0]);

        try {
            Path registryFile = resolveFile(Reference.FILE_TELEPORT_REGISTRY);
            try (BufferedWriter writer = Files.newBufferedWriter(registryFile, StandardCharsets.UTF_8)) {
                GSON.toJson(tempArray, writer);
                writer.flush();
            }
            ModLog.step("pocket_config", "save_registry", "file={} pockets={}", registryFile, tempArray.length);
        } catch (Exception e) {
            // 写盘失败不中止游戏（上游同样只记 severe），但必须留痕
            ModLog.fail("pocket_config", "save_registry", e, "reason=write_error expected=written entries={}", tempArray.length);
        }
    }

    /**
     * 读回整张注册表。
     *
     * @throws IllegalStateException 文件存在但读不动 / 解析失败（必须中止开服，报告 01 §4.4）
     */
    public static Map<CoordSet, Pocket> loadBackLinkMap() {
        Path registryFile = resolveFile(Reference.FILE_TELEPORT_REGISTRY);

        Pocket[] tempArray;
        try (BufferedReader reader = Files.newBufferedReader(registryFile, StandardCharsets.UTF_8)) {
            tempArray = GSON.fromJson(reader, Pocket[].class);
        } catch (Exception e) {
            ModLog.fail("pocket_config", "load_registry", e, "file={} reason=read_or_parse_error expected=readable_json", registryFile);
            throw new IllegalStateException("Dimensional Pockets: could not read the pocket registry " + registryFile
                    + " - refusing to start with an empty registry", e);
        }

        Map<CoordSet, Pocket> backLinkMap = new HashMap<>();
        if (tempArray == null) {
            // 新世界：getConfig 刚建出来的 0 字节文件，Gson 解析成 null（上游同样容忍）
            ModLog.step("pocket_config", "load_registry", "file={} pockets=0 action=empty_file_new_world", registryFile);
            return backLinkMap;
        }

        int skipped = 0;
        for (Pocket link : tempArray) {
            if (link == null) {
                skipped++;
                ModLog.deny("pocket_config", "load_registry", "reason=null_entry actual=null expected=pocket action=skipped");
                continue;
            }
            CoordSet chunkCoords = link.getChunkCoords();
            if (chunkCoords == null) {
                // 上游 bug 2 写出来的存档可能没有 chunkCoords：不能拿它当键
                skipped++;
                ModLog.deny("pocket_config", "load_registry",
                        "reason=chunk_coords_missing actual=null expected=int[3] action=skipped blockDim={} blockCoords={}",
                        link.getBlockDim(), link.getBlockCoords());
                continue;
            }
            backLinkMap.put(chunkCoords, link);
        }

        ModLog.step("pocket_config", "load_registry", "file={} entries={} pockets={} skipped={}",
                registryFile, tempArray.length, backLinkMap.size(), skipped);
        return backLinkMap;
    }

    public static void savePocketGenParams(PocketGenParameters pocketGenParameters) {
        try {
            Path dataFile = resolveFile(Reference.FILE_POCKET_GEN_PARAMS);
            try (BufferedWriter writer = Files.newBufferedWriter(dataFile, StandardCharsets.UTF_8)) {
                GSON.toJson(pocketGenParameters, writer);
                writer.flush();
            }
            ModLog.step("pocket_config", "save_gen_params", "file={} params={}", dataFile, pocketGenParameters);
        } catch (Exception e) {
            ModLog.fail("pocket_config", "save_gen_params", e, "reason=write_error expected=written");
        }
    }

    /**
     * 读回分配游标。读不动时**返回全新默认值**（上游行为：重置为 {@code (0,0,0)} + {@code NORTH}），
     * 因为螺旋分配会跳过已被占用的位置，重置不会覆盖已有房间。
     */
    public static PocketGenParameters loadPocketGenParams() {
        try {
            Path dataFile = resolveFile(Reference.FILE_POCKET_GEN_PARAMS);
            try (BufferedReader dataReader = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
                PocketGenParameters params = GSON.fromJson(dataReader, PocketGenParameters.class);
                if (params == null) {
                    ModLog.step("pocket_config", "load_gen_params", "file={} action=empty_file_new_world", dataFile);
                    return new PocketGenParameters();
                }
                if (params.currentChunk == null) {
                    ModLog.deny("pocket_config", "load_gen_params",
                            "reason=current_chunk_missing actual=null expected=int[3] action=reset_to_(0,0,0)");
                    params.currentChunk = new CoordSet(0, 0, 0);
                }
                if (params.nextPocketCoordsDirection == null) {
                    ModLog.deny("pocket_config", "load_gen_params",
                            "reason=next_direction_missing actual=null expected=NORTH action=reset_to_NORTH");
                    params.nextPocketCoordsDirection = Direction.NORTH;
                }
                ModLog.step("pocket_config", "load_gen_params", "file={} params={}", dataFile, params);
                return params;
            }
        } catch (Exception e) {
            ModLog.fail("pocket_config", "load_gen_params", e, "reason=read_or_parse_error expected=readable_json action=reset_to_default");
            return new PocketGenParameters();
        }
    }
}
