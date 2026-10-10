package net.gtn.dimensionalpocket.common.lib;

/**
 * Registry names.
 *
 * <p>1.7.10 used camelCase names ({@code dimensionalPocket}, {@code infoBookDP}, ...). 1.20.1 only
 * allows {@code [a-z0-9_]} so every name below is snake_case and the language file
 * ({@code assets/dimensionalpockets/lang/en_us.json}) uses exactly the same strings.
 */
public final class Strings {

    public static final String BLOCK_POCKET = "dimensional_pocket";
    public static final String BLOCK_POCKET_WALL = "dimensional_pocket_wall";

    public static final String TILE_POCKET = "dimensional_pocket";
    public static final String TILE_POCKET_WALL_CONNECTOR = "dimensional_pocket_wall_connector";

    public static final String INFO_BOOK = "info_book";
    public static final String END_CRYSTAL = "end_crystal";
    public static final String NETHER_CRYSTAL = "nether_crystal";

    public static final String CONTAINER_POCKET = "dimensional_pocket";

    private Strings() {}
}
