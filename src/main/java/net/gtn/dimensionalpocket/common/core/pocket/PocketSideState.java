package net.gtn.dimensionalpocket.common.core.pocket;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import net.gtn.dimensionalpocket.common.core.utils.Colour;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.network.chat.Component;

/**
 * What a single face of a Dimensional Pocket does.
 *
 * <p>1.7.10 had exactly two values ({@code NONE}, {@code ENERGY}) - the mod never shipped the
 * fluid/item/redstone states, even though the language file still contains the (unused) keys.
 * Serialisation is by enum <i>name</i>, same as upstream, but unknown names now fall back to
 * {@link #NONE} instead of throwing (see {@link #byName(String)}).
 */
public enum PocketSideState {

    NONE(Colour.WHITE),
    /** Used for Forge Energy (formerly RF) and item transfer. */
    ENERGY(Colour.GREEN, "Basic");

    /** States that have an overlay texture in {@code textures/blocks/pocketOverlay*}. */
    public static final PocketSideState[] TEXTURED_STATES = new PocketSideState[] { ENERGY };

    private static final Map<PocketSideState, Colour> STATE_COLOURS;

    static {
        Map<PocketSideState, Colour> colours = new EnumMap<>(PocketSideState.class);
        for (PocketSideState state : values()) {
            colours.put(state, state.colour);
        }
        STATE_COLOURS = Collections.unmodifiableMap(colours);
    }

    private final Colour colour;
    private final String textureName;

    PocketSideState(Colour colour) {
        this(colour, "");
    }

    PocketSideState(Colour colour, String textureName) {
        this.colour = colour.copy();
        this.colour.a = 0.392F;
        this.textureName = textureName;
    }

    public String translateName() {
        // 1.7.10: StatCollector.translateToLocal("pocket.side.state." + name())
        return Component.translatable("pocket.side.state." + name()).getString();
    }

    public String getTextureName() {
        return textureName;
    }

    public Colour getColour() {
        return colour;
    }

    public static Colour colourOf(PocketSideState state) {
        Colour colour = STATE_COLOURS.get(state);
        return colour == null ? Colour.WHITE : colour;
    }

    public PocketSideState next() {
        PocketSideState[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /**
     * Name based lookup that never throws.
     *
     * @return the matching state, or {@link #NONE} for {@code null} / unknown names (logged).
     */
    public static PocketSideState byName(String name) {
        if (name == null || name.isEmpty()) {
            return NONE;
        }
        for (PocketSideState state : values()) {
            if (state.name().equals(name)) {
                return state;
            }
        }
        ModLog.warn("pocket", "side_state_unknown", "name", name, "fallback", NONE.name());
        return NONE;
    }
}
