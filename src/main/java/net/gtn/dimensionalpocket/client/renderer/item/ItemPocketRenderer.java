package net.gtn.dimensionalpocket.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;

import net.gtn.dimensionalpocket.client.theme.PocketTextures;
import net.gtn.dimensionalpocket.client.utils.RenderUtils;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Item form of the pocket block: a static particle texture on all six faces plus the frame, without
 * the animated, six sided field the block entity renderer draws.
 *
 * <p>1.7.10 origin: {@code client/renderer/item/ItemPocketRenderer.java} ({@code IItemRenderer}). That
 * interface is gone; the 1.20.1 equivalent is a {@link BlockEntityWithoutLevelRenderer} handed out by
 * {@link IClientItemExtensions#getCustomRenderer()}. Forge 47.4.0 has no
 * {@code RegisterClientExtensionsEvent} (1.20.2+ only), so the extension is registered from the item:
 * the pocket block item's {@code initializeClient(Consumer<IClientItemExtensions>)} must accept
 * {@link #EXTENSIONS}.
 *
 * <p>Two upstream behaviours are preserved deliberately:
 * <ul>
 *   <li>the particle texture is always {@code particleFieldStaticSmall.png} - the item never goes
 *       through {@code PortalRenderer.updateField}, so it does not follow the fancy setting;</li>
 *   <li>all six faces are drawn with a {@code -0.001} inset, then the frame on the block surface.</li>
 * </ul>
 *
 * <p><b>Display transforms</b>: 1.7.10 relied on {@code shouldUseRenderHelper = true} plus the vanilla
 * helper for the per {@code ItemRenderType} transform and only added two offsets itself. 1.20.1 hands
 * the {@link ItemDisplayContext} to {@code renderByItem}, and the mapping is not one to one
 * (报告 03 §10.2 item 6): {@code INVENTORY -> GUI} and {@code ENTITY -> GROUND} are applied, the other
 * contexts keep the vanilla transform only.
 */
public class ItemPocketRenderer extends BlockEntityWithoutLevelRenderer {

    /**
     * The client extension the pocket block item must hand out from
     * {@code Item#initializeClient(Consumer<IClientItemExtensions>)}.
     *
     * <p>The renderer instance is created lazily on the first {@code getCustomRenderer()} call, because
     * {@link BlockEntityWithoutLevelRenderer} needs the client's render dispatcher and model set - both
     * of which only exist once the client is up.
     */
    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        private BlockEntityWithoutLevelRenderer renderer;

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (renderer == null) {
                Minecraft minecraft = Minecraft.getInstance();
                renderer = new ItemPocketRenderer(minecraft.getBlockEntityRenderDispatcher(),
                        minecraft.getEntityModels());
            }
            return renderer;
        }
    };

    /** 1.7.10: {@code offsets = { -0.001F, ... }} for the six particle faces. */
    private static final float[] FIELD_OFFSETS = { -0.001F, -0.001F, -0.001F, -0.001F, -0.001F, -0.001F };

    public ItemPocketRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack pose,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        pose.pushPose();

        if (displayContext == ItemDisplayContext.GUI) {
            pose.translate(0.0F, -0.1F, 0.0F);
        }
        if (displayContext == ItemDisplayContext.GROUND) {
            pose.translate(-0.5F, -0.4F, -0.5F);
        }

        RenderUtils.drawFaces(pose, buffers, Reference.TEXTURE_PARTICLE_FIELD_STATIC_SMALL, FIELD_OFFSETS, 1.0F,
                LightTexture.FULL_BRIGHT);
        RenderUtils.drawFaces(pose, buffers, PocketTextures.pocketFrame, RenderUtils.DEFAULT_OFFSETS, 1.0F,
                packedLight);

        pose.popPose();
    }
}
