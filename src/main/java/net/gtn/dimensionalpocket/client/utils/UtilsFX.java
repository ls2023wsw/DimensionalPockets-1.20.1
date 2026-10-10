package net.gtn.dimensionalpocket.client.utils;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;

import net.gtn.dimensionalpocket.client.lib.TextureMaps;
import net.gtn.dimensionalpocket.client.particles.PlayerStreamFX;
import net.gtn.dimensionalpocket.common.core.utils.Colour;
import net.gtn.dimensionalpocket.common.core.utils.CoordSet;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * Particle helpers: the texture lookup for the 20 frame lightning ball animation, one
 * {@link ParticleRenderType} per frame (so the vanilla particle engine can batch them), and the
 * "player is being pulled in" spawner.
 *
 * <p>1.7.10 origin: {@code client/utils/UtilsFX.java}. Dropped from the upstream version:
 * {@code bindTexture(String)} (1.20.1 has no global texture binding - the render type owns the
 * texture) and {@code getParticleTexture()} (a reflection hack to restore the vanilla particle atlas
 * after drawing; unnecessary now).
 */
public final class UtilsFX {

    /** {@code Strings.DIMENSIONAL_POCKET_PARTICLE} frames: 1..20. */
    public static final int PARTICLE_FRAMES = 20;

    private static final ParticleRenderType[] FRAME_RENDER_TYPES = new ParticleRenderType[PARTICLE_FRAMES];

    private UtilsFX() {
    }

    /** @param frame 1 based animation frame (values outside 1..20 are clamped). */
    public static ResourceLocation particleTexture(long frame) {
        long clamped = Mth.clamp((int) frame, 1, PARTICLE_FRAMES);
        return new ResourceLocation(Reference.MOD_ID,
                TextureMaps.TEXTURE_MISC_ROOT + Reference.PARTICLE_TEXTURE_PREFIX + clamped + ".png");
    }

    /**
     * Render type of one animation frame. All 20 instances are created up front and reused, because
     * the particle engine groups particles by the identity of the returned render type - creating a
     * fresh instance per particle would defeat the batching.
     */
    public static ParticleRenderType frameRenderType(int frame) {
        int index = (int) Mth.clamp(frame - 1L, 0L, PARTICLE_FRAMES - 1L);
        ParticleRenderType renderType = FRAME_RENDER_TYPES[index];
        if (renderType == null) {
            renderType = new FrameRenderType(particleTexture(frame));
            FRAME_RENDER_TYPES[index] = renderType;
        }
        return renderType;
    }

    /** 1.7.10 signature: the pocket block's coordinates as a {@link CoordSet}. */
    public static void createPlayerStream(Player player, CoordSet targetSet, int ticksToTake) {
        if (targetSet == null) {
            ModLog.deny("UtilsFX", "player_stream", "reason=target_null actual=null");
            return;
        }
        createPlayerStream(player, targetSet.toBlockPos(), ticksToTake);
    }

    /**
     * Spawns the particle stream of a player being pulled into a pocket.
     *
     * <p>1.7.10 spawned {@code 5 * (4 - particleSetting)} particles (20 / 15 / 10 for the three
     * particle settings) from {@code TileDimensionalPocket#prepareTeleportIntoPocket}. The 1.20.1
     * {@code TileDimensionalPocket} only prepares the teleport on the server, so the caller has to
     * reach this method through {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)}.
     */
    public static void createPlayerStream(Player player, BlockPos target, int ticksToTake) {
        if (player == null || target == null) {
            ModLog.deny("UtilsFX", "player_stream", "reason=null_argument player={} target={}", player, target);
            return;
        }
        if (!(player.level() instanceof ClientLevel level)) {
            ModLog.deny("UtilsFX", "player_stream", "reason=not_client_level side=SERVER actual={}",
                    player.level() == null ? "null" : player.level().dimension());
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.particleEngine == null) {
            ModLog.deny("UtilsFX", "player_stream", "reason=particle_engine_null actual=null");
            return;
        }

        int particleSetting = minecraft.options.particles().get().getId();
        int numParticles = 5 * (4 - particleSetting);
        RandomSource random = RandomSource.create();
        for (int i = 0; i < numParticles; i++) {
            minecraft.particleEngine.add(new PlayerStreamFX(level, player, target, ticksToTake, random, Colour.WHITE));
        }
        ModLog.trace("UtilsFX", "player_stream", "count={} ticks={} setting={} target={}",
                numParticles, ticksToTake, particleSetting, target);
    }

    /**
     * 1.7.10 blending: {@code glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)} with depth writes off. The
     * texture is the frame's own file (not the particle atlas), which is exactly what the upstream
     * per particle {@code bindTexture} did.
     */
    private static final class FrameRenderType implements ParticleRenderType {

        private final ResourceLocation texture;

        private FrameRenderType(ResourceLocation texture) {
            this.texture = texture;
        }

        @Override
        public void begin(BufferBuilder builder, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, this.texture);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        public void end(Tesselator tesselator) {
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }

        @Override
        public String toString() {
            return "dimensionalpockets:particle_frame[" + this.texture + "]";
        }
    }
}
