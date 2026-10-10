package net.gtn.dimensionalpocket.common;

import net.gtn.dimensionalpocket.common.block.BlockDimensionalPocket;
import net.gtn.dimensionalpocket.common.block.BlockDimensionalPocketWall;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.gtn.dimensionalpocket.common.lib.Strings;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 方块注册表。1.7.10 对应物 {@code common/ModBlocks.java}（构造即注册）。
 *
 * <p>{@link #ITEMS} 是挂 BlockItem 的独立注册表（naaj 的既有写法），
 * 与 {@link ModItems#ITEMS}（纯物品）分开。
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Reference.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Reference.MOD_ID);

    public static final RegistryObject<Block> DIMENSIONAL_POCKET =
            BLOCKS.register(Strings.BLOCK_POCKET, BlockDimensionalPocket::new);

    public static final RegistryObject<Block> DIMENSIONAL_POCKET_WALL =
            BLOCKS.register(Strings.BLOCK_POCKET_WALL, BlockDimensionalPocketWall::new);

    public static final RegistryObject<Item> DIMENSIONAL_POCKET_ITEM =
            ITEMS.register(Strings.BLOCK_POCKET, () -> new BlockItem(DIMENSIONAL_POCKET.get(), new Item.Properties()));

    /** 墙方块有物品（上游 GameRegistry.registerBlock 自动建 ItemBlock），但不进创造栏。 */
    public static final RegistryObject<Item> DIMENSIONAL_POCKET_WALL_ITEM =
            ITEMS.register(Strings.BLOCK_POCKET_WALL, () -> new BlockItem(DIMENSIONAL_POCKET_WALL.get(), new Item.Properties()));

    private ModBlocks() {
    }
}
