package net.gtn.dimensionalpocket.common;

import net.gtn.dimensionalpocket.common.lib.Reference;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 创造模式标签。1.7.10 对应物 {@code oc.client.CreativeTabSimple}（{@code DimensionalPockets.creativeTab}）。
 *
 * <p>注册名 {@code dimensionalpockets:main}，lang key {@code itemGroup.dimensionalpockets.main}。
 * 墙方块 {@code setCreativeTab(null)} 的语义 = 这里不 accept 它。
 */
public final class ModTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Reference.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dimensionalpockets.main"))
            .icon(() -> new ItemStack(ModBlocks.DIMENSIONAL_POCKET.get()))
            .displayItems((params, output) -> {
                output.accept(ModBlocks.DIMENSIONAL_POCKET_ITEM.get());
                output.accept(ModItems.INFO_BOOK.get());
                output.accept(ModItems.END_CRYSTAL.get());
                output.accept(ModItems.NETHER_CRYSTAL.get());
            })
            .build());

    private ModTabs() {
    }
}
