package net.gtn.dimensionalpocket.client.theme;

import java.util.EnumMap;
import java.util.Map;

import net.gtn.dimensionalpocket.client.lib.IColourBlindTexture;
import net.gtn.dimensionalpocket.client.lib.TextureMaps;
import net.gtn.dimensionalpocket.common.core.pocket.PocketSideState;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Every texture a pocket block / wall connector can draw, plus the two look-up tables for the
 * "side indicator" and the "face state overlay".
 *
 * <p>1.7.10 origin: {@code client/theme/PocketTextures.java}. {@code ForgeDirection} became
 * {@link Direction} ({@code Direction.values()} has the same order as
 * {@code ForgeDirection.VALID_DIRECTIONS}: DOWN, UP, NORTH, SOUTH, WEST, EAST).
 *
 * <p>Texture list (all files already exist in {@code assets/dimensionalpockets/textures/blocks/}):
 * <ul>
 *   <li>frame / inside frame / connector icon / connector background</li>
 *   <li>direction indicator: {@code pocketIndicator.png}, colour blind variants
 *       {@code pocketIndicator_<direction>.png} (only the four horizontal ones exist upstream; UP and
 *       DOWN intentionally fall back to the plain texture, exactly like 1.7.10)</li>
 *   <li>face state overlay: {@code pocketOverlay<TextureName>.png} + {@code ...CB.png}</li>
 * </ul>
 */
public final class PocketTextures {

    public static final ResourceLocation pocketFrame = rl("pocketFrame");
    public static final ResourceLocation pocketFrameInside = rl("pocketFrameInside");
    public static final ResourceLocation connector = rl("wallConnector");
    public static final ResourceLocation connectorBG = rl("wallConnector_bg");

    private static final Map<Direction, IColourBlindTexture> sideIndicators = new EnumMap<>(Direction.class);
    private static final Map<PocketSideState, IColourBlindTexture> overlays = new EnumMap<>(PocketSideState.class);

    static {
        ResourceLocation indicator = rl("pocketIndicator");

        for (Direction direction : Direction.values()) {
            Direction opposite = direction.getOpposite();
            sideIndicators.put(opposite,
                    new TextureWrapper(indicator, rl("pocketIndicator_" + opposite.getName())));
        }
        // Upstream overwrites these two because pocketIndicator_up/down.png do not exist.
        sideIndicators.put(Direction.UP, new TextureWrapper(indicator));
        sideIndicators.put(Direction.DOWN, new TextureWrapper(indicator));

        for (PocketSideState state : PocketSideState.TEXTURED_STATES) {
            String stateString = "pocketOverlay" + state.getTextureName();
            overlays.put(state, new TextureWrapper(rl(stateString), rl(stateString + "CB")));
        }
    }

    private PocketTextures() {
    }

    /** @return the indicator for that face, never {@code null} for a valid direction. */
    public static IColourBlindTexture sideIndicatorFor(Direction direction) {
        return sideIndicators.get(direction);
    }

    /**
     * @return the overlay texture for that face state, or {@code null} for states without a texture
     *         ({@link PocketSideState#NONE}) - callers must skip those faces.
     */
    public static IColourBlindTexture overlayFor(PocketSideState sideState) {
        return overlays.get(sideState);
    }

    private static ResourceLocation rl(String name) {
        return new ResourceLocation(Reference.MOD_ID, TextureMaps.TEXTURE_BLOCK_ROOT + name + ".png");
    }
}
