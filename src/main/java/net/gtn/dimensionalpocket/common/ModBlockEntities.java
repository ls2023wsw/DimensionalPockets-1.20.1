package net.gtn.dimensionalpocket.common;

import net.gtn.dimensionalpocket.common.lib.Reference;
import net.gtn.dimensionalpocket.common.lib.Strings;
import net.gtn.dimensionalpocket.common.tileentity.TileDimensionalPocket;
import net.gtn.dimensionalpocket.common.tileentity.TileDimensionalPocketWallConnector;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 方块实体类型注册表。1.7.10 对应物 {@code CommonProxy.registerTileEntities()}。
 *
 * <p>1.20.1 的 {@code BlockEntityType} 没有 metadata 概念：上游用 {@code meta == 1} 区分
 * 「连接器」，这里靠 {@code BlockDimensionalPocketWall.CONNECTOR} 方块状态在
 * {@code newBlockEntity} 里分流，两种状态共用一个 {@code BlockEntityType}。
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Reference.MOD_ID);

    public static final RegistryObject<BlockEntityType<TileDimensionalPocket>> DIMENSIONAL_POCKET =
            BLOCK_ENTITIES.register(Strings.TILE_POCKET, () -> BlockEntityType.Builder
                    .of(TileDimensionalPocket::new, ModBlocks.DIMENSIONAL_POCKET.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<TileDimensionalPocketWallConnector>> WALL_CONNECTOR =
            BLOCK_ENTITIES.register(Strings.TILE_POCKET_WALL_CONNECTOR, () -> BlockEntityType.Builder
                    .of(TileDimensionalPocketWallConnector::new, ModBlocks.DIMENSIONAL_POCKET_WALL.get())
                    .build(null));

    private ModBlockEntities() {
    }
}
