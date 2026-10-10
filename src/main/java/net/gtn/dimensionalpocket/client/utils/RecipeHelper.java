package net.gtn.dimensionalpocket.client.utils;

import net.gtn.dimensionalpocket.common.ModItems;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.gtn.dimensionalpocket.common.lib.Strings;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The hard coded 3x3 recipes the info book shows. These are <b>not</b> the registered recipes, they
 * only exist for display - same as 1.7.10 ({@code client/utils/RecipeHelper.java}).
 *
 * <p>Layout of every array: index 0..8 = the 3x3 grid (row major), index 9 = the output slot.
 *
 * <p>Kept hard coded on purpose (报告 03 §3.6 option A): the book then shows exactly what 1.7.10
 * showed, including the upstream inconsistency of ignoring {@code usePeacefulRecipes}.
 */
public final class RecipeHelper {

    /** 8 ender eyes around a glass block, output = end crystal. */
    private static final ItemStack[] ENDER_RECIPE = {
            new ItemStack(Items.ENDER_EYE), new ItemStack(Items.ENDER_EYE), new ItemStack(Items.ENDER_EYE),
            new ItemStack(Items.ENDER_EYE), new ItemStack(Blocks.GLASS), new ItemStack(Items.ENDER_EYE),
            new ItemStack(Items.ENDER_EYE), new ItemStack(Items.ENDER_EYE), new ItemStack(Items.ENDER_EYE),
            stack(ModItems.END_CRYSTAL)
    };

    /** 8 ghast tears around a redstone block, output = nether crystal. */
    private static final ItemStack[] NETHER_RECIPE = {
            new ItemStack(Items.GHAST_TEAR), new ItemStack(Items.GHAST_TEAR), new ItemStack(Items.GHAST_TEAR),
            new ItemStack(Items.GHAST_TEAR), new ItemStack(Blocks.REDSTONE_BLOCK), new ItemStack(Items.GHAST_TEAR),
            new ItemStack(Items.GHAST_TEAR), new ItemStack(Items.GHAST_TEAR), new ItemStack(Items.GHAST_TEAR),
            stack(ModItems.NETHER_CRYSTAL)
    };

    /** stone bricks / crystals / iron-diamond-iron, output = the dimensional pocket block. */
    private static final ItemStack[] BLOCK_RECIPE = {
            new ItemStack(Blocks.STONE_BRICKS), stack(ModItems.END_CRYSTAL), new ItemStack(Blocks.STONE_BRICKS),
            new ItemStack(Blocks.IRON_BLOCK), new ItemStack(Blocks.DIAMOND_BLOCK), new ItemStack(Blocks.IRON_BLOCK),
            new ItemStack(Blocks.STONE_BRICKS), stack(ModItems.NETHER_CRYSTAL), new ItemStack(Blocks.STONE_BRICKS),
            pocketItemStack()
    };

    private RecipeHelper() {
    }

    /**
     * 1.7.10 order: 0 = dimensional pocket, 1 = end crystal, 2 = nether crystal.
     *
     * @return the 10 slot array, or {@code null} for an unknown type.
     */
    public static ItemStack[] getRecipe(int type) {
        switch (type) {
            case 0:
                return BLOCK_RECIPE;
            case 1:
                return ENDER_RECIPE;
            case 2:
                return NETHER_RECIPE;
            default:
                return null;
        }
    }

    private static ItemStack stack(RegistryObject<Item> item) {
        return new ItemStack(item.get());
    }

    /**
     * The pocket block item is registered by {@code ModBlocks.ITEMS} as a {@code BlockItem} under
     * {@link Strings#BLOCK_POCKET}; the contract exposes no {@code RegistryObject<Item>} for it, so it
     * is resolved through the item registry (safe here: this class is only used from the client GUI,
     * i.e. long after the registry events ran).
     */
    private static ItemStack pocketItemStack() {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(Reference.MOD_ID, Strings.BLOCK_POCKET));
        if (item == null) {
            ModLog.deny("RecipeHelper", "pocket_item", "reason=item_not_registered actual=null expected={}:{}",
                    Reference.MOD_ID, Strings.BLOCK_POCKET);
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }
}
