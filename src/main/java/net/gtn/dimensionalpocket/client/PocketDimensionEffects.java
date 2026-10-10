package net.gtn.dimensionalpocket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Sky / fog / cloud look of the pocket dimension.
 *
 * <p>1.7.10 origin: {@code common/core/WorldProviderPocket.java} (124 lines), which declared
 * {@code hasNoSky = true} plus an empty sky renderer, black sky colour, black fog colour, no stars,
 * no clouds and a cloud height of {@code 600000F}.
 *
 * <p>1.20.1 has no {@code WorldProvider}, so those client side hooks live here. Everything that was a
 * data-only flag (no weather, no lightning, no respawn, no skylight) is declared in
 * {@code data/dimensionalpockets/dimension_type/pocket.json} and
 * {@code data/dimensionalpockets/worldgen/biome/pocket.json} instead.
 *
 * <p>The internal id registered below must stay identical to the {@code effects} field of that
 * dimension_type json ({@code dimensionalpockets:pocket}).
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class PocketDimensionEffects extends DimensionSpecialEffects {

    /** 1.7.10 {@code getCloudHeight()} returned this, effectively pushing clouds out of sight. */
    private static final float CLOUD_HEIGHT = 600000.0F;

    public PocketDimensionEffects() {
        // cloudLevel, hasGround=false, no sky, no forced bright lightmap, constant ambient light
        super(CLOUD_HEIGHT, false, SkyType.NONE, false, true);
    }

    @SubscribeEvent
    public static void onRegisterDimensionSpecialEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Reference.POCKET_LEVEL_ID, new PocketDimensionEffects());
    }

    /** 1.7.10 {@code getFogColor} returned (0,0,0); black fog, no day/night tinting. */
    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float brightness) {
        return Vec3.ZERO;
    }

    /** 1.7.10 {@code getWorldHasVoidParticles} was false, and the pocket has no weather at all. */
    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    /** 1.7.10 {@code calcSunriseSunsetColors} was all zeroes; null is the 1.20.1 "no sunrise colour". */
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null;
    }

    /** 1.7.10 {@code getCloudHeight()} == 600000F. */
    @Override
    public float getCloudHeight() {
        return CLOUD_HEIGHT;
    }

    /** 1.7.10 installed an empty sky renderer: nothing is drawn, vanilla sky is skipped. */
    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera,
            Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        return true;
    }

    /** 1.7.10 {@code drawClouds} returned (0,0,0): clouds are never drawn. */
    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
            double camX, double camY, double camZ, Matrix4f projectionMatrix) {
        return true;
    }

    /** No weather in the pocket dimension (1.7.10 {@code canDoRainSnowIce} was false). */
    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick,
            net.minecraft.client.renderer.LightTexture lightTexture, double camX, double camY, double camZ) {
        return true;
    }
}
