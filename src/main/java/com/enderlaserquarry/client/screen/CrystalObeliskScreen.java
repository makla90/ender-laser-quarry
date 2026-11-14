package com.enderlaserquarry.client.screen;

import com.enderio.enderio.foundation.state.MachineState;
import com.enderlaserquarry.content.obelisk.CrystalObeliskMenu;
import com.enderlaserquarry.content.obelisk.LaserCrystalItem;
import com.enderlaserquarry.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.enderio.core.client.gui.screen.EnderContainerScreen;
import com.enderio.enderio.client.content.machines.gui.widget.ActivityWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Status icon and crystal slot on the left, enchantments right of them, the obelisk's status under it. */
public class CrystalObeliskScreen extends EnderContainerScreen<CrystalObeliskMenu> {
    private static final int TEXT_COLOR = 0xFF404040;
    /** Slot face colour, mostly opaque: the crystal shows faintly in the empty slot. */
    private static final int GHOST_COVER = 0xB08B8B8B;
    private static final int ENCHANT_X = CrystalObeliskMenu.CRYSTAL_X + 22;
    /** First line under the title. */
    private static final int ENCHANT_TOP = 17;
    private static final int STATUS_Y = 56;
    /** Enchantment names, like their tooltip purple, darkened for the light panel. */
    private static final int ENCHANT_COLOR = 0xFF7A2FBF;

    private final ItemStack ghost = new ItemStack(ModItems.LASER_CRYSTAL.get());

    public CrystalObeliskScreen(CrystalObeliskMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, CrystalObeliskMenu.PLAYER_INV_Y + 82);
        // no "Inventory" label: the status lines use that room
        inventoryLabelY = -10_000;
        shouldRenderLabels = true;
    }

    @Override
    protected void init() {
        super.init();
        centerAlignTitleLabelX();
        // Ender IO status icon, left of the crystal (the enchantment lines use the right side)
        addRenderableWidget(new ActivityWidget(leftPos + 8, topPos + CrystalObeliskMenu.CRYSTAL_Y, menu::getStates));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        GuiPanels.panel(g, leftPos, topPos, imageWidth, imageHeight);
        int x = leftPos + CrystalObeliskMenu.CRYSTAL_X;
        int y = topPos + CrystalObeliskMenu.CRYSTAL_Y;
        GuiPanels.slot(g, x, y);
        if (!menu.getSlot(0).hasItem()) {
            g.fakeItem(ghost, x, y);
            g.pose().pushMatrix();
            g.nextStratum();
            g.fill(x, y, x + 16, y + 16, GHOST_COVER);
            g.pose().popMatrix();
        }
        GuiPanels.playerInventory(g, leftPos + CrystalObeliskMenu.PLAYER_INV_X, topPos + CrystalObeliskMenu.PLAYER_INV_Y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        MachineState problem = menu.getProblem();
        Component state = problem != null
            ? problem.component().copy().withStyle(ChatFormatting.DARK_RED)
            : Component.translatable("message.ender_laser_quarry.obelisk.ready").withStyle(ChatFormatting.DARK_GREEN);
        List<Component> lines = new ArrayList<>(List.of(
            Component.translatable("gui.ender_laser_quarry.obelisk.status", state),
            Component.translatable("gui.ender_laser_quarry.obelisk.dishes", menu.getActiveDishes(), menu.getDishes()),
            Component.translatable("gui.ender_laser_quarry.obelisk.satellite", Component.translatable(menu.isLinked()
                ? "message.ender_laser_quarry.obelisk.linked"
                : "message.ender_laser_quarry.obelisk.unlinked"))));
        ItemStack crystal = menu.getSlot(0).getItem();
        // broken: the status line says it
        if (crystal.isDamageableItem() && !LaserCrystalItem.isBroken(crystal)) {
            int left = crystal.getMaxDamage() - crystal.getDamageValue();
            ChatFormatting color = LaserCrystalItem.isWorn(crystal) ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY;
            lines.add(Component.translatable("gui.ender_laser_quarry.obelisk.durability", left, crystal.getMaxDamage()).withStyle(color));
        }
        var enchantments = menu.getSlot(0).getItem().getEnchantments();
        boolean showCost = !enchantments.isEmpty() && menu.getCostMultiplier() > 1;
        // centred on the crystal; 4 lines (Fortune / Silk Touch, Unbreaking, Mending, cost) fill ENCHANT_TOP..STATUS_Y
        int enchantY = Math.max(ENCHANT_TOP, CrystalObeliskMenu.CRYSTAL_Y + 1 + (16 - 9 * (enchantments.size() + (showCost ? 1 : 0))) / 2);
        int enchantWidth = imageWidth - ENCHANT_X - 6;
        for (var entry : enchantments.entrySet()) {
            Component name = Enchantment.getFullname(entry.getKey(), entry.getIntValue());
            g.text(font, font.plainSubstrByWidth(name.getString(), enchantWidth), ENCHANT_X, enchantY, ENCHANT_COLOR, false);
            enchantY += 9;
        }
        if (showCost) {
            Component cost = Component.translatable("gui.ender_laser_quarry.obelisk.enchant_cost",
                LaserCrystalItem.formatMultiplier(menu.getCostMultiplier()));
            g.text(font, font.plainSubstrByWidth(cost.getString(), enchantWidth), ENCHANT_X, enchantY, TEXT_COLOR, false);
        }

        int y = STATUS_Y;
        for (Component line : lines) {
            // wrap long problems
            for (var part : font.split(line, imageWidth - 16)) {
                g.text(font, part, 8, y, TEXT_COLOR, false);
                y += 9;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (hoveredSlot == menu.getSlot(0) && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.ender_laser_quarry.obelisk.crystal_hint")), mouseX, mouseY);
        }
    }
}
