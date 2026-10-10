package net.gtn.dimensionalpocket.common;

import net.gtn.dimensionalpocket.common.items.ItemBook;
import net.gtn.dimensionalpocket.common.items.ItemEndCrystal;
import net.gtn.dimensionalpocket.common.items.ItemNetherCrystal;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.gtn.dimensionalpocket.common.lib.Strings;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 物品注册表。1.7.10 对应物 {@code common/ModItems.java}。
 *
 * <p>{@code initRecipes()}（1.7.10 的 {@code CraftingManager.addRecipe}）**不在这里**：
 * 1.20.1 的配方是数据包 JSON（{@code data/dimensionalpockets/recipes/*.json}），由 S6 负责。
 *
 * <p>水晶的「可合成次数」不走 {@code Item.Properties.durability(...)}，而是由两个水晶类覆写
 * {@code getMaxDamage(ItemStack)} / {@code canBeDepleted()} 在运行时读 {@code DPConfig}：
 * 注册期读配置会踩「配置尚未加载 → {@code ConfigValue.get()} 抛异常」的时序坑，
 * 而且这样配置改动无需重启即可生效。
 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Reference.MOD_ID);

    public static final RegistryObject<Item> INFO_BOOK =
            ITEMS.register(Strings.INFO_BOOK, () -> new ItemBook(new Item.Properties()));

    public static final RegistryObject<Item> END_CRYSTAL =
            ITEMS.register(Strings.END_CRYSTAL, () -> new ItemEndCrystal(new Item.Properties()));

    public static final RegistryObject<Item> NETHER_CRYSTAL =
            ITEMS.register(Strings.NETHER_CRYSTAL, () -> new ItemNetherCrystal(new Item.Properties()));

    private ModItems() {
    }
}
