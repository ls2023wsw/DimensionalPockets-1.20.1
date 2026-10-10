package net.gtn.dimensionalpocket.common.items;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * 信息书。1.7.10 对应物 {@code common/items/ItemBook.java}（右键开 {@code GuiInfoBook}）。
 *
 * <p>上游只在客户端开 GUI（{@code if (world.isRemote) player.openGui(...)}），服务端什么都不做；
 * 1.20.1 的 {@code use} 双端都会被调，所以必须判 {@code level.isClientSide()}。
 * 本 mod 没有服务端容器，指南书是纯客户端 Screen（S5 的 {@code client/gui/InfoBookScreen}）。
 */
public class ItemBook extends ItemDP {

    public ItemBook(Item.Properties properties) {
        // 1.7.10: setMaxStackSize(1)
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            openInfoBookScreen();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * 客户端专属入口。用 {@link DistExecutor} 隔离，dedicated server 上永远不会解析
     * {@code net.minecraft.client.Minecraft} 与 Screen 类。
     */
    private static void openInfoBookScreen() {
        DistExecutor.unsafeRunForDist(
                () -> () -> {
                    net.minecraft.client.Minecraft.getInstance()
                            .setScreen(new net.gtn.dimensionalpocket.client.gui.InfoBookScreen());
                    return Boolean.TRUE;
                },
                () -> () -> Boolean.FALSE);
    }
}
