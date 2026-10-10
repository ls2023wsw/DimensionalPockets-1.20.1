package net.gtn.dimensionalpocket.client.utils;

import net.gtn.dimensionalpocket.common.lib.Reference;
import net.minecraft.resources.ResourceLocation;

/**
 * GUI texture sheet.
 *
 * <p>1.7.10 origin: {@code client/utils/GuiSheet.java} - the only sheet is the info book atlas
 * {@code textures/gui/guideDP.png} (256x256), which is exactly the file the resources already ship.
 */
public final class GuiSheet {

    public static final ResourceLocation GUI_INFO_BOOK = Reference.TEXTURE_GUIDE;

    private GuiSheet() {
    }
}
