package net.gtn.dimensionalpocket.client.renderer.tile;

import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;

import net.gtn.dimensionalpocket.client.event.ClientEventHandler;
import net.gtn.dimensionalpocket.client.event.RenderEventHandler;
import net.gtn.dimensionalpocket.client.lib.IColourBlindTexture;
import net.gtn.dimensionalpocket.client.renderer.PortalRenderer;
import net.gtn.dimensionalpocket.client.theme.PocketTextures;
import net.gtn.dimensionalpocket.client.utils.RenderUtils;
import net.gtn.dimensionalpocket.common.core.DPConfig;
import net.gtn.dimensionalpocket.common.core.pocket.Pocket;
import net.gtn.dimensionalpocket.common.core.pocket.PocketSideState;
import net.gtn.dimensionalpocket.common.core.utils.Colour;
import net.gtn.dimensionalpocket.common.tileentity.TileDimensionalPocket;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Block entity renderer of the Dimensional Pocket block: the six sided particle field, the outer
 * frame, and - while the player holds a nether crystal - the per face direction indicator and the
 * face state overlay.
 *
 * <p>1.7.10 origin: {@code client/renderer/tile/TileRendererPocket.java}
 * ({@code TileEntitySpecialRenderer}). The 1.20.1 {@code BlockEntityRenderer#render} has no
 * {@code x/y/z} parameters: the {@link PoseStack} origin is already the block origin, so every
 * upstream {@code glTranslated(x, y, z)} simply disappears.
 *
 * <p><b>Distance behaviour (must be preserved)</b>: 1.7.10 gated the field, the indicators and the
 * overlays behind a 32 block radius, but still drew the outer frame from further away.
 * {@link BlockEntityRenderer#getViewDistance()} would remove the block entity entirely beyond its
 * value, so it is set to {@link #VIEW_DISTANCE} (64, the vanilla default) and the 32 block check is
 * done inside {@link #render} - otherwise far away pocket blocks would lose their frame too.
 */
public class TileRendererPocket implements BlockEntityRenderer<TileDimensionalPocket> {

    /** 1.7.10: {@code TileRendererPocket.maxBrightness} (unused upstream, kept for parity). */
    public static final int maxBrightness = 240;

    /** 1.7.10: {@code maxDistance = 32.0} - field / indicators / overlays only within this radius. */
    public static final double FIELD_RANGE = 32.0D;

    /** Block entity render distance, in blocks. Large enough to keep the frame visible. */
    public static final int VIEW_DISTANCE = 64;

    /**
     * Per face seeds, so every face shows a different colour sequence while staying stable for the
     * whole session (1.7.10: one random base multiplied by the face index 1..6).
     */
    public static final long[] seeds = new long[6];

    static {
        Random random = new Random();
        long base = random.nextLong() / 6L;
        for (int i = 1; i <= 6; i++) {
            seeds[i - 1] = base * i;
        }
    }

    /** Per face offsets of the particle field, in the 1.7.10 face order DOWN, UP, NORTH, SOUTH, WEST, EAST. */
    private static final float[] FIELD_OFFSETS = { 0.001F, 0.999F, 0.001F, 0.999F, 0.001F, 0.999F };

    /**
     * 1.7.10 shared render state: written by {@code ClientPlayerTickEventHandler} (nether crystal in
     * hand) and read by both block entity renderers. Kept as a static field exactly like upstream -
     * the contract's file list has no room for a separate state class.
     */
    public static boolean doIndicateSides = false;

    protected final PortalRenderer portalRenderer = PortalRenderer.instance;

    /** Breathing brightness of the face state overlays (1.7.10 {@code updateStateColorLevel}). */
    protected float stateColorLevel;

    private boolean inRange;

    public TileRendererPocket(BlockEntityRendererProvider.Context context) {
        // The context is unused: all textures are bound through RenderType.
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public void render(TileDimensionalPocket blockEntity, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        this.inRange = Vec3.atCenterOf(blockEntity.getBlockPos()).closerThan(camera, FIELD_RANGE);

        boolean isInGui = isRenderingGui(minecraft);
        boolean fancy = DPConfig.CFG.useFancyField();
        boolean colorBlind = DPConfig.colorBlindMode();

        pose.pushPose();

        if (!isInGui) {
            renderField(blockEntity, pose, buffers, fancy);
        }

        // Outer frame: all six faces, drawn with the full 0..1 texture (1.7.10 BlockRenderer.drawFaces).
        RenderUtils.drawFaces(pose, buffers, PocketTextures.pocketFrame, RenderUtils.DEFAULT_OFFSETS, 1.0F,
                LightTexture.FULL_BRIGHT);

        if (!inRange) {
            ModLog.trace("RenderPocket", "field_range", "pos={} reason=out_of_range actual={} expected<={}",
                    blockEntity.getBlockPos(), String.format("%.1f", Math.sqrt(camera.distanceToSqr(Vec3.atCenterOf(blockEntity.getBlockPos())))),
                    FIELD_RANGE);
            pose.popPose();
            return;
        }

        if (doIndicateSides) {
            renderIndicators(pose, buffers, colorBlind);
            renderStateOverlays(blockEntity, pose, buffers, colorBlind);
        }

        pose.popPose();
    }

    /** The six particle field faces, one seed and one offset each. */
    protected void renderField(TileDimensionalPocket blockEntity, PoseStack pose, MultiBufferSource buffers, boolean fancy) {
        Vec3 origin = Vec3.atLowerCornerOf(blockEntity.getBlockPos());
        long gameTicks = ClientEventHandler.gameTicks;
        int planes = DPConfig.CFG.numberOfParticlePlanes();

        Direction[] order = Direction.values(); // DOWN, UP, NORTH, SOUTH, WEST, EAST
        for (int face = 0; face < order.length; face++) {
            portalRenderer.renderFace(pose, buffers, order[face], FIELD_OFFSETS[face], 1.0F,
                    fancy, true, planes, origin, gameTicks, seeds[face]);
        }
    }

    protected void renderIndicators(PoseStack pose, MultiBufferSource buffers, boolean colorBlind) {
        for (Direction direction : Direction.values()) {
            IColourBlindTexture indicator = PocketTextures.sideIndicatorFor(direction);
            if (indicator == null) {
                ModLog.trace("RenderPocket", "indicator_missing", "direction={} reason=no_texture", direction);
                continue;
            }
            RenderUtils.drawFace(pose, buffers, direction, indicator.getTexture(false), 0.0003F, 1.0F,
                    RenderUtils.faceColour(direction), LightTexture.FULL_BRIGHT);
            if (colorBlind) {
                RenderUtils.drawFace(pose, buffers, direction, indicator.getTexture(true), 0.0003F, 1.0F,
                        Colour.WHITE, LightTexture.FULL_BRIGHT);
            }
        }
    }

    protected void renderStateOverlays(TileDimensionalPocket blockEntity, PoseStack pose, MultiBufferSource buffers,
                                       boolean colorBlind) {
        Pocket pocket = blockEntity.getPocket();
        if (pocket == null) {
            ModLog.trace("RenderPocket", "overlay_skip", "pos={} reason=pocket_null side=CLIENT",
                    blockEntity.getBlockPos());
            return;
        }

        updateStateColorLevel();

        for (Direction direction : Direction.values()) {
            PocketSideState state = pocket.getFlowState(direction);
            IColourBlindTexture texture = PocketTextures.overlayFor(state);
            if (texture == null) {
                // NONE has no overlay texture upstream - that face simply draws nothing.
                continue;
            }
            RenderUtils.drawFace(pose, buffers, direction, texture.getTexture(colorBlind), 0.0002F, 1.0F,
                    RenderUtils.scaled(state.getColour(), stateColorLevel), LightTexture.FULL_BRIGHT);
        }
    }

    /** 1.7.10: 1.337 s sine breathing between 0.5 and 1.0. */
    protected void updateStateColorLevel() {
        long colorCycleTime = 1337000000L;
        double minColorLevel = 0.5D;
        stateColorLevel = (float) (minColorLevel + (1 - minColorLevel)
                * Math.sin(System.nanoTime() % colorCycleTime * Math.PI / colorCycleTime));
    }

    /**
     * 1.7.10 {@code RenderEventHandler.isRenderingGUI}. The upstream event pair still exists, but
     * {@code Minecraft.getInstance().screen} is the reliable signal on the 1.20.1 item render path
     * (报告 03 §2.4), so both are checked.
     */
    protected static boolean isRenderingGui(Minecraft minecraft) {
        return minecraft.screen != null || RenderEventHandler.isRenderingGUI;
    }
}
