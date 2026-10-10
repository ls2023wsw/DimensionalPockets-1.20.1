package net.gtn.dimensionalpocket.common.lib;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Identity of the mod, plus everything that used to be a numeric id in 1.7.10.
 *
 * <p>1.7.10 origin: {@code Reference} also held the mod options as annotated statics; in 1.20.1 all
 * of those live in {@link net.gtn.dimensionalpocket.common.core.DPConfig} (a ForgeConfigSpec) and the
 * old numeric dimension id (33) / biome id (99) are replaced by the {@link ResourceLocation}s below.
 */
public final class Reference {

    public static final String MOD_ID = "dimensionalpockets";
    public static final String MOD_NAME = "Dimensional Pockets";
    public static final String VERSION = "1.0.5";

    /** Upstream authors, kept for attribution (LGPL v3). */
    public static final String MOD_AUTHORS = "NPException, Team(\"GTN\")";
    public static final String MOD_CREDITS = "1.20.1 port by luoshang";

    public static final String LOG_PREFIX = MOD_ID;

    // ------------------------------------------------------------------
    // Pocket level
    // ------------------------------------------------------------------

    /** The pocket level's registry name. Replaces the old numeric dimension id (33). */
    public static final ResourceLocation POCKET_LEVEL_ID = new ResourceLocation(MOD_ID, "pocket");

    /** Key used for every {@code ServerLevel} lookup of the pocket level. */
    public static final ResourceKey<Level> POCKET_LEVEL = ResourceKey.create(Registries.DIMENSION, POCKET_LEVEL_ID);

    /** The single biome the pocket rooms are built in. Replaces the old biome id (99). */
    public static final ResourceLocation POCKET_BIOME_ID = new ResourceLocation(MOD_ID, "pocket");

    /** Human readable dimension name (shows up on the F3 debug screen). */
    public static final String POCKET_LEVEL_NAME = "Pocket Dimension";

    /** Datapack paths of the generated dimension files. */
    public static final ResourceLocation POCKET_DIMENSION_TYPE_ID = new ResourceLocation(MOD_ID, "pocket");
    public static final ResourceLocation POCKET_DIMENSION_JSON_ID = new ResourceLocation(MOD_ID, "pocket");

    // ------------------------------------------------------------------
    // World save layout (original: <world>/dimpockets/*.json)
    // ------------------------------------------------------------------

    /** Folder inside the world save directory that holds every DP json file. */
    public static final String SAVE_DIR = "dimpockets";

    public static final String FILE_TELEPORT_REGISTRY = "teleportRegistry";
    public static final String FILE_POCKET_GEN_PARAMS = "pocketGenParameters";

    // ------------------------------------------------------------------
    // Assets
    // ------------------------------------------------------------------

    /** Animated lightning ball frames: {@code <prefix><frame>.png}, frames 1..20. */
    public static final String PARTICLE_TEXTURE_PREFIX = "fx8_lighteningBall/lighteningball_1_20_";

    public static final ResourceLocation TEXTURE_PARTICLE_FIELD =
            texture("misc/particleField/particleField");
    public static final ResourceLocation TEXTURE_PARTICLE_FIELD_STATIC =
            texture("misc/particleField/particleFieldStatic");
    public static final ResourceLocation TEXTURE_PARTICLE_FIELD_STATIC_SMALL =
            texture("misc/particleField/particleFieldStaticSmall");
    public static final ResourceLocation TEXTURE_TUNNEL = texture("misc/particleField/tunnel");
    public static final ResourceLocation TEXTURE_BLANK = texture("misc/blank");
    public static final ResourceLocation TEXTURE_GUIDE = texture("gui/guideDP");

    private static ResourceLocation texture(final String path) {
        return new ResourceLocation(MOD_ID, "textures/" + path + ".png");
    }

    private Reference() {}
}
