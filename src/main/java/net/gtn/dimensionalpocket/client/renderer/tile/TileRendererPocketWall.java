package net.gtn.dimensionalpocket.client.renderer.tile;

import com.mojang.blaze3d.vertex.PoseStack;

import net.gtn.dimensionalpocket.client.event.ClientEventHandler;
import net.gtn.dimensionalpocket.client.lib.IColourBlindTexture;
import net.gtn.dimensionalpocket.client.renderer.PortalRenderer;
import net.gtn.dimensionalpocket.client.theme.PocketTextures;
import net.gtn.dimensionalpocket.client.utils.RenderUtils;
import net.gtn.dimensionalpocket.common.core.DPConfig;
import net.gtn.dimensionalpocket.common.core.pocket.Pocket;
import net.gtn.dimensionalpocket.common.core.pocket.PocketSideState;
import net.gtn.dimensionalpocket.common.core.utils.Colour;
import net.gtn.dimensionalpocket.common.core.utils.CoordSet;
import net.gtn.dimensionalpocket.common.tileentity.TileDimensionalPocketWallConnector;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Block entity renderer of a wall connector: <b>one</b> face only (the face of the wall the connector
 * sits on), the particle field with {@code scale = 14}, the inner frame, the connector icon with its
 * background, and - while the player holds a nether crystal - the direction indicator and the face
 * state overlay with {@code scale = 16}.
 *
 * <p>1.7.10 origin: {@code client/renderer/tile/TileRendererPocketWall.java}, which <i>extended</i>
 * {@code TileRendererPocket}. In 1.20.1 the two renderers have different block entity type
 * parameters, so this class is standalone and reuses {@link TileRendererPocket}'s public statics
 * ({@link TileRendererPocket#seeds}, {@link TileRendererPocket#doIndicateSides}) instead of inheriting
 * them.
 *
 * <p>All the 1.7.10 {@code x/y/z} terms (which were camera relative block coordinates) drop out: the
 * {@link PoseStack} already starts at the block origin. The {@code glTranslatef(-ox, -oy, -oz)} /
 * {@code glTranslatef(-offX, -offY, -offZ)} calls are kept, because they shift the origin inside the
 * pocket room's 16x16x16 volume.
 */
public class TileRendererPocketWall implements BlockEntityRenderer<TileDimensionalPocketWallConnector> {

    /** {@code pocketFrameInside.png} is 224x224 = 14 blocks of 16px, hence the 14x scale. */
    private static final float FRAME_SCALE = 14.0F;

    /** Indicator / overlay textures are 32x32 and cover the whole 16 block wall face. */
    private static final float WALL_SCALE = 16.0F;

    private final PortalRenderer portalRenderer = PortalRenderer.instance;

    private final Colour connectorBaseColor;
    private final Colour connectorColor;
    private final Colour connectorBGColour;

    /** Breathing brightness of the face state overlays (1.7.10 inherited from TileRendererPocket). */
    private float stateColorLevel;

    public TileRendererPocketWall(BlockEntityRendererProvider.Context context) {
        // The context is unused: all textures are bound through RenderType.
        this.connectorBaseColor = Colour.WHITE.copy();
        this.connectorColor = Colour.WHITE.copy();
        this.connectorBGColour = Colour.WHITE.copy();
        this.connectorBGColour.a = 0.80D;
    }

    @Override
    public void render(TileDimensionalPocketWallConnector blockEntity, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        CoordSet offsetCoords = blockEntity.getCoordSet().toChunkOffset();
        Direction wallVisibleSide = Pocket.getSideForConnector(offsetCoords).getOpposite();

        // % 15 maps 15 back to 0, so only the two coordinates spanning the wall plane stay non zero.
        int offX = offsetCoords.x % 15;
        int offY = offsetCoords.y % 15;
        int offZ = offsetCoords.z % 15;

        int ordinal = wallVisibleSide.ordinal();
        float offset = ordinal % 2 == 0 ? 0.0F : 1.0F;

        int tempX = offX;
        int tempY = offY;
        int tempZ = offZ;
        switch (wallVisibleSide) {
            case DOWN:
            case UP:
                tempX -= 1;
                tempZ -= 1;
                break;
            case NORTH:
            case SOUTH:
                tempX -= 1;
                tempY -= 1;
                break;
            case WEST:
            case EAST:
                tempY -= 1;
                tempZ -= 1;
                break;
            default:
                ModLog.deny("RenderPocketWall", "render", "pos={} reason=unhandled_side actual={}",
                        blockEntity.getBlockPos(), wallVisibleSide);
                return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean isInGui = TileRendererPocket.isRenderingGui(minecraft);
        boolean fancy = DPConfig.CFG.useFancyField();
        boolean colorBlind = DPConfig.colorBlindMode();

        pose.pushPose();

        if (!isInGui) {
            // 1.7.10: setInterpolatedPosition(x - tempX, y - tempY, z - tempZ), small = false.
            Vec3 origin = Vec3.atLowerCornerOf(blockEntity.getBlockPos()).add(-tempX, -tempY, -tempZ);
            portalRenderer.renderFace(pose, buffers, wallVisibleSide, offset, FRAME_SCALE, fancy, false,
                    DPConfig.CFG.numberOfParticlePlanes(), origin, ClientEventHandler.gameTicks,
                    seedFor(wallVisibleSide));
        }

        // "frame other offsets and scale because the texture is smaller" (upstream comment)
        int ox = offX == 0 ? 0 : offX - 1;
        int oy = offY == 0 ? 0 : offY - 1;
        int oz = offZ == 0 ? 0 : offZ - 1;

        if (!isInGui) {
            pose.pushPose();
            pose.translate(-ox, -oy, -oz);
            RenderUtils.drawFace(pose, buffers, wallVisibleSide, PocketTextures.pocketFrameInside, 0.001F,
                    FRAME_SCALE, Colour.WHITE, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }

        // Connectors: drawn in the GUI too, just pushed further behind the block surface there.
        updateConnectorColor(isInGui);
        float guiOffset = isInGui ? 0.1F : 0.0F;
        RenderUtils.drawFace(pose, buffers, wallVisibleSide, PocketTextures.connector, 0.005F - guiOffset,
                1.0F, connectorColor, LightTexture.FULL_BRIGHT);
        RenderUtils.drawFace(pose, buffers, wallVisibleSide, PocketTextures.connectorBG, 0.004F - guiOffset,
                1.0F, connectorBGColour, LightTexture.FULL_BRIGHT);

        if (TileRendererPocket.doIndicateSides) {
            pose.pushPose();
            pose.translate(-offX, -offY, -offZ);
            renderIndicators(pose, buffers, blockEntity, wallVisibleSide, colorBlind);
            pose.popPose();
        }

        pose.popPose();
    }

    /**
     * The upstream indicator takes the colour and the state of {@code wallVisibleSide.getOpposite()}.
     */
    private void renderIndicators(PoseStack pose, MultiBufferSource buffers,
                                  TileDimensionalPocketWallConnector blockEntity, Direction wallVisibleSide,
                                  boolean colorBlind) {
        IColourBlindTexture indicator = PocketTextures.sideIndicatorFor(wallVisibleSide);
        if (indicator == null) {
            ModLog.trace("RenderPocketWall", "indicator_missing", "direction={} reason=no_texture", wallVisibleSide);
        } else {
            RenderUtils.drawFace(pose, buffers, wallVisibleSide, indicator.getTexture(false), 0.002F, WALL_SCALE,
                    RenderUtils.faceColour(wallVisibleSide.getOpposite()), LightTexture.FULL_BRIGHT);
            if (colorBlind) {
                RenderUtils.drawFace(pose, buffers, wallVisibleSide, indicator.getTexture(true), 0.003F, WALL_SCALE,
                        Colour.WHITE, LightTexture.FULL_BRIGHT);
            }
        }

        updateStateColorLevel();

        Pocket pocket = blockEntity.getPocket();
        if (pocket == null) {
            ModLog.trace("RenderPocketWall", "overlay_skip", "pos={} reason=pocket_null side=CLIENT",
                    blockEntity.getBlockPos());
            return;
        }

        PocketSideState state = pocket.getFlowState(wallVisibleSide.getOpposite());
        IColourBlindTexture texture = PocketTextures.overlayFor(state);
        if (texture == null) {
            // NONE has no overlay texture upstream - that face simply draws nothing.
            ModLog.trace("RenderPocketWall", "overlay_skip", "state={} reason=no_texture", state);
            return;
        }
        RenderUtils.drawFace(pose, buffers, wallVisibleSide, texture.getTexture(colorBlind), 0.001F, WALL_SCALE,
                RenderUtils.scaled(state.getColour(), stateColorLevel), LightTexture.FULL_BRIGHT);
    }

    /** 1.7.10 {@code updateConnectorColor}: 5 s breathing, and a more transparent background in GUIs. */
    private void updateConnectorColor(boolean isInGUI) {
        long colorCycleTime = 5000000000L;
        double minColorLevel = 0.5D;
        double level = minColorLevel + (1 - minColorLevel)
                * Math.sin(System.nanoTime() % colorCycleTime * Math.PI / colorCycleTime);

        connectorColor.r = connectorBaseColor.r * level;
        connectorColor.g = connectorBaseColor.g * level;
        connectorColor.b = connectorBaseColor.b * level;
        connectorColor.a = connectorBaseColor.a * level;

        connectorBGColour.a = isInGUI ? 0.2D : 0.8D;
    }

    /** 1.7.10: 1.337 s sine breathing between 0.5 and 1.0 (inherited from {@code TileRendererPocket}). */
    private void updateStateColorLevel() {
        long colorCycleTime = 1337000000L;
        double minColorLevel = 0.5D;
        stateColorLevel = (float) (minColorLevel + (1 - minColorLevel)
                * Math.sin(System.nanoTime() % colorCycleTime * Math.PI / colorCycleTime));
    }
    /**
     * The upstream renderer indexed {@code seeds[wallVisibleSide.ordinal()]}. An explicit table is used
     * instead of relying on the enum order (报告 03 §4.3).
     */
    private static long seedFor(Direction side) {
        switch (side) {
            case DOWN:
                return TileRendererPocket.seeds[0];
            case UP:
                return TileRendererPocket.seeds[1];
            case NORTH:
                return TileRendererPocket.seeds[2];
            case SOUTH:
                return TileRendererPocket.seeds[3];
            case WEST:
                return TileRendererPocket.seeds[4];
            case EAST:
            default:
                return TileRendererPocket.seeds[5];
        }
    }
}
