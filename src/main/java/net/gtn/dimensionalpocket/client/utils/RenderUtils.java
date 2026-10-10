package net.gtn.dimensionalpocket.client.utils;

import net.gtn.dimensionalpocket.common.core.utils.Colour;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Native replacement for the 1.7.10 helper bundle that used to be spread over
 * {@code oc/client/renderer/BlockRenderer} (fixed function quads) and {@code client/utils/RenderUtils}
 * (GUI item rendering).
 *
 * <p><b>Why this file exists</b>: the contract keeps {@code client/utils/RenderUtils.java} in the
 * client slice, but its 1.7.10 body (manual {@code RenderHelper.enableGUIStandardItemLighting()} +
 * {@code RenderItem.zLevel} for GUI items) is dead in 1.20.1 - {@code GuiGraphics#renderItem} does all
 * of that internally (报告 03 §3.4). So this class is repurposed as the native {@code BlockRenderer}:
 * it draws the axis aligned quads the pocket block, the wall connector, the item form and the portal
 * field are all built from, plus the per face colours that 1.7.10 kept in {@code Utils.FD_COLOURS}.
 *
 * <p><b>Vertex tables</b> are copied verbatim from {@code BlockRenderer.drawFace*}: the offset pushes
 * the quad in/out of the block, {@code scale} is the edge length (1 = one block, 14 = the wall
 * connector's inner frame, 16 = its indicator/overlay layer).
 */
public final class RenderUtils {

    public static final float OFFSET_1 = 0.0625F;
    public static final float OFFSET_2 = 0.125F;
    public static final float OFFSET_3 = 0.1875F;
    public static final float OFFSET_4 = 0.25F;
    public static final float OFFSET_5 = 0.3125F;
    public static final float OFFSET_6 = 0.375F;
    public static final float OFFSET_7 = 0.4375F;
    public static final float OFFSET_8 = 0.5F;
    public static final float OFFSET_9 = 0.5625F;
    public static final float OFFSET_10 = 0.625F;
    public static final float OFFSET_11 = 0.6875F;
    public static final float OFFSET_12 = 0.75F;
    public static final float OFFSET_13 = 0.8125F;
    public static final float OFFSET_14 = 0.875F;
    public static final float OFFSET_15 = 0.9375F;

    /** All zero, the "draw on the block surface" offsets used by the frame and by the item form. */
    public static final float[] DEFAULT_OFFSETS = new float[6];

    /**
     * 1.7.10: {@code Utils.FD_COLOURS}. Alpha is {@code 7.0} upstream, which the fixed pipeline clamped
     * to 1.0; in 1.20.1 the colour is written into a normalised byte attribute, so an out of range
     * value would wrap instead of clamp - the alpha is therefore already 1.0 here.
     */
    private static final Colour[] FACE_COLOURS = new Colour[6];

    static {
        FACE_COLOURS[Direction.DOWN.ordinal()] = new Colour(Colour.PURPLE.r, Colour.PURPLE.g, Colour.PURPLE.b, 1.0D);
        FACE_COLOURS[Direction.UP.ordinal()] = new Colour(Colour.ORANGE.r, Colour.ORANGE.g, Colour.ORANGE.b, 1.0D);
        FACE_COLOURS[Direction.NORTH.ordinal()] = new Colour(Colour.YELLOW.r, Colour.YELLOW.g, Colour.YELLOW.b, 1.0D);
        FACE_COLOURS[Direction.SOUTH.ordinal()] = new Colour(Colour.BLUE.r, Colour.BLUE.g, Colour.BLUE.b, 1.0D);
        FACE_COLOURS[Direction.WEST.ordinal()] = new Colour(Colour.GREEN.r, Colour.GREEN.g, Colour.GREEN.b, 1.0D);
        FACE_COLOURS[Direction.EAST.ordinal()] = new Colour(Colour.RED.r, Colour.RED.g, Colour.RED.b, 1.0D);
    }

    private RenderUtils() {
    }

    /** @return the marker colour of that face (1.7.10 {@code Utils.FD_COLOURS}). */
    public static Colour faceColour(Direction direction) {
        return FACE_COLOURS[direction.ordinal()];
    }

    /** Draws all six faces of a unit cube with the given per face offsets. */
    public static void drawFaces(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture,
                                 float[] offsets, float scale, int packedLight) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(texture));
        drawFaces(pose, consumer, offsets, scale, packedLight);
    }

    /**
     * Draws all six faces of a unit cube into one consumer. Order is the 1.7.10 order:
     * XNeg, XPos, YNeg, YPos, ZNeg, ZPos.
     */
    public static void drawFaces(PoseStack pose, VertexConsumer consumer, float[] offsets, float scale, int packedLight) {
        drawFace(pose, consumer, Direction.WEST, offsets[0], scale, Colour.WHITE, packedLight);
        drawFace(pose, consumer, Direction.EAST, offsets[1], scale, Colour.WHITE, packedLight);
        drawFace(pose, consumer, Direction.DOWN, offsets[2], scale, Colour.WHITE, packedLight);
        drawFace(pose, consumer, Direction.UP, offsets[3], scale, Colour.WHITE, packedLight);
        drawFace(pose, consumer, Direction.NORTH, offsets[4], scale, Colour.WHITE, packedLight);
        drawFace(pose, consumer, Direction.SOUTH, offsets[5], scale, Colour.WHITE, packedLight);
    }

    /** Draws a single face with the full 0..1 texture. */
    public static void drawFace(PoseStack pose, MultiBufferSource buffers, Direction direction,
                                ResourceLocation texture, float offset, float scale, Colour colour, int packedLight) {
        drawFace(pose, buffers.getBuffer(RenderType.entityTranslucent(texture)), direction, offset, scale, colour, packedLight);
    }

    /** Draws a single face with the full 0..1 texture into one consumer. */
    public static void drawFace(PoseStack pose, VertexConsumer consumer, Direction direction,
                                float offset, float scale, Colour colour, int packedLight) {
        float[] xyz = faceVertices(direction, offset, scale);
        float[] uv = faceUvs(direction);
        float nx = direction.getStepX();
        float ny = direction.getStepY();
        float nz = direction.getStepZ();
        drawQuad(pose, consumer, xyz, uv, (float) colour.r, (float) colour.g, (float) colour.b, (float) colour.a,
                nx, ny, nz, packedLight);
    }

    /**
     * Emits one quad.
     *
     * @param xyz  12 floats: x,y,z for the four corners, in the 1.7.10 vertex order
     * @param uv   8 floats: u,v for the four corners
     */
    public static void drawQuad(PoseStack pose, VertexConsumer consumer, float[] xyz, float[] uv,
                                float r, float g, float b, float a,
                                float nx, float ny, float nz, int packedLight) {
        for (int i = 0; i < 4; i++) {
            consumer.vertex(pose.last().pose(), xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2])
                    .color(r, g, b, a)
                    .uv(uv[i * 2], uv[i * 2 + 1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(packedLight)
                    .normal(nx, ny, nz)
                    .endVertex();
        }
    }

    /** Convenience overload for callers that do not care about the light value (always full bright). */
    public static void drawQuad(PoseStack pose, VertexConsumer consumer, float[] xyz, float[] uv,
                                float r, float g, float b, float a, float nx, float ny, float nz) {
        drawQuad(pose, consumer, xyz, uv, r, g, b, a, nx, ny, nz, LightTexture.FULL_BRIGHT);
    }

    /**
     * The four corners of one face of a unit cube, 1.7.10 {@code BlockRenderer} order and winding.
     */
    public static float[] faceVertices(Direction direction, float offset, float scale) {
        switch (direction) {
            case WEST: // XNeg
                return new float[] {
                        0.0F - offset, 0.0F, 0.0F,
                        0.0F - offset, 0.0F, 1.0F * scale,
                        0.0F - offset, 1.0F * scale, 1.0F * scale,
                        0.0F - offset, 1.0F * scale, 0.0F
                };
            case EAST: // XPos
                return new float[] {
                        1.0F + offset, 0.0F, 1.0F * scale,
                        1.0F + offset, 0.0F, 0.0F,
                        1.0F + offset, 1.0F * scale, 0.0F,
                        1.0F + offset, 1.0F * scale, 1.0F * scale
                };
            case DOWN: // YNeg
                return new float[] {
                        0.0F, 0.0F - offset, 0.0F,
                        1.0F * scale, 0.0F - offset, 0.0F,
                        1.0F * scale, 0.0F - offset, 1.0F * scale,
                        0.0F, 0.0F - offset, 1.0F * scale
                };
            case UP: // YPos
                return new float[] {
                        0.0F, 1.0F + offset, 1.0F * scale,
                        1.0F * scale, 1.0F + offset, 1.0F * scale,
                        1.0F * scale, 1.0F + offset, 0.0F,
                        0.0F, 1.0F + offset, 0.0F
                };
            case NORTH: // ZNeg
                return new float[] {
                        0.0F, 1.0F * scale, 0.0F - offset,
                        1.0F * scale, 1.0F * scale, 0.0F - offset,
                        1.0F * scale, 0.0F, 0.0F - offset,
                        0.0F, 0.0F, 0.0F - offset
                };
            case SOUTH: // ZPos
            default:
                return new float[] {
                        0.0F, 1.0F * scale, 1.0F + offset,
                        0.0F, 0.0F, 1.0F + offset,
                        1.0F * scale, 0.0F, 1.0F + offset,
                        1.0F * scale, 1.0F * scale, 1.0F + offset
                };
        }
    }

    /** The four UV pairs of one face, 1.7.10 {@code BlockRenderer} order. */
    public static float[] faceUvs(Direction direction) {
        switch (direction) {
            case WEST:
                return new float[] { 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F };
            case EAST:
                return new float[] { 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F };
            case DOWN:
                return new float[] { 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F };
            case UP:
                return new float[] { 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F };
            case NORTH:
                return new float[] { 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F };
            case SOUTH:
            default:
                return new float[] { 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F };
        }
    }

    /** 1.7.10: {@code GL11.glColor4d(...)} with every component scaled by the same factor. */
    public static Colour scaled(Colour colour, float level) {
        return new Colour(colour.r * level, colour.g * level, colour.b * level, colour.a * level);
    }
}
