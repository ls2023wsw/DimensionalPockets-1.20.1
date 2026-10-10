package net.gtn.dimensionalpocket.client.renderer;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.gtn.dimensionalpocket.common.lib.Reference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the "endless tunnel" particle field: one texture layer (the tunnel) plus N semi transparent,
 * per layer rotated particle planes, on the six faces of a pocket block (or on the single visible
 * face of a wall connector).
 *
 * <p>1.7.10 origin: {@code client/renderer/PortalRenderer.java} (817 lines of fixed function GL).
 *
 * <h2>What changed in the port</h2>
 * The 1.7.10 renderer drove {@code glTexGen*} / {@code glMatrixMode(GL_TEXTURE)} / {@code glBlendFunc}
 * and kept all of its state in a singleton. 1.20.1 has no texgen and no global matrix stack, so this
 * class is <b>stateless</b>: every value 1.7.10 stored in a field is now a parameter, and the texture
 * coordinates are computed in Java per vertex (报告 03 §4.1 "route A"). Sharing one instance between
 * the pocket renderer, the wall renderer and the item renderer is therefore safe.
 *
 * <p>The upstream texture coordinate pipeline was
 * <pre>
 *   (s, t, r, q) = (planeS·v, planeT·v, planeR·v, planeQ·v_eye)          // glTexGen
 *   (u, v)       = (m00·s + m01·t + m03, m10·s + m11·t + m13) / q        // texture matrix + divide
 * </pre>
 * with the texture matrix built as {@code translate(0, fieldTranslation) · scale(f6) ·
 * translate(0.5, 0.5) · rotateZ(angle) · translate(-0.5, -0.5) · translate(-camera)}. {@code S/T/R}
 * are {@code GL_OBJECT_LINEAR} (they pick two world coordinates) and {@code Q} is
 * {@code GL_EYE_LINEAR}.
 *
 * <p><b>Two deliberate simplifications</b> (both flagged in 报告 03 §4.1 point 3 and §10.2 item 1):
 * <ol>
 *   <li>{@code q} is treated as {@code 1}. That drops the perspective stretch <i>inside</i> a single
 *       quad; every layer stays a uniformly transformed (scaled / rotated / scrolled) plane.
 *       Everything else - layer count, per layer depth, texture scale, rotation angle, colour
 *       sequence, brightness falloff and scroll speed - is identical to 1.7.10.</li>
 *   <li>Of the upstream texture matrix only {@code translate(-camera)} is kept, which is what anchors
 *       the field in world space. The second upstream translate ({@code camera * f5 / depth}) is a
 *       hand tuned fudge that cannot be reproduced without the divide.</li>
 * </ol>
 *
 * <p><b>Blending</b>: upstream drew the tunnel layer with {@code SRC_ALPHA/ONE_MINUS_SRC_ALPHA} and
 * every particle layer with {@code GL_ONE/GL_ONE} (additive). In <b>both</b> cases the brightness
 * lived in the <b>colour</b> and the alpha stayed {@code 1.0}. 1.20.1 has no built in additive
 * {@link RenderType} that accepts an arbitrary texture, so {@link #additiveField(ResourceLocation)}
 * rolls one; the tunnel layer keeps {@link RenderType#entityTranslucent(ResourceLocation)}.
 * Putting the brightness into the alpha instead makes the field nearly invisible: 16 stacked layers
 * of 6%..26% alpha composite to a faint wash instead of adding up.
 */
public final class PortalRenderer {

    /** 1.7.10: {@code PortalRenderer.MAX_BRIGHTNESS}, passed to {@code Tessellator#setBrightness}. */
    public static final int MAX_BRIGHTNESS = 240;

    /** 1.7.10: the shared singleton. Kept because it is immutable, so sharing is safe. */
    public static final PortalRenderer instance = new PortalRenderer();

    /** [0] fancy field, [1] fast + small faces, [2] fast + large faces. */
    public static final ResourceLocation[] fieldTextures = {
            Reference.TEXTURE_PARTICLE_FIELD,
            Reference.TEXTURE_PARTICLE_FIELD_STATIC_SMALL,
            Reference.TEXTURE_PARTICLE_FIELD_STATIC
    };

    /** 1.7.10: {@code PortalRenderer.tunnel}. */
    public static final ResourceLocation tunnel = Reference.TEXTURE_TUNNEL;

    /** 1.7.10 defaults of {@code resetRenderer()}: 16 particle planes + 1 tunnel layer. */
    public static final int DEFAULT_PLANE_COUNT = 17;
    public static final float MAX_PLANE_DEPTH = 16.0F;
    public static final float MIN_PLANE_DEPTH = 1.0F;

    private static final float PLANE_TEXTURE_SCALE = 0.0625F;
    private static final float TUNNEL_TEXTURE_SCALE = 0.125F;
    private static final float SECOND_PLANE_TEXTURE_SCALE = 0.5F;
    private static final float TUNNEL_BRIGHTNESS = 0.1F;

    /** {@code 5000L / 2} for small faces, {@code 5000L / 3} otherwise (upstream {@code updateField}). */
    private static final long SMALL_CYCLE_TICKS = 2500L;
    private static final long LARGE_CYCLE_TICKS = 1666L;

    private PortalRenderer() {
    }

    /** 1.7.10: {@code updateField()}'s texture selection. */
    public static ResourceLocation fieldTexture(boolean fancy, boolean small) {
        return fancy ? fieldTextures[0] : fieldTextures[small ? 1 : 2];
    }

    /** 1.7.10: {@code updateField()}'s {@code fieldTranslation} - the vertical scroll, 0..1. */
    public static float fieldTranslation(long gameTicks, boolean small) {
        long cycleTime = small ? SMALL_CYCLE_TICKS : LARGE_CYCLE_TICKS;
        return gameTicks % cycleTime / (float) cycleTime;
    }

    /**
     * Draws every layer of one face.
     *
     * @param pose           pose whose origin is the <b>local</b> origin of the quads (a block entity
     *                       renderer's pose, i.e. the block origin)
     * @param face           which face to draw
     * @param offset         push along the face normal, exactly as 1.7.10 (0.001 / 0.999 for a pocket
     *                       block, 0.0 / 1.0 for a wall connector)
     * @param scale          edge length of the quads (1 for a pocket block, 14 for a wall connector)
     * @param fancy          1.7.10 {@code Reference.useFancyField()}; {@code false} draws one quad only
     * @param small          1.7.10 {@code updateField(small)}: {@code true} for small faces (2500 tick
     *                       cycle, {@code particleFieldStaticSmall} in fast mode)
     * @param particlePlanes 1.7.10 {@code Reference.numberOfParticlePlanes()} (1..50)
     * @param fieldOrigin    world position of the local origin, needed for the world anchored texture
     *                       coordinates (1.7.10 passed this through {@code setInterpolatedPosition})
     * @param gameTicks      monotonic tick counter (1.7.10 {@code ClientEventHandler.gameTicks})
     * @param seed           per face seed (1.7.10 {@code TileRendererPocket.seeds[face]})
     */
    public void renderFace(PoseStack pose, MultiBufferSource buffers, Direction face, float offset, float scale,
                           boolean fancy, boolean small, int particlePlanes, Vec3 fieldOrigin,
                           long gameTicks, long seed) {
        float[] xyz = planeVertices(face, offset, scale);
        float nx = face.getStepX();
        float ny = face.getStepY();
        float nz = face.getStepZ();

        if (!fancy) {
            // 1.7.10 fast path: one quad with the static texture, no layers at all.
            // The upstream call passed a negated offset (and "-1 + offset" for the positive faces) into
            // BlockRenderer, whose vertex table uses "0 - offset" / "1 + offset"; both negations cancel
            // out, so the same planeVertices() table is used here.
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(fieldTexture(false, small)));
            float[] uv = worldUvs(face, xyz, fieldOrigin, 1.0F, 0.0F, 0.0F);
            emitQuad(pose, consumer, xyz, uv, 1.0F, 1.0F, 1.0F, 1.0F, nx, ny, nz);
            return;
        }

        // 1.7.10: overridePlaneCount(n) stores n + 1 - one extra layer for the tunnel.
        int planes = particlePlanes + 1;
        float planeDepthIncrement = (MAX_PLANE_DEPTH - MIN_PLANE_DEPTH) / planes;
        ResourceLocation fieldTexture = fieldTexture(true, small);
        float fieldTranslation = fieldTranslation(gameTicks, small);
        RandomSource random = RandomSource.create(seed);

        int i = -1;
        for (float depthDecrease = 0.0F; i < planes; depthDecrease += planeDepthIncrement) {
            i++;

            float planeDepth = MAX_PLANE_DEPTH - depthDecrease;
            float textureScale = PLANE_TEXTURE_SCALE;
            float brightness = 1.0F / (planeDepth + 1.0F);

            ResourceLocation texture;
            if (i == 0) {
                // Tunnel layer: depth 65 and brightness 0.1 upstream, both only used for the falloff.
                texture = tunnel;
                brightness = TUNNEL_BRIGHTNESS;
                textureScale = TUNNEL_TEXTURE_SCALE;
            } else if (i == 1) {
                texture = fieldTexture;
                textureScale = SECOND_PLANE_TEXTURE_SCALE;
            } else {
                texture = fieldTexture;
            }

            // Upstream draws three random values per layer, in this order, even for the tunnel layer.
            float red = random.nextFloat() * 0.5F + 0.1F;
            float green = random.nextFloat() * 0.5F + 0.4F;
            float blue = random.nextFloat() * 0.5F + 0.5F;
            if (i == 0) {
                red = 1.0F;
                green = 1.0F;
                blue = 1.0F;
            }

            // 1.7.10: the tunnel layer used SRC_ALPHA/ONE_MINUS_SRC_ALPHA, every particle layer used
            // GL_ONE/GL_ONE (additive); the brightness is carried by the colour and the alpha is 1.0.
            RenderType renderType = i == 0 ? RenderType.entityTranslucent(texture) : additiveField(texture);
            float angleDegrees = (i * i * 4321 + i * 9) * 2.0F;
            VertexConsumer consumer = buffers.getBuffer(renderType);
            float[] uv = worldUvs(face, xyz, fieldOrigin, textureScale, angleDegrees, fieldTranslation);
            emitQuad(pose, consumer, xyz, uv, red * brightness, green * brightness, blue * brightness, 1.0F,
                    nx, ny, nz);
        }
    }

    /**
     * The shader {@link RenderType#entityTranslucent(ResourceLocation)} uses. Vanilla keeps every
     * state shard {@code protected} on {@link RenderStateShard}, so they have to be built here
     * instead of borrowed from {@link RenderType}.
     */
    private static final RenderStateShard.ShaderStateShard ENTITY_TRANSLUCENT_SHADER =
            new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntityTranslucentShader);

    /** 1.7.10 {@code glBlendFunc(1, 1)}, i.e. {@code GL_ONE/GL_ONE}. */
    private static final RenderStateShard.TransparencyStateShard ADDITIVE_TRANSPARENCY =
            new RenderStateShard.TransparencyStateShard("dimensionalpockets_additive_transparency",
                    () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
                    },
                    () -> {
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    });

    /**
     * Additive counterpart of {@link RenderType#entityTranslucent(ResourceLocation)}: same vertex
     * format, shader, cull / lightmap / overlay state and write mask, only the blending differs.
     * The {@code COLOR_WRITE} mask matters here - vanilla's translucent types never write depth, and
     * an additive layer that did would cull whatever is drawn after it. The composite state is
     * immutable, so the cache is purely an allocation saver - one entry per field texture.
     */
    private static final Map<ResourceLocation, RenderType> ADDITIVE_FIELD = new HashMap<>();

    private static RenderType additiveField(ResourceLocation texture) {
        return ADDITIVE_FIELD.computeIfAbsent(texture, t -> RenderType.create(
                "dimensionalpockets_particle_field",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                true,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(t, false, true))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(new RenderStateShard.CullStateShard(true))
                        .setLightmapState(new RenderStateShard.LightmapStateShard(true))
                        .setOverlayState(new RenderStateShard.OverlayStateShard(true))
                        .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, false))
                        .createCompositeState(true)));
    }

    private static void emitQuad(PoseStack pose, VertexConsumer consumer, float[] xyz, float[] uv,
                                 float r, float g, float b, float a, float nx, float ny, float nz) {
        for (int corner = 0; corner < 4; corner++) {
            consumer.vertex(pose.last().pose(), xyz[corner * 3], xyz[corner * 3 + 1], xyz[corner * 3 + 2])
                    .color(r, g, b, a)
                    .uv(uv[corner * 2], uv[corner * 2 + 1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .normal(nx, ny, nz)
                    .endVertex();
        }
    }

    /**
     * The four (u, v) pairs of one layer: world anchored plane equations, then the upstream texture
     * matrix {@code translate(0, fieldTranslation) · scale(textureScale) · translate(0.5, 0.5) ·
     * rotateZ(angle) · translate(-0.5, -0.5)}. The {@code translate(-camera)} term is folded into the
     * plane equations below (they use camera relative world coordinates).
     */
    private static float[] worldUvs(Direction face, float[] xyz, Vec3 fieldOrigin, float textureScale,
                                    float angleDegrees, float fieldTranslation) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float[] uv = new float[8];
        double radians = Math.toRadians(angleDegrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);

        for (int corner = 0; corner < 4; corner++) {
            double worldX = fieldOrigin.x + xyz[corner * 3];
            double worldY = fieldOrigin.y + xyz[corner * 3 + 1];
            double worldZ = fieldOrigin.z + xyz[corner * 3 + 2];

            // The S / T plane equations of the 1.7.10 drawFace* methods.
            float s;
            float t;
            switch (face) {
                case WEST:
                case EAST:
                    s = (float) (worldZ - camera.z);
                    t = (float) (worldY - camera.y);
                    break;
                case DOWN:
                case UP:
                    s = (float) (worldX - camera.x);
                    t = (float) (worldZ - camera.z);
                    break;
                case NORTH:
                case SOUTH:
                default:
                    s = (float) (worldX - camera.x);
                    t = (float) (worldY - camera.y);
                    break;
            }

            float px = s - 0.5F;
            float py = t - 0.5F;
            float rx = px * cos - py * sin;
            float ry = px * sin + py * cos;
            uv[corner * 2] = (rx + 0.5F) * textureScale;
            uv[corner * 2 + 1] = (ry + 0.5F) * textureScale + fieldTranslation;
        }
        return uv;
    }

    /**
     * The four corners of one face, in the 1.7.10 {@code PortalRenderer.drawFace*} order. All values
     * are relative to the local origin (1.7.10 used the interpolated block position as its origin).
     */
    public static float[] planeVertices(Direction face, float offset, float scale) {
        switch (face) {
            case WEST: // XNeg
                return new float[] {
                        offset, 0.0F, 0.0F,
                        offset, 0.0F, scale,
                        offset, scale, scale,
                        offset, scale, 0.0F
                };
            case EAST: // XPos
                return new float[] {
                        offset, scale, 0.0F,
                        offset, scale, scale,
                        offset, 0.0F, scale,
                        offset, 0.0F, 0.0F
                };
            case DOWN: // YNeg
                return new float[] {
                        0.0F, offset, scale,
                        0.0F, offset, 0.0F,
                        scale, offset, 0.0F,
                        scale, offset, scale
                };
            case UP: // YPos
                return new float[] {
                        0.0F, offset, 0.0F,
                        0.0F, offset, scale,
                        scale, offset, scale,
                        scale, offset, 0.0F
                };
            case NORTH: // ZNeg
                return new float[] {
                        0.0F, 0.0F, offset,
                        0.0F, scale, offset,
                        scale, scale, offset,
                        scale, 0.0F, offset
                };
            case SOUTH: // ZPos
            default:
                return new float[] {
                        0.0F, scale, offset,
                        0.0F, 0.0F, offset,
                        scale, 0.0F, offset,
                        scale, scale, offset
                };
        }
    }
}
