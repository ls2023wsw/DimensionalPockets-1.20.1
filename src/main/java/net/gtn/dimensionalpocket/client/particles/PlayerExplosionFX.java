package net.gtn.dimensionalpocket.client.particles;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.gtn.dimensionalpocket.client.utils.UtilsFX;
import net.gtn.dimensionalpocket.common.core.utils.Colour;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * "Player exploded into the pocket" particle.
 *
 * <p>1.7.10 origin: {@code client/particles/PlayerExplosionFX.java}, which was marked
 * {@code TO BE DECIDED} by its author and had <b>no call site at all</b> (报告 03 §6.3). It is ported
 * for parity with the file list, not because anything spawns it: use {@link #spawn} if the effect is
 * ever wanted.
 *
 * <p>Kept behaviour: spawns in a tight cloud around the player, uses one of the first three lightning
 * ball frames (chosen once, at spawn), and moves towards a fixed target at a tenth of the distance per
 * tick - upstream that target was the hard coded {@code (16, 16, 16)}, i.e. the pocket room centre.
 */
public class PlayerExplosionFX extends Particle {

    /** 1.7.10 hard coded motion target: the pocket room centre. */
    public static final double DEFAULT_TARGET_X = 16.0D;
    public static final double DEFAULT_TARGET_Y = 16.0D;
    public static final double DEFAULT_TARGET_Z = 16.0D;

    /** Upstream used {@code particleScale * 0.1F} with the default scale of 1.0. */
    private static final float SIZE = 0.1F;

    private final double motionX;
    private final double motionY;
    private final double motionZ;
    private final int frameNumber;

    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;

    private int age;
    private final int maxAge;

    public PlayerExplosionFX(ClientLevel level, Player player, RandomSource random, Colour colour,
                             double targetX, double targetY, double targetZ, int maxAge) {
        super(level,
                player.getX() + (random.nextDouble() - 0.5D) * 0.5D,
                player.getY() - random.nextDouble() * 1.1D,
                player.getZ() + (random.nextDouble() - 0.5D) * 0.5D);

        // 1.7.10: motion = (16 - pos) / 10, using the upstream default particle age.
        this.motionX = (targetX - this.x) / 10.0D;
        this.motionY = (targetY - this.y) / 10.0D;
        this.motionZ = (targetZ - this.z) / 10.0D;

        this.frameNumber = random.nextInt(3) + 1;
        this.maxAge = maxAge;

        Colour copy = colour == null ? Colour.WHITE : colour;
        this.red = (float) copy.r;
        this.green = (float) copy.g;
        this.blue = (float) copy.b;
        this.alpha = (float) copy.a;
    }

    /** Convenience spawner with the upstream defaults (room centre target, 10 tick life). */
    public static void spawn(Player player, RandomSource random, Colour colour) {
        if (player == null || !(player.level() instanceof ClientLevel level)) {
            ModLog.deny("PlayerExplosionFX", "spawn", "reason=no_client_player actual={}",
                    player == null ? "null_player" : "server_side");
            return;
        }
        Minecraft.getInstance().particleEngine.add(new PlayerExplosionFX(level, player, random, colour,
                DEFAULT_TARGET_X, DEFAULT_TARGET_Y, DEFAULT_TARGET_Z, 10));
    }

    @Override
    public void tick() {
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
        return UtilsFX.frameRenderType(this.frameNumber);
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
