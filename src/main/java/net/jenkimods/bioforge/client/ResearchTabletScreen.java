package net.jenkimods.bioforge.client;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.api.guide.ResearchJournalPageView;
import net.jenkimods.bioforge.api.guide.ResearchJournalRecipeView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ResearchTabletScreen extends Screen {
    private static final ResourceLocation TABLET_TEXTURE = ResourceLocation.tryBuild(
            BioForge.MODID, "textures/gui/research_journal/tablet.png");
    private static final ResourceLocation SLOT_TEXTURE = ResourceLocation.tryBuild(
            BioForge.MODID, "textures/gui/vaccine_maker/slot.png");
    private static final int TABLET_WIDTH = 360;
    private static final int TABLET_HEIGHT = 230;
    private static final int SIDEBAR_WIDTH = 116;
    private static final int HEADER_HEIGHT = 30;
    private static final int ENTRY_HEIGHT = 20;
    private static final int RECIPE_AREA_HEIGHT = 91;
    private static final int SCROLL_BAR_WIDTH = 5;
    private static final int PANEL = 0xFF071E27;
    private static final int PANEL_INNER = 0xFF0A2A34;
    private static final int PANEL_HOVER = 0xFF0D3A45;
    private static final int PANEL_SELECTED = 0xFF10505E;
    private static final int BORDER = 0xFF397783;
    private static final int CYAN = 0xFF75F4FF;
    private static final int TEXT_CYAN = 0xFFA8F9FF;
    private static final int MUTED_CYAN = 0xFF62B5C0;
    private static final int DISABLED_CYAN = 0xFF39727A;
    private final List<ResearchJournalPageView> pages;
    private int selectedPage;
    private int navigationScroll;
    private int contentScroll;
    private int navigationMaxScroll;
    private int contentMaxScroll;
    private int recipeScroll;
    private int recipeMaxScroll;
    private ItemStack recipeTooltipStack = ItemStack.EMPTY;
    private int navigationVisibleRows = 1;
    private int contentVisibleLines = 1;
    private ScrollTarget draggedScroll = ScrollTarget.NONE;

    public ResearchTabletScreen(List<ResearchJournalPageView> pages) {
        super(Component.translatable("gui.bioforge.research_journal.title"));
        this.pages = List.copyOf(pages);
        for (int index = 0; index < pages.size(); index++) {
            if (pages.get(index).unlocked()) {
                selectedPage = index;
                break;
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (width - TABLET_WIDTH) / 2;
        int top = (height - TABLET_HEIGHT) / 2;

        renderTablet(graphics, left, top);
        renderNavigation(graphics, left, top, mouseX, mouseY);
        renderPage(graphics, left, top);
        renderRecipes(graphics, left, top, mouseX, mouseY);
        renderCloseButton(graphics, left, top, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTablet(GuiGraphics graphics, int left, int top) {
        boolean hasTexture = minecraft != null && minecraft.getResourceManager()
                .getResource(TABLET_TEXTURE).isPresent();
        if (hasTexture) {
            graphics.blit(TABLET_TEXTURE, left, top, 0, 0,
                    TABLET_WIDTH, TABLET_HEIGHT, TABLET_WIDTH, TABLET_HEIGHT);
        } else {
            graphics.fill(left, top, left + TABLET_WIDTH, top + TABLET_HEIGHT, 0xFF10171D);
            graphics.fill(left + 4, top + 4, left + TABLET_WIDTH - 4,
                    top + TABLET_HEIGHT - 4, 0xFF26343C);
            graphics.fill(left + 10, top + 10, left + TABLET_WIDTH - 10,
                    top + TABLET_HEIGHT - 10, 0xFFF0F3EC);
            graphics.fill(left + 10, top + 10, left + TABLET_WIDTH - 10,
                    top + HEADER_HEIGHT, 0xFF173F4A);
            graphics.fill(left + SIDEBAR_WIDTH, top + HEADER_HEIGHT,
                    left + SIDEBAR_WIDTH + 1, top + TABLET_HEIGHT - 10, 0xFF8AA0A3);
        }
        graphics.fill(left + 10, top + 10, left + TABLET_WIDTH - 10,
                top + HEADER_HEIGHT, PANEL);
        drawPanel(graphics, left + 10, top + HEADER_HEIGHT + 4,
                left + SIDEBAR_WIDTH, top + TABLET_HEIGHT - 10);
        drawPanel(graphics, left + SIDEBAR_WIDTH + 5, top + HEADER_HEIGHT + 4,
                left + TABLET_WIDTH - 10, top + TABLET_HEIGHT - 10);
        graphics.drawString(font, title, left + 18, top + 17, CYAN, false);
        int unlocked = (int) pages.stream().filter(ResearchJournalPageView::unlocked).count();
        Component progress = Component.translatable(
                "gui.bioforge.research_journal.progress", unlocked, pages.size());
        graphics.drawString(font, progress,
                left + TABLET_WIDTH - 20 - font.width(progress), top + 17,
                MUTED_CYAN, false);
    }

    private void renderNavigation(GuiGraphics graphics, int left, int top,
                                  int mouseX, int mouseY) {
        int listTop = top + HEADER_HEIGHT + 8;
        int listBottom = top + TABLET_HEIGHT - 14;
        int visible = Math.max(1, (listBottom - listTop) / ENTRY_HEIGHT);
        navigationVisibleRows = visible;
        navigationMaxScroll = Math.max(0, pages.size() - visible);
        navigationScroll = Mth.clamp(navigationScroll, 0, navigationMaxScroll);
        for (int row = 0; row < visible; row++) {
            int index = navigationScroll + row;
            if (index >= pages.size()) break;
            ResearchJournalPageView page = pages.get(index);
            int y = listTop + row * ENTRY_HEIGHT;
            boolean hovered = mouseX >= left + 14 && mouseX < left + SIDEBAR_WIDTH - 5
                    && mouseY >= y && mouseY < y + ENTRY_HEIGHT - 2;
            int background = index == selectedPage ? PANEL_SELECTED
                    : hovered ? PANEL_HOVER : PANEL_INNER;
            graphics.fill(left + 14, y, left + SIDEBAR_WIDTH - 5,
                    y + ENTRY_HEIGHT - 2, BORDER);
            graphics.fill(left + 15, y + 1, left + SIDEBAR_WIDTH - 6,
                    y + ENTRY_HEIGHT - 3, background);
            Component label = page.unlocked() ? page.title()
                    : Component.translatable("gui.bioforge.research_journal.locked_short");
            graphics.drawString(font, trim(label, SIDEBAR_WIDTH - 32),
                    left + 19, y + 5, page.unlocked() ? CYAN : DISABLED_CYAN, false);
        }
        renderScrollBar(graphics, navigationScrollX(left), listTop,
                listBottom - listTop, navigationScroll, navigationMaxScroll,
                navigationVisibleRows);
    }

    private void renderPage(GuiGraphics graphics, int left, int top) {
        if (pages.isEmpty()) {
            graphics.drawString(font,
                    Component.translatable("gui.bioforge.research_journal.empty"),
                    left + SIDEBAR_WIDTH + 14, top + HEADER_HEIGHT + 14,
                    TEXT_CYAN, false);
            return;
        }
        selectedPage = Mth.clamp(selectedPage, 0, pages.size() - 1);
        ResearchJournalPageView page = pages.get(selectedPage);
        int contentLeft = left + SIDEBAR_WIDTH + 14;
        int contentTop = top + HEADER_HEIGHT + 9;
        int contentWidth = TABLET_WIDTH - SIDEBAR_WIDTH - 34;
        graphics.drawString(font, trim(page.title(), contentWidth), contentLeft,
                contentTop, page.unlocked() ? CYAN : DISABLED_CYAN, false);
        List<FormattedCharSequence> lines = formattedLines(page.body(), contentWidth);
        int recipeArea = !page.recipes().isEmpty()
                ? RECIPE_AREA_HEIGHT : 0;
        int visibleLines = Math.max(1,
                (TABLET_HEIGHT - HEADER_HEIGHT - 42 - recipeArea) / 11);
        contentVisibleLines = visibleLines;
        contentMaxScroll = Math.max(0, lines.size() - visibleLines);
        contentScroll = Mth.clamp(contentScroll, 0, contentMaxScroll);
        int bodyTop = contentTop + 18;
        graphics.enableScissor(contentLeft, bodyTop,
                contentScrollX(left) - 2, bodyTop + visibleLines * 11);
        for (int row = 0; row < visibleLines; row++) {
            int index = contentScroll + row;
            if (index >= lines.size()) break;
            graphics.drawString(font, lines.get(index), contentLeft,
                    bodyTop + row * 11, TEXT_CYAN, false);
        }
        graphics.disableScissor();
        if (lines.size() > visibleLines) {
            Component scroll = Component.translatable(
                    "gui.bioforge.research_journal.scroll_hint");
            graphics.drawString(font, scroll, contentLeft,
                    top + TABLET_HEIGHT - 20 - recipeArea, MUTED_CYAN, false);
        }
        renderScrollBar(graphics, contentScrollX(left), contentTop + 18,
                visibleLines * 11, contentScroll, contentMaxScroll, contentVisibleLines);
    }

    private void renderRecipes(GuiGraphics graphics, int left, int top,
                               int mouseX, int mouseY) {
        recipeMaxScroll = 0;
        recipeTooltipStack = ItemStack.EMPTY;
        if (pages.isEmpty() || minecraft == null || minecraft.level == null) return;
        ResearchJournalPageView page = pages.get(Mth.clamp(selectedPage, 0, pages.size() - 1));
        if (page.recipes().isEmpty()) return;

        List<ResearchJournalRecipeView> recipes = page.recipes();
        if (recipes.isEmpty()) return;

        int x = left + SIDEBAR_WIDTH + 14;
        int y = top + TABLET_HEIGHT - RECIPE_AREA_HEIGHT;
        graphics.fill(x - 5, y - 5, left + TABLET_WIDTH - 14,
                top + TABLET_HEIGHT - 14, BORDER);
        graphics.fill(x - 4, y - 4, left + TABLET_WIDTH - 15,
                top + TABLET_HEIGHT - 15, PANEL);
        graphics.drawString(font, Component.translatable(page.unlocked()
                ? "gui.bioforge.research_journal.recipes"
                : "gui.bioforge.research_journal.unlock_recipe"), x, y, CYAN, false);
        recipeMaxScroll = Math.max(0, recipes.size() - 1);
        recipeScroll = Mth.clamp(recipeScroll, 0, recipeMaxScroll);
        ResearchJournalRecipeView recipe = recipes.get(recipeScroll);
        graphics.drawString(font, trim(recipe.station(), 135), x, y + 10,
                MUTED_CYAN, false);
        int gridX = x + 31;
        int gridY = y + 22;
        renderRecipeGrid(graphics, recipe, gridX, gridY, mouseX, mouseY);
        int arrowX = gridX + 61;
        int arrowY = gridY + 23;
        graphics.fill(arrowX, arrowY + 3, arrowX + 14, arrowY + 5, CYAN);
        graphics.fill(arrowX + 10, arrowY, arrowX + 12, arrowY + 8, CYAN);
        graphics.fill(arrowX + 12, arrowY + 2, arrowX + 14, arrowY + 6, CYAN);

        int resultX = gridX + 82;
        int visibleResults = Math.min(3, recipe.results().size());
        int resultY = gridY + Math.max(0, (54 - visibleResults * 18) / 2);
        for (int resultIndex = 0; resultIndex < visibleResults; resultIndex++) {
            ItemStack result = recipe.results().get(resultIndex);
            int slotY = resultY + resultIndex * 18;
            renderRecipeSlot(graphics, result, resultX, slotY, mouseX, mouseY);
            if (!result.isEmpty()) {
                graphics.drawString(font, trim(result.getHoverName(), 72), resultX + 22,
                        slotY + 5, TEXT_CYAN, false);
            }
        }
        if (recipeMaxScroll > 0) {
            int previousX = recipePreviousX(left);
            int nextX = recipeNextX(left);
            int navigationY = recipeNavigationY(top);
            renderRecipeNavigationButton(graphics, previousX, navigationY, "<",
                    recipeScroll > 0, mouseX, mouseY);
            renderRecipeNavigationButton(graphics, nextX, navigationY, ">",
                    recipeScroll < recipeMaxScroll, mouseX, mouseY);
            Component counter = Component.literal((recipeScroll + 1) + "/"
                    + (recipeMaxScroll + 1));
            graphics.drawString(font, counter,
                    previousX - 5 - font.width(counter), navigationY + 2,
                    MUTED_CYAN, false);
        }
        if (!recipeTooltipStack.isEmpty()) {
            graphics.renderTooltip(font, recipeTooltipStack, mouseX, mouseY);
        }
    }

    private void renderRecipeGrid(GuiGraphics graphics, ResearchJournalRecipeView recipe, int gridX,
                                  int gridY, int mouseX, int mouseY) {
        List<List<ItemStack>> ingredients = recipe.ingredients();
        int width = Mth.clamp(recipe.width(), 1, 3);
        int height = Mth.clamp(recipe.height(), 1, 3);
        int offsetX = (3 - width) / 2;
        int offsetY = (3 - height) / 2;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                ItemStack display = ItemStack.EMPTY;
                int ingredientIndex = shapedIngredientIndex(
                        column, row, width, height, offsetX, offsetY);
                if (ingredientIndex >= 0 && ingredientIndex < ingredients.size()) {
                    display = cyclingStack(ingredients.get(ingredientIndex),
                            ingredientIndex);
                }
                renderRecipeSlot(graphics, display, gridX + column * 18,
                        gridY + row * 18, mouseX, mouseY);
            }
        }
    }

    private int shapedIngredientIndex(int column, int row, int width, int height,
                                      int offsetX, int offsetY) {
        if (column < offsetX || column >= offsetX + width
                || row < offsetY || row >= offsetY + height) return -1;
        return (row - offsetY) * width + column - offsetX;
    }

    private ItemStack cyclingStack(List<ItemStack> choices, int offset) {
        if (choices.isEmpty()) return ItemStack.EMPTY;
        long cycle = minecraft == null || minecraft.level == null
                ? 0L : minecraft.level.getGameTime() / 30L;
        return choices.get(Math.floorMod((int) cycle + offset, choices.size())).copy();
    }

    private void renderRecipeSlot(GuiGraphics graphics, ItemStack stack, int x, int y,
                                  int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
        if (minecraft != null && minecraft.getResourceManager().getResource(SLOT_TEXTURE).isPresent()) {
            graphics.blit(SLOT_TEXTURE, x - 1, y - 1, 0, 0, 18, 19, 18, 19);
        } else {
            graphics.fill(x - 1, y - 1, x + 17, y + 18, BORDER);
            graphics.fill(x, y, x + 16, y + 16, PANEL_INNER);
        }
        if (hovered) graphics.fill(x, y, x + 16, y + 16, 0x55FFFFFF);
        if (stack.isEmpty()) return;
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
        if (hovered) recipeTooltipStack = stack;
    }

    private void renderRecipeNavigationButton(GuiGraphics graphics, int x, int y,
                                              String label, boolean enabled,
                                              int mouseX, int mouseY) {
        boolean hovered = enabled && mouseX >= x && mouseX < x + 14
                && mouseY >= y && mouseY < y + 13;
        graphics.fill(x, y, x + 14, y + 13, BORDER);
        graphics.fill(x + 1, y + 1, x + 13, y + 12,
                enabled ? hovered ? PANEL_SELECTED : PANEL_INNER : PANEL);
        graphics.drawString(font, label, x + (14 - font.width(label)) / 2,
                y + 2, enabled ? CYAN : DISABLED_CYAN, false);
    }

    private void renderCloseButton(GuiGraphics graphics, int left, int top,
                                   int mouseX, int mouseY) {
        int x = left + TABLET_WIDTH - 15;
        int y = top + 5;
        boolean hovered = mouseX >= x - 6 && mouseX <= x + 6
                && mouseY >= y && mouseY <= y + 12;
        graphics.drawString(font, "×", x - 3, y + 1,
                hovered ? 0xFFFF8A7A : 0xFFD7E5E2, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int left = (width - TABLET_WIDTH) / 2;
        int top = (height - TABLET_HEIGHT) / 2;
        int closeX = left + TABLET_WIDTH - 15;
        if (mouseX >= closeX - 6 && mouseX <= closeX + 6
                && mouseY >= top + 5 && mouseY <= top + 17) {
            onClose();
            return true;
        }
        int recipeY = recipeNavigationY(top);
        int previousX = recipePreviousX(left);
        int nextX = recipeNextX(left);
        if (mouseY >= recipeY && mouseY < recipeY + 13) {
            if (mouseX >= previousX && mouseX < previousX + 14 && recipeScroll > 0) {
                recipeScroll--;
                return true;
            }
            if (mouseX >= nextX && mouseX < nextX + 14
                    && recipeScroll < recipeMaxScroll) {
                recipeScroll++;
                return true;
            }
        }
        int listTop = top + HEADER_HEIGHT + 8;
        int listBottom = top + TABLET_HEIGHT - 14;
        if (isInsideScrollBar(mouseX, mouseY, navigationScrollX(left), listTop,
                listBottom - listTop)) {
            draggedScroll = ScrollTarget.NAVIGATION;
            navigationScroll = scrollFromPointer(mouseY, listTop, listBottom - listTop,
                    navigationMaxScroll, navigationVisibleRows);
            return true;
        }
        int contentTop = top + HEADER_HEIGHT + 27;
        int contentHeight = contentVisibleLines * 11;
        if (isInsideScrollBar(mouseX, mouseY, contentScrollX(left), contentTop,
                contentHeight)) {
            draggedScroll = ScrollTarget.CONTENT;
            contentScroll = scrollFromPointer(mouseY, contentTop, contentHeight,
                    contentMaxScroll, contentVisibleLines);
            return true;
        }
        if (mouseX >= left + 14 && mouseX < left + SIDEBAR_WIDTH - 5
                && mouseY >= listTop && mouseY < listBottom) {
            int row = ((int) mouseY - listTop) / ENTRY_HEIGHT;
            int index = navigationScroll + row;
            if (index >= 0 && index < pages.size()) {
                selectedPage = index;
                contentScroll = 0;
                recipeScroll = 0;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double deltaX, double deltaY) {
        double delta = deltaY;
        int left = (width - TABLET_WIDTH) / 2;
        int top = (height - TABLET_HEIGHT) / 2;
        int direction = delta > 0.0D ? -1 : delta < 0.0D ? 1 : 0;
        if (mouseX < left + SIDEBAR_WIDTH) navigationScroll += direction;
        else if (mouseY >= top + TABLET_HEIGHT - RECIPE_AREA_HEIGHT - 5) {
            recipeScroll += direction;
        }
        else contentScroll += direction;
        return direction != 0 || super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (button == 0 && draggedScroll != ScrollTarget.NONE) {
            int left = (width - TABLET_WIDTH) / 2;
            int top = (height - TABLET_HEIGHT) / 2;
            if (draggedScroll == ScrollTarget.NAVIGATION) {
                int trackTop = top + HEADER_HEIGHT + 8;
                int trackHeight = TABLET_HEIGHT - HEADER_HEIGHT - 22;
                navigationScroll = scrollFromPointer(mouseY, trackTop, trackHeight,
                        navigationMaxScroll, navigationVisibleRows);
            } else {
                int trackTop = top + HEADER_HEIGHT + 27;
                int trackHeight = contentVisibleLines * 11;
                contentScroll = scrollFromPointer(mouseY, trackTop, trackHeight,
                        contentMaxScroll, contentVisibleLines);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggedScroll != ScrollTarget.NONE) {
            draggedScroll = ScrollTarget.NONE;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void renderScrollBar(GuiGraphics graphics, int x, int y, int height,
                                 int scroll, int maximum, int visible) {
        graphics.fill(x, y, x + SCROLL_BAR_WIDTH, y + height, PANEL);
        int thumbHeight = scrollThumbHeight(height, maximum, visible);
        int travel = Math.max(0, height - thumbHeight);
        int thumbY = maximum == 0 ? y : y + Math.round(travel * (scroll / (float) maximum));
        graphics.fill(x - 1, thumbY, x + SCROLL_BAR_WIDTH + 1,
                thumbY + thumbHeight, maximum == 0 ? DISABLED_CYAN : CYAN);
    }

    private void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, BORDER);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, PANEL_INNER);
    }

    private boolean isInsideScrollBar(double mouseX, double mouseY, int x, int y,
                                      int height) {
        return mouseX >= x - 2 && mouseX <= x + SCROLL_BAR_WIDTH + 2
                && mouseY >= y && mouseY <= y + height;
    }

    private int scrollFromPointer(double mouseY, int trackTop, int trackHeight,
                                  int maximum, int visible) {
        if (maximum <= 0) return 0;
        int thumbHeight = scrollThumbHeight(trackHeight, maximum, visible);
        int travel = Math.max(1, trackHeight - thumbHeight);
        double position = Mth.clamp(mouseY - trackTop - thumbHeight / 2.0D,
                0.0D, travel);
        return Mth.clamp((int) Math.round(position / travel * maximum), 0, maximum);
    }

    private int scrollThumbHeight(int trackHeight, int maximum, int visible) {
        int total = maximum + visible;
        if (maximum <= 0 || total <= 0) return trackHeight;
        return Mth.clamp(Math.round(trackHeight * (visible / (float) total)),
                12, trackHeight);
    }

    private int navigationScrollX(int left) {
        return left + SIDEBAR_WIDTH - 10;
    }

    private int contentScrollX(int left) {
        return left + TABLET_WIDTH - 18;
    }

    private int recipePreviousX(int left) {
        return left + TABLET_WIDTH - 57;
    }

    private int recipeNextX(int left) {
        return left + TABLET_WIDTH - 39;
    }

    private int recipeNavigationY(int top) {
        return top + TABLET_HEIGHT - 81;
    }

    private List<FormattedCharSequence> formattedLines(Component component, int maxWidth) {
        String body = normalizeText(component.getString());
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String paragraph : body.split("\n", -1)) {
            String display = paragraph.startsWith("- ") ? "• " + paragraph.substring(2)
                    : paragraph;
            if (display.isEmpty()) {
                lines.add(Component.empty().getVisualOrderText());
            } else {
                lines.addAll(font.split(Component.literal(display), maxWidth));
            }
        }
        return lines;
    }

    private String normalizeText(String value) {
        return value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\\n", "\n")
                .replace("\\t", "    ")
                .replace("\t", "    ");
    }

    private Component singleLine(Component component) {
        return Component.literal(normalizeText(component.getString())
                .replace("\n", " ").replaceAll("\\s{2,}", " ").trim());
    }

    private Component trim(Component component, int maxWidth) {
        Component singleLine = singleLine(component);
        if (font.width(singleLine) <= maxWidth) return singleLine;
        return Component.literal(font.substrByWidth(singleLine, maxWidth - font.width("…"))
                .getString() + "…");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum ScrollTarget {
        NONE,
        NAVIGATION,
        CONTENT
    }
}
