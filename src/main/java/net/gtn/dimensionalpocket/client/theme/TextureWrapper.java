package net.gtn.dimensionalpocket.client.theme;

import net.gtn.dimensionalpocket.client.lib.IColourBlindTexture;
import net.gtn.dimensionalpocket.client.lib.TextureMaps;
import net.minecraft.resources.ResourceLocation;

/**
 * Default {@link IColourBlindTexture} implementation: a normal / colour blind texture pair, with
 * {@link TextureMaps#BLANK_TEXTURE} as the fallback for every {@code null}.
 *
 * <p>1.7.10 origin: {@code client/theme/TextureWrapper.java} - ported without semantic changes.
 */
public class TextureWrapper implements IColourBlindTexture {

    private final ResourceLocation defaultTexture;
    private final ResourceLocation colourBlindTexture;

    public TextureWrapper() {
        this.defaultTexture = TextureMaps.BLANK_TEXTURE;
        this.colourBlindTexture = TextureMaps.BLANK_TEXTURE;
    }

    public TextureWrapper(ResourceLocation defaultTexture) {
        this.defaultTexture = defaultTexture != null ? defaultTexture : TextureMaps.BLANK_TEXTURE;
        this.colourBlindTexture = TextureMaps.BLANK_TEXTURE;
    }

    public TextureWrapper(ResourceLocation defaultTexture, ResourceLocation colourBlindTexture) {
        this.defaultTexture = defaultTexture != null ? defaultTexture : TextureMaps.BLANK_TEXTURE;
        this.colourBlindTexture = colourBlindTexture != null ? colourBlindTexture : TextureMaps.BLANK_TEXTURE;
    }

    @Override
    public ResourceLocation getTexture(boolean colourBlind) {
        return colourBlind ? colourBlindTexture : defaultTexture;
    }
}
