package net.gtn.dimensionalpocket.common.core;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Port of the 1.7.10 annotated statics that used to live in {@code Reference}.
 *
 * <p>Dropped on purpose (see 移植说明-1.20.1.md):
 * <ul>
 *   <li>{@code DIMENSION_ID} / {@code BIOME_ID} - 1.20.1 addresses dimensions and biomes by
 *       {@link net.minecraft.resources.ResourceLocation}, not by a numeric id.</li>
 *   <li>{@code USE_SHADER_FOR_PARTICLE_FIELD} - the legacy GLSL pipeline does not exist in 1.20.1;
 *       the non-shader particle field is always used (that was the 1.7.10 default anyway).</li>
 *   <li>{@code DO_VERSION_CHECK} / {@code MAY_COLLECT_ANONYMOUS_USAGE_DATA} - all outbound network
 *       calls were removed for this port.</li>
 * </ul>
 */
public final class DPConfig {

    public static final ForgeConfigSpec SPEC;
    public static final DPConfig CFG;

    static {
        final Pair<DPConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(DPConfig::new);
        CFG = pair.getLeft();
        SPEC = pair.getRight();
    }

    // ------------------------------------------------------------------ Debugging

    public final ForgeConfigSpec.BooleanValue enforceSidedMethods;
    public final ForgeConfigSpec.BooleanValue canBreakPocketWallInCreative;

    // ------------------------------------------------------------------ Graphics (client only in effect)

    public final ForgeConfigSpec.IntValue numberOfParticlePlanes;
    public final ForgeConfigSpec.IntValue forceFancyRendering;

    // ------------------------------------------------------------------ Gameplay

    public final ForgeConfigSpec.DoubleValue dimensionalPocketResistance;
    public final ForgeConfigSpec.BooleanValue shouldSpawnWithBook;
    public final ForgeConfigSpec.IntValue craftingsPerCrystal;
    public final ForgeConfigSpec.BooleanValue usePeacefulRecipes;

    // ------------------------------------------------------------------ General

    public final ForgeConfigSpec.BooleanValue keepPocketRoomsChunkLoaded;
    public final ForgeConfigSpec.BooleanValue colorBlindMode;

    private DPConfig(final ForgeConfigSpec.Builder b) {
        b.comment("Dimensional Pockets - port of the 1.7.10 original by NPException / Team(\"GTN\")").push("dimensionalpockets");

        b.comment("Debugging helpers.").push("debugging");
        enforceSidedMethods = b
                .comment("If true, a RuntimeException is thrown whenever a client-only method is called by the server or vice versa.")
                .define("enforceSidedMethods", false);
        canBreakPocketWallInCreative = b
                .comment("Set this to true if you desperately want to try to break your world :P")
                .define("canBreakPocketWallInCreative", false);
        b.pop();

        b.comment("Client side rendering. Has no effect on a dedicated server.").push("graphics");
        numberOfParticlePlanes = b
                .comment("If you experience low FPS, try reducing this number first before switching fancy rendering off entirely.",
                        "Or if you have render power to spare you could raise this value.")
                .defineInRange("numberOfParticlePlanes", 15, 1, 50);
        forceFancyRendering = b
                .comment("0 = particle field appearance based on Minecraft's Graphics settings (fancy or fast)",
                        "1 = particle field appearance forced to non-fancy version",
                        "2 = particle field appearance forced to fancy version")
                .defineInRange("forceFancyRendering", 0, 0, 2);
        b.pop();

        b.comment("Gameplay tuning.").push("gameplay");
        dimensionalPocketResistance = b
                .comment("You can change this to modify the resistance of the DP to explosions.")
                .defineInRange("dimensionalPocketResistance", 15.0D, 0.0D, 6000000.0D);
        shouldSpawnWithBook = b
                .comment("Decides whether or not any player spawns with a book upon new spawn.")
                .define("shouldSpawnWithBook", true);
        craftingsPerCrystal = b
                .comment("This is the number of Dimensional Pockets you can craft with a pair of crystals before they break.",
                        "If set to 0, crystals will never break.")
                .defineInRange("craftingsPerCrystal", 4, 0, Integer.MAX_VALUE);
        usePeacefulRecipes = b
                .comment("Decides if the Nether Crystal and End Crystal can be crafted without hostile mob drops.",
                        "The Nether Crystal will use gold nuggets instead of Ghast tears, and the End Crystal will use Quartz instead of Ender Eyes.")
                .define("usePeacefulRecipes", false);
        b.pop();

        b.comment("General behaviour.").push("general");
        keepPocketRoomsChunkLoaded = b
                .comment("Keeps the chunk of every existing pocket room loaded, even when nobody is inside.")
                .define("keepPocketRoomsChunkLoaded", true);
        colorBlindMode = b
                .comment("If you have a hard time distinguishing colours, you can change this to true.",
                        "Gameplay relevant parts of the mod will be displayed in a less color dependent way then.")
                .define("colorBlindMode", false);
        b.pop();

        b.pop();
    }

    // ------------------------------------------------------------------ convenience

    /** 1.7.10: {@code Reference.numberOfParticlePlanes()}, minus the client-only FPS fallback. */
    public int numberOfParticlePlanes() {
        return numberOfParticlePlanes.get();
    }

    /** 1.7.10: {@code Reference.useFancyField()}. */
    public boolean useFancyField() {
        return forceFancyRendering.get() != 1;
    }

    public static boolean usePeacefulRecipes() {
        return CFG.usePeacefulRecipes.get();
    }

    public static int craftingsPerCrystal() {
        return CFG.craftingsPerCrystal.get();
    }

    public static boolean colorBlindMode() {
        return CFG.colorBlindMode.get();
    }

    public static boolean canBreakPocketWallInCreative() {
        return CFG.canBreakPocketWallInCreative.get();
    }

    public static boolean enforceSidedMethods() {
        return CFG.enforceSidedMethods.get();
    }

    public static boolean keepPocketRoomsChunkLoaded() {
        return CFG.keepPocketRoomsChunkLoaded.get();
    }

    public static boolean shouldSpawnWithBook() {
        return CFG.shouldSpawnWithBook.get();
    }

    public static float dimensionalPocketResistance() {
        return CFG.dimensionalPocketResistance.get().floatValue();
    }
}
