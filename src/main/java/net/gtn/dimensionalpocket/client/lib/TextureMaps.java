package net.gtn.dimensionalpocket.client.lib;

import net.gtn.dimensionalpocket.common.lib.Reference;
import net.minecraft.resources.ResourceLocation;

/**
 * Texture root paths.
 *
 * <p>1.7.10 origin: {@code client/lib/TextureMaps.java}. The upstream constants embedded the
 * namespace ({@code Reference.MOD_IDENTIFIER + "textures/blocks/"}) because 1.7.10 resource
 * locations were built from one single string; in 1.20.1 the namespace is a separate constructor
 * argument, so the roots below are bare paths and every caller pairs them with
 * {@link Reference#MOD_ID}.
 *
 * <p>The paths themselves are kept exactly as upstream ({@code textures/blocks/} - plural, camelCase
 * file names): {@code src/main/resources/assets/dimensionalpockets/textures/} already ships those
 * file names byte for byte (see 报告 05 §B), so renaming them here would break every reference.
 */
public final class TextureMaps {

    public static final String TEXTURE_BLOCK_ROOT = "textures/blocks/";
    public static final String TEXTURE_MISC_ROOT = "textures/misc/";
    public static final String TEXTURE_PARTICLE_FIELD_ROOT = "textures/misc/particleField/";

    /** 1x1 fully transparent texture, used whenever a texture would otherwise be {@code null}. */
    public static final ResourceLocation BLANK_TEXTURE = Reference.TEXTURE_BLANK;

    private TextureMaps() {
    }
}
