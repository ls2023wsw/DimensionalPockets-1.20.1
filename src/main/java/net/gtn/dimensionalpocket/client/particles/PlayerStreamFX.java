package net.gtn.dimensionalpocket.client.particles;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.gtn.dimensionalpocket.client.utils.UtilsFX;
import net.gtn.dimensionalpocket.common.core.utils.Colour;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The particle stream that pulls a player into a pocket: a 20 frame lightning ball animation (0.5 s
 * per cycle) that flies from the player to the pocket block within {@code ticksToTake} ticks.
 *
 * <p>1.7.10 origin: {@code client/particles/PlayerStreamFX.java} ({@code EntityFX}; it bound one of 20
 * textures per particle and restored the vanilla particle atlas afterwards). 1.20.1 particles are
 * batched per {@link ParticleRenderType}, so this particle asks {@link UtilsFX} for the render type of
 * the current animation frame - the engine groups by render type, which restores the "one texture per
 * frame" behaviour without any atlas juggling.
 *
 * <p>Frame timing uses {@link Util#getMillis()} (25 ms per frame), matching the upstream
 * {@code System.nanoTime() % 500000000 / 25ms}; a tick based counter would run at half speed.
 */
public class PlayerStreamFX extends Particle {

    /** 1.7.10: {@code private static final long frames = 20}. */
    public static final int FRAMES = 20;
    private static final long CYCLE_MILLIS = 500L;
    private static final long FRAME_MILLIS = CYCLE_MILLIS / FRAMES;

    /** 1.7.10: {@code particleScale = 1.5F} multiplied by {@code 0.1F} while rendering. */
    private static final float SIZE = 1.5F * 0.1F;

    private final double motionX;
    private final double motionY;
    private final double motionZ;
    private final long frameOffset;
    private final int maxAge;

    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;

    private int age;

    /**
     * @param level       client level
     * @param player      the player being pulled in
     * @param target      the pocket block the stream flies to (1.7.10 {@code CoordSet} of the block)
     * @param ticksToTake ticks until the teleport happens (1.7.10 constant: 15)
     * @param random      shared random of the spawning call
     * @param colour      stream colour (1.7.10 always passed {@link Colour#WHITE})
     */
    public PlayerStreamFX(ClientLevel level, Player player, BlockPos target, int ticksToTake, RandomSource random,
                          Colour colour) {
        super(level,
                player.getX() + (random.nextDouble() - 0.5D) * 2.0D,
                player.getY() + player.getBbHeight() / 4.0F - random.nextDouble() * 1.1D,
                player.getZ() + (random.nextDouble() - 0.5D) * 2.0D);

        this.maxAge = ticksToTake;
        this.frameOffset = random.nextInt(FRAMES);

        // 1.7.10: motion = (target + 0.5 - pos) / ticksToTake, applied once per tick, so the particle
        // arrives exactly when the teleport fires.
        this.motionX = (target.getX() + 0.5D - this.x) / ticksToTake;
        this.motionY = (target.getY() + 0.5D - this.y) / ticksToTake;
        this.motionZ = (target.getZ() + 0.5D - this.z) / ticksToTake;

        Colour copy = colour == null ? Colour.WHITE : colour;
        this.red = (float) copy.r;
        this.green = (float) copy.g;
        this.blue = (float) copy.b;
        this.alpha = (float) copy.a;
    }

    @Override
    public void tick() {
        // 1.7.10 onUpdate: prevPos = pos, then moveEntity(motion) - no gravity, no collision.
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.setPos(this.x + this.motionX, this.y + this.motionY, this.z + this.motionZ);

        if (++this.age >= this.maxAge) {
            this.remove();
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return UtilsFX.frameRenderType(frameNumber());
    }

    /** 1.7.10 frame maths, ported to milliseconds: 1..20, wrapped, plus the per particle offset. */
    public static int frameNumber(long millis, long frameOffset) {
        long frame = (millis % CYCLE_MILLIS) / FRAME_MILLIS + 1L + frameOffset;
        if (frame > FRAMES) {
            frame %= FRAMES;
        }
        return (int) Mth.clamp(frame, 1L, FRAMES);
    }

    private int frameNumber() {
        return frameNumber(Util.getMillis(), this.frameOffset);
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTick) {
        Vec3 cameraPos = camera.getPosition();
        float ix = (float) (Mth.lerp(partialTick, this.xo, this.x) - cameraPos.x());
        float iy = (float) (Mth.lerp(partialTick, this.yo, this.y) - cameraPos.y());
        float iz = (float) (Mth.lerp(partialTick, this.zo, this.z) - cameraPos.z());

        Quaternionf rotation = camera.rotation();
        Vector3f[] corners = {
                new Vector3f(-1.0F, -1.0F, 0.0F),
                new Vector3f(-1.0F, 1.0F, 0.0F),
                new Vector3f(1.0F, 1.0F, 0.0F),
                new Vector3f(1.0F, -1.0F, 0.0F)
        };
        for (Vector3f corner : corners) {
            corner.rotate(rotation);
            corner.mul(SIZE);
            corner.add(ix, iy, iz);
        }

        // 1.7.10 UV order: (0,1) (1,1) (1,0) (0,0), brightness 240 (LightTexture.FULL_BRIGHT).
        emit(consumer, corners[0], 0.0F, 1.0F);
        emit(consumer, corners[1], 1.0F, 1.0F);
        emit(consumer, corners[2], 1.0F, 0.0F);
        emit(consumer, corners[3], 0.0F, 0.0F);
    }

    private void emit(VertexConsumer consumer, Vector3f corner, float u, float v) {
        consumer.vertex(corner.x(), corner.y(), corner.z())
                .uv(u, v)
                .color(this.red, this.green, this.blue, this.alpha)
                .uv2(LightTexture.FULL_BRIGHT)
                .endVertex();
    }
}
