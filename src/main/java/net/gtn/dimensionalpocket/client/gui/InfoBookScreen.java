package net.gtn.dimensionalpocket.client.gui;

import java.util.List;

import net.gtn.dimensionalpocket.client.ClientProxy;
import net.gtn.dimensionalpocket.client.utils.GuiSheet;
import net.gtn.dimensionalpocket.client.utils.RecipeHelper;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The guide book: ten pages (0..9) on the {@code guideDP.png} atlas, three of which are recipe pages,
 * with a left / right arrow at the bottom.
 *
 * <p>1.7.10 origin: {@code client/gui/GuiInfoBook.java} ({@code GuiContainer} + an empty
 * {@code Container} + the author's own {@code GuiWidget} framework). The 1.20.1 port is a plain
 * {@link Screen}: the book has no server state, so no {@code Menu}, no {@code AbstractContainerScreen}
 * and no network packet is involved (contract §4.1). It is opened with
 * {@code Minecraft.getInstance().setScreen(new InfoBookScreen())}.
 *
 * <p>Layout, page numbers and language keys are unchanged: {@code xSize = 154}, {@code ySize = 180},
 * arrows 18x10 at {@code (leftPos + 26, topPos + 160)} / {@code (leftPos + 114, topPos + 160)},
 * 3x3 grid from {@code (leftPos + 51, topPos + 36)} with a step of 18, output slot at
 * {@code (leftPos + 69, topPos + 124)}, recipe backdrop at {@code (leftPos + 52, topPos + 37)} size
 * 54x106, text scaled to 0.75 at {@code (39, 20)} with a 140 pixel wrap width and the {@code <br>}
 * line separator.
 *
 * <p>Page numbers are read from the language file, exactly like upstream
 * ({@code info.page.maxPage} = 9, {@code info.page.recipe.block.id} = 7,
 * {@code info.page.recipe.endCrystal.id} = 5, {@code info.page.recipe.netherCrystal.id} = 6), with the
 * same fallbacks when they are not valid numbers.
 */
public class InfoBookScreen extends Screen {

    private static final int XSIZE = 154;
    private static final int YSIZE = 180;

    private static final int ARROW_WIDTH = 18;
    private static final int ARROW_HEIGHT = 10;
    private static final int ARROW_U_HOVER = 23;
    private static final int ARROW_U_PRESSED = 46;
    private static final int ARROW_V_RIGHT = 180;
    private static final int ARROW_V_LEFT = 193;
    /** 1.7.10 GuiTexturedButton.buttonDelay: the "pressed" frame lasts 125 ms. */
    private static final long ARROW_PRESSED_MILLIS = 125L;

    private static final int RECIPE_BG_U = 155;
    private static final int RECIPE_BG_V = 0;
    private static final int RECIPE_BG_WIDTH = 54;
    private static final int RECIPE_BG_HEIGHT = 106;

    private static final int SLOT_SIZE = 19;
    private static final int SLOT_GRID_X = 51;
    private static final int SLOT_GRID_Y = 36;
    private static final int SLOT_STEP = 18;
    private static final int SLOT_OUTPUT_X = 69;
    private static final int SLOT_OUTPUT_Y = 124;

    private static final float TEXT_SCALE = 0.75F;
    private static final int TEXT_WRAP_WIDTH = 140;
    private static final int TEXT_COLOUR = 0xFF333333;
    private static final String LINE_BREAK = "<br>";

    private final ItemStack[] slots = new ItemStack[10];
    private final int[] slotX = new int[10];
    private final int[] slotY = new int[10];

    private int leftPos;
    private int topPos;
    private int currentPage;

    private int maxPage = 9;
    private int recipePocket = 7;
    private int recipeEndCrystal = 5;
    private int recipeNetherCrystal = 6;

    private int leftArrowX;
    private int leftArrowY;
    private int rightArrowX;
    private int rightArrowY;
    private long leftPressedUntil;
    private long rightPressedUntil;

    public InfoBookScreen() {
        super(Component.translatable("item." + Reference.MOD_ID + ".info_book"));

        this.currentPage = ClientProxy.currentPage;
        this.maxPage = parseInt("info.page.maxPage", this.maxPage);
        this.recipePocket = parseInt("info.page.recipe.block.id", this.recipePocket);
        this.recipeEndCrystal = parseInt("info.page.recipe.endCrystal.id", this.recipeEndCrystal);
        this.recipeNetherCrystal = parseInt("info.page.recipe.netherCrystal.id", this.recipeNetherCrystal);
    }

    /**
     * 1.7.10 wrapped the four {@code Integer.parseInt} calls in one try/catch and fell back to
     * {@code MAX_PAGE = 9, END = 5, NETHER = 6, POCKET = 7} with a severe log line.
     */
    private static int parseInt(String key, int fallback) {
        String raw = Component.translatable(key).getString();
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            ModLog.warn("InfoBook", "lang_number",
                    "key={} actual={} expected=integer fallback={} reason=not_a_number", key, raw, fallback);
            return fallback;
        }
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width - XSIZE) / 2;
        topPos = (height - YSIZE) / 2;

        // 1.7.10 initArrows(): x = guiLeft + (xSize / 2) - (18 / 2) + 2, y = guiTop + ySize - 20
        int arrowX = leftPos + (XSIZE / 2) - (ARROW_WIDTH / 2) + 2;
        int arrowY = topPos + YSIZE - (ARROW_HEIGHT * 2);
        leftArrowX = arrowX - 44;
        leftArrowY = arrowY;
        rightArrowX = arrowX + 44;
        rightArrowY = arrowY;

        int index = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                slotX[index] = leftPos + SLOT_GRID_X + (column * SLOT_STEP);
                slotY[index] = topPos + SLOT_GRID_Y + (row * SLOT_STEP);
                slots[index] = null;
                index++;
            }
        }
        slotX[9] = leftPos + SLOT_OUTPUT_X;
        slotY[9] = topPos + SLOT_OUTPUT_Y;
        slots[9] = null;

        currentPage = Mth.clamp(currentPage, 0, maxPage);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.blit(GuiSheet.GUI_INFO_BOOK, leftPos, topPos, 0, 0, XSIZE, YSIZE);

        int recipeType = getRecipeType();
        if (recipeType >= 0) {
            graphics.blit(GuiSheet.GUI_INFO_BOOK, leftPos + 52, topPos + 37, RECIPE_BG_U, RECIPE_BG_V,
                    RECIPE_BG_WIDTH, RECIPE_BG_HEIGHT);
            fillRecipe(recipeType);
            renderRecipeItems(graphics, mouseX, mouseY);
        }

        graphics.pose().pushPose();
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        drawBookText(graphics);
        graphics.pose().popPose();

        long now = Util.getMillis();
        drawArrow(graphics, leftArrowX, leftArrowY, ARROW_V_LEFT, mouseX, mouseY,
                now < leftPressedUntil, currentPage != 0);
        drawArrow(graphics, rightArrowX, rightArrowY, ARROW_V_RIGHT, mouseX, mouseY,
                now < rightPressedUntil, currentPage != maxPage);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 1.7.10 renderRecipe(type) + GuiItemStack rendering (which is done by GuiGraphics in 1.20.1). */
    private void renderRecipeItems(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = slots[i];
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int x = slotX[i] + 2;
            int y = slotY[i] + 2;
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(font, stack, x, y);

            // Hover tooltip - this is where the "press Shift for more info" hint of the crystals shows
            // up (1.7.10 RenderUtils.renderItemStackInGUI + renderToolTip).
            if (isOver(mouseX, mouseY, slotX[i], slotY[i], SLOT_SIZE, SLOT_SIZE)) {
                graphics.renderTooltip(font, stack, mouseX, mouseY);
            }
        }
    }

    /** 1.7.10 drawWrappedString: {@code <br>} separated blocks, 0.75 scale, 140 pixel wrap width. */
    private void drawBookText(GuiGraphics graphics) {
        boolean recipePage = shouldDrawRecipe();
        String key = recipePage ? getRecipeString() : "info.page." + currentPage;
        String text = Component.translatable(key).getString();

        int prevLines = 0;
        for (String part : text.split(LINE_BREAK)) {
            int x = (XSIZE - 135) / 2 + 30;
            int y = YSIZE / 2 - 70;
            int lineY = y + (font.lineHeight * prevLines);

            if (recipePage) {
                // Recipe pages draw the title only, without wrapping (upstream behaviour).
                graphics.drawString(font, part, x, lineY, TEXT_COLOUR, false);
            } else {
                // Font#split returns List<FormattedCharSequence> in 1.20.1 (not FormattedText).
                List<FormattedCharSequence> lines = font.split(Component.literal(part), TEXT_WRAP_WIDTH);
                for (FormattedCharSequence line : lines) {
                    graphics.drawString(font, line, x, lineY, TEXT_COLOUR, false);
                    lineY += font.lineHeight;
                }
            }
            // Upstream line estimate for the following block: ceil(length * 7 / 165).
            prevLines += (int) Math.ceil((part.length() * 7.0F) / 165.0F);
        }
    }

    /** 1.7.10 GuiArrow + GuiTexturedButton: three frames along u, one row per arrow. */
    private void drawArrow(GuiGraphics graphics, int x, int y, int v, int mouseX, int mouseY,
                           boolean pressed, boolean visible) {
        if (!visible) {
            return;
        }
        int u = 0;
        if (isOver(mouseX, mouseY, x, y, ARROW_WIDTH, ARROW_HEIGHT)) {
            u = ARROW_U_HOVER;
        }
        if (pressed) {
            u = ARROW_U_PRESSED;
        }
        graphics.blit(GuiSheet.GUI_INFO_BOOK, x, y, u, v, ARROW_WIDTH, ARROW_HEIGHT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (currentPage != 0 && isOver(mouseX, mouseY, leftArrowX, leftArrowY, ARROW_WIDTH, ARROW_HEIGHT)) {
                turnPage(-1);
                leftPressedUntil = Util.getMillis() + ARROW_PRESSED_MILLIS;
                return true;
            }
            if (currentPage != maxPage && isOver(mouseX, mouseY, rightArrowX, rightArrowY, ARROW_WIDTH, ARROW_HEIGHT)) {
                turnPage(1);
                rightPressedUntil = Util.getMillis() + ARROW_PRESSED_MILLIS;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 1.7.10 onActionPerformed: clamp the page, then clear the 3x3 grid when leaving a recipe page. */
    private void turnPage(int delta) {
        currentPage = Mth.clamp(currentPage + delta, 0, maxPage);
        if (getRecipeType() < 0) {
            clearSlots();
        }
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        ModLog.trace("InfoBook", "turn_page", "page={} max={} recipe={}", currentPage, maxPage, getRecipeType());
    }

    private void clearSlots() {
        for (int i = 0; i < slots.length; i++) {
            slots[i] = null;
        }
    }

    /** 1.7.10 renderRecipe(type): copies the ten displayed stacks into the slots. */
    private void fillRecipe(int type) {
        ItemStack[] recipe = RecipeHelper.getRecipe(type);
        if (recipe == null) {
            ModLog.deny("InfoBook", "recipe", "reason=unknown_recipe_type actual={} expected=0..2", type);
            return;
        }
        for (int i = 0; i < slots.length && i < recipe.length; i++) {
            slots[i] = recipe[i];
        }
    }

    /** 1.7.10 getRecipeType(): 0 = pocket block (page 7), 1 = end crystal (5), 2 = nether crystal (6). */
    private int getRecipeType() {
        if (currentPage == recipePocket) {
            return 0;
        }
        if (currentPage == recipeEndCrystal) {
            return 1;
        }
        if (currentPage == recipeNetherCrystal) {
            return 2;
        }
        return -1;
    }

    /** 1.7.10 shouldDrawRecipe(): true while the current page shows a recipe. */
    private boolean shouldDrawRecipe() {
        return getRecipeType() >= 0;
    }

    /** 1.7.10 getRecipeString(): the title key of the current recipe page. */
    private String getRecipeString() {
        switch (getRecipeType()) {
            case 0:
                return "info.page.recipe.block";
            case 1:
                return "info.page.recipe.endCrystal";
            case 2:
                return "info.page.recipe.netherCrystal";
            default:
                return "info.page." + currentPage;
        }
    }

    /** 1.7.10 onGuiClosed: the page is remembered for the whole client session. */
    @Override
    public void removed() {
        super.removed();
        ClientProxy.currentPage = currentPage;
    }

    /** 1.7.10 GuiWidget#canClick used strict inequalities; kept so the hover frame matches. */
    private static boolean isOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX > x && mouseX < x + width && mouseY > y && mouseY < y + height;
    }
}
