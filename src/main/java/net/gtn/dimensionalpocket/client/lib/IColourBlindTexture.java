package net.gtn.dimensionalpocket.client.lib;

import net.minecraft.resources.ResourceLocation;

/**
 * A texture that has an alternative version for colour blind players.
 *
 * <p>1.7.10 origin: {@code client/lib/IColourBlindTexture.java} - unchanged, it only depends on
 * {@link ResourceLocation} and has no version specific API.
 */
public interface IColourBlindTexture {

    /**
     * @param colourBlind whether the colour blind variant should be returned
     * @return the texture to bind, never {@code null} (implementations fall back to a blank texture)
     */
    ResourceLocation getTexture(boolean colourBlind);
}
