package net.gtn.dimensionalpocket.common.items;

import net.minecraft.world.item.Item;

/**
 * 三个物品的公共父类。1.7.10 对应物 {@code common/items/ItemDP.java}。
 *
 * <p>上游的 {@code onCreated}（合成时上报 analytics）与 {@code setCreativeTab} 都已按移植决策删除：
 * analytics 整体丢弃，创造栏归属由 {@code ModTabs} 负责。
 */
public class ItemDP extends Item {

    public ItemDP(Item.Properties properties) {
        super(properties);
    }
}
