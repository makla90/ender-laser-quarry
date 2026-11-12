package com.enderlaserquarry.client.screen;

import com.enderlaserquarry.content.QuarryStates;
import com.enderio.enderio.client.foundation.widgets.ioconfig.IOConfigOverlay;
import com.enderio.enderio.client.content.machines.gui.screen.base.MachineScreen;
import com.enderio.enderio.client.content.machines.gui.widget.ActivityWidget;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.client.foundation.widgets.FluidStackWidget;
import com.enderio.enderio.foundation.lang.EIOCommonLang;
import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.controller.DepthMode;
import com.enderlaserquarry.content.controller.MinimapSnapshot;
import com.enderlaserquarry.content.controller.QuarryControllerBlockEntity;
import com.enderlaserquarry.content.controller.QuarryControllerMenu;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.enderlaserquarry.init.ModItems;
import com.enderlaserquarry.network.MinimapPayload;
import com.enderlaserquarry.network.QuarryActionPayload;
import com.enderlaserquarry.network.SpaceMapPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class QuarryControllerScreen extends MachineScreen<QuarryControllerMenu> {
    private static final Identifier MINIMAP_TEXTURE = EnderLaserQuarry.rl("dynamic/quarry_minimap");

    private static final int MAP_X = 8;
    private static final int MAP_Y = 18;
    private static final int MAP_SIZE = 144;
    // two text lines at the bottom of the map
    private static final int STATUS_STRIP_HEIGHT = 22;
    private static final int REFRESH_INTERVAL = 40;
    private static final int DEPTH_X = QuarryControllerMenu.CAPACITOR_X - 2;
    private static final int DEPTH_Y = 136;
    private static final int FLOOR_ARROW_X = DEPTH_X + 22;
    private static final int FLOOR_ARROW_WIDTH = 12;
    private static final int FLOOR_ARROW_HEIGHT = 10;
    // how long a clicked Y is shown before the synced one takes over again
    private static final int FLOOR_PENDING_TICKS = 20;

    private static final int UNKNOWN_A = 0xFF2B2B2B;
    private static final int UNKNOWN_B = 0xFF333333;
    private static final int GRID = 0x40000000;
    private static final int HOVER = 0x60FFFFFF;
    private static final int BOX_ADD = 0xFF7DFFF0;
    private static final int BOX_REMOVE = 0xFFFFB347;
    private static final int BOX_TOO_MANY = 0xFFFF4A4A;
    private static final int SELECTED_FILL = 0x5000E5FF;
    private static final int SELECTED_EDGE = 0xFF00E5FF;
    private static final int DONE_FILL = 0x60000000;
    private static final int DONE_EDGE = 0xFF3A6A70;
    private static final int PAD_MARKER = 0xFFFF3A2A;
    private static final int CONTROLLER_MARKER = 0xFFFFD700;
    private static final int NO_DATA = 0xD0141820;
    private static final int NO_DATA_LINE = 0x40FFFFFF;
    private static final int SCANNING_FILL = 0x403CFF6E;
    private static final int SCANNING_EDGE = 0xFF3CFF6E;

    @Nullable
    private MinimapSnapshot snapshot;
    @Nullable
    private BlockPos padPos;
    // also the cache for the chunk pictures
    @Nullable
    private DynamicTexture texture;
    private int imageRevision;

    private Button startStopButton;
    @Nullable
    private Button clearButton;
    @Nullable
    private Button depthButton;
    @Nullable
    private Button floorUpButton;
    @Nullable
    private Button floorDownButton;
    // shown until synced so fast clicks add up
    @Nullable
    private Integer pendingFloorY;
    private int pendingFloorTicks;
    // refresh, or scan space with a void crystal
    private Button refreshButton;
    private NamedTankWidget dewTank;
    private final SpaceMapView spaceView = new SpaceMapView(MAP_SIZE);
    private float lastScanProgress = -1;
    // asteroid picked for Analyze, -1 = none
    private int pickedAsteroid = -1;
    private int lastAnalyzeId = -1;
    
    private Button analyzeButton;
    @Nullable
    private IOConfigOverlay ioConfigOverlay;
    private int ticksUntilRefresh;

    // region Orbit planner state

    private static final int ORBIT_AREA_FILL = 0x2200E5FF;
    private static final int ORBIT_AREA_EDGE = 0xFF00E5FF;
    private static final int REACH_EDGE = 0xFF7DFFF0;
    private static final int GHOST_EDGE = 0xFFFFFFFF;
    private static final int GHOST_BAD = 0xFFFF4A4A;
    private static final int TARGET_EDGE = 0xFFFFC23A;
    private static final int TETHER = 0x90A0B0C0;
    private static final long FLASH_MS = 900;

    // the orbit planner is up (instead of the mining map)
    private boolean planning;
    // picked target, dx / dz in chunks from the pad's chunk
    @Nullable
    private ChunkPos planned;
    private Button plannerButton;
    private static final int PLANNER_BUTTON_WIDTH = 52;
    private static final float PLANNER_ICON_SCALE = 10 / 16f;
    private Button moveButton;
    // where the satellite was before the fly-over (the menu already has the new spot by then)
    private int fromDx;
    private int fromDz;
    private int lastTransferState;
    private long arrivalFlashStart = -1;

    // endregion

    public QuarryControllerScreen(QuarryControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, QuarryControllerMenu.GUI_WIDTH, 258);
        inventoryLabelX = QuarryControllerMenu.PLAYER_INV_X;
        inventoryLabelY = QuarryControllerMenu.PLAYER_INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();

        int rightX = leftPos + QuarryControllerMenu.OUTPUT_X - 1;

        startStopButton = addRenderableWidget(Button.builder(Component.empty(), b -> send(menu.isRunning()
                ? QuarryActionPayload.Action.STOP
                : QuarryActionPayload.Action.START))
            .bounds(rightX + 20, topPos + 94, 36, 20)
            .build());
        updateButtons();

        refreshButton = addRenderableWidget(Button.builder(Component.translatable("gui.ender_laser_quarry.controller.refresh"),
                b -> {
                    if (menu.isVoidMode()) {
                        send(QuarryActionPayload.Action.SCAN_SPACE);
                    } else {
                        requestMinimap();
                    }
                })
            .bounds(rightX, topPos + 116, 56, 14)
            .build());

        // on the space map, above the status strip, only while an unknown asteroid is picked
        analyzeButton = addRenderableWidget(Button.builder(Component.translatable("gui.ender_laser_quarry.controller.analyze"),
                b -> {
                    if (pickedAsteroid >= 0) {
                        ClientPacketDistributor.sendToServer(QuarryActionPayload.analyzeAsteroid(menu.getBlockEntity().getBlockPos(), pickedAsteroid));
                    }
                })
            .bounds(leftPos + MAP_X + MAP_SIZE - 54, topPos + MAP_Y + MAP_SIZE - 39, 52, 14)
            .build());
        analyzeButton.visible = false;

        // orbit planner: toggle in the map's top left corner, launch button where Analyze sits
        plannerButton = addRenderableWidget(Button.builder(plannerLabel(false), b -> togglePlanner())
            .bounds(leftPos + MAP_X + 2, topPos + MAP_Y + 2, PLANNER_BUTTON_WIDTH, 12)
            .build());
        moveButton = addRenderableWidget(Button.builder(Component.translatable("gui.ender_laser_quarry.orbit.launch"), b -> {
                if (planned != null) {
                    ClientPacketDistributor.sendToServer(QuarryActionPayload.transferSatellite(menu.getBlockEntity().getBlockPos(), planned.x(), planned.z()));
                }
            })
            .bounds(leftPos + MAP_X + MAP_SIZE - 54, topPos + MAP_Y + MAP_SIZE - 39, 52, 14)
            .build());
        moveButton.visible = false;

        addRenderableWidget(new RedstoneControlPickerWidget(rightX, topPos + 138,
            menu::getRedstoneControl, menu::setRedstoneControl, EIOCommonLang.REDSTONE_MODE));

        // IO config for the ports, stops above the status strip
        ioConfigOverlay = addIOConfigOverlay(1, leftPos + MAP_X, topPos + MAP_Y, MAP_SIZE, MAP_SIZE - STATUS_STRIP_HEIGHT);
        addIOConfigButton(rightX + 20, topPos + 138, ioConfigOverlay);
        addRenderableWidget(new ActivityWidget(rightX + 40, topPos + 138, menu::getMachineStates));

        addRenderableOnly(new UsageEnergyWidget(leftPos + QuarryControllerMenu.ENERGY_X, topPos + QuarryControllerMenu.ENERGY_Y, 9,
            QuarryControllerMenu.ENERGY_HEIGHT, menu::getEnergyStorage, menu::isCapacitorInstalled, () -> {
                int own = menu.getOwnUsage();
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("gui.ender_laser_quarry.energy.usage", UsageEnergyWidget.FORMAT.format(own))
                    .withStyle(ChatFormatting.AQUA));
                if (menu.getPadEnergy() >= 0) {
                    // the launch pad's Ground Radar, fed over the antenna link
                    lines.add(Component.translatable("gui.ender_laser_quarry.controller.antenna_sent",
                        UsageEnergyWidget.FORMAT.format(menu.getAntennaSent())).withStyle(ChatFormatting.AQUA));
                }
                lines.add(Component.translatable("gui.ender_laser_quarry.controller.intake", UsageEnergyWidget.FORMAT.format(menu.getEnergyIntake()))
                    .withStyle(ChatFormatting.DARK_AQUA));
                lines.add(Component.translatable("gui.ender_laser_quarry.controller.port_output", UsageEnergyWidget.FORMAT.format(menu.getPortItemRate()))
                    .withStyle(ChatFormatting.DARK_AQUA));
                return lines;
            }));
        // coolant
        addRenderableOnly(new NamedTankWidget(leftPos + QuarryControllerMenu.COOLANT_X, topPos + QuarryControllerMenu.COOLANT_Y, 9,
            QuarryControllerMenu.COOLANT_HEIGHT, menu::getCoolant, "minecraft:water"));
        // liquid XP, for the crystal's Mending
        addRenderableOnly(new NamedTankWidget(leftPos + QuarryControllerMenu.XP_X, topPos + QuarryControllerMenu.COOLANT_Y, 9,
            QuarryControllerMenu.COOLANT_HEIGHT, menu::getXp, "enderio:fluid_xp_juice_still"));
        dewTank = addRenderableOnly(new NamedTankWidget(leftPos + QuarryControllerMenu.DEW_X, topPos + QuarryControllerMenu.COOLANT_Y, 9,
            QuarryControllerMenu.COOLANT_HEIGHT, this::dewInfo, "enderio:fluid_dew_of_the_void_still"));

        clearButton = addRenderableWidget(Button.builder(Component.translatable("gui.ender_laser_quarry.controller.clear"),
                b -> send(QuarryActionPayload.Action.CLEAR_SELECTION))
            .bounds(rightX, topPos + 157, 56, 14)
            .build());

        // depth mode, Enhanced+ only
        depthButton = addRenderableWidget(new Button(leftPos + DEPTH_X, topPos + DEPTH_Y, 20, 20, Component.empty(),
            b -> ClientPacketDistributor.sendToServer(QuarryActionPayload.setDepthMode(menu.getBlockEntity().getBlockPos(),
                menu.getDepthMode().next())), narration -> narration.get()) {
            @Override
            protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
                extractDefaultSprite(g);
                g.item(menu.getDepthMode().icon(), getX() + 2, getY() + 2);
                if (menu.getDepthMode() == DepthMode.Y_LEVEL) {
                    // floor Y drawn like an item count
                    String y = Integer.toString(shownFloorY());
                    g.pose().pushMatrix();
                    g.nextStratum();
                    g.text(font, y, getX() + getWidth() - 1 - font.width(y), getY() + getHeight() - 9, 0xFFFFFFFF, true);
                    g.pose().popMatrix();
                }
            }
        });
        floorUpButton = addRenderableWidget(new FloorArrow(leftPos + FLOOR_ARROW_X, topPos + DEPTH_Y, true));
        floorDownButton = addRenderableWidget(new FloorArrow(leftPos + FLOOR_ARROW_X, topPos + DEPTH_Y + FLOOR_ARROW_HEIGHT, false));
        updateButtons();

        requestMinimap();
    }

    // region Floor Y (Ender)

    private boolean showsFloorControl() {
        return menu.getBlockEntity().getTier() == QuarryTier.ENDER && menu.getDepthMode() == DepthMode.Y_LEVEL
            && !menu.isVoidMode();
    }

    private int shownFloorY() {
        return pendingFloorY != null ? pendingFloorY : menu.getFloorY();
    }

    // shift = 10
    private void stepFloorY(int direction) {
        if (!showsFloorControl() || menu.isRunning()) {
            return;
        }
        var level = menu.getBlockEntity().getLevel();
        int y = shownFloorY() + direction * (Minecraft.getInstance().hasShiftDown() ? 10 : 1);
        if (level != null) {
            y = DepthMode.clampY(level, y);
        }
        if (y == shownFloorY()) {
            return;
        }
        pendingFloorY = y;
        pendingFloorTicks = FLOOR_PENDING_TICKS;
        ClientPacketDistributor.sendToServer(QuarryActionPayload.setFloorY(menu.getBlockEntity().getBlockPos(), y));
    }

    private boolean isOverFloorControl(double mouseX, double mouseY) {
        int x = leftPos + DEPTH_X;
        int y = topPos + DEPTH_Y;
        return showsFloorControl() && mouseX >= x && mouseX < leftPos + FLOOR_ARROW_X + FLOOR_ARROW_WIDTH
            && mouseY >= y && mouseY < y + 20;
    }

    private class FloorArrow extends Button {
        private final boolean up;

        FloorArrow(int x, int y, boolean up) {
            super(x, y, FLOOR_ARROW_WIDTH, FLOOR_ARROW_HEIGHT, Component.empty(), b -> stepFloorY(up ? 1 : -1),
                narration -> narration.get());
            this.up = up;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            extractDefaultSprite(g);
            int colour = active ? 0xFFFFFFFF : 0xFFA0A0A0;
            int cx = getX() + getWidth() / 2;
            int top = getY() + (getHeight() - 3) / 2;
            for (int row = 0; row < 3; row++) {
                int half = up ? row : 2 - row;
                g.fill(cx - half, top + row, cx + half + 1, top + row + 1, colour);
            }
        }
    }

    // endregion

    // region Minimap data

    // only asks for pictures newer than the ones we have
    private void requestMinimap() {
        ticksUntilRefresh = REFRESH_INTERVAL;
        BlockPos controller = menu.getBlockEntity().getBlockPos();
        ClientPacketDistributor.sendToServer(planning
            ? QuarryActionPayload.requestPlanner(controller, imageRevision)
            : QuarryActionPayload.requestMinimap(controller, imageRevision));
    }

    // region Orbit planner

    private boolean canPlan() {
        return !menu.isVoidMode() && padPos != null && menu.getTransferRange() > 0;
    }

    private void togglePlanner() {
        planning = !planning;
        planned = null;
        // the other view's area: start over from revision 0
        imageRevision = 0;
        requestMinimap();
        playUi(planning ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.UI_BUTTON_CLICK.value(), planning ? 1.8f : 1f, 0.35f);
    }

    private static void playUi(net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // dx / dz from the pad's chunk -> world chunk
    private ChunkPos padOffsetChunk(int dx, int dz) {
        ChunkPos pad = ChunkPos.containing(padPos);
        return new ChunkPos(pad.x() + dx, pad.z() + dz);
    }

    // the footprint radius: the planner area is reach + footprint around the pad
    private int footprintRadius() {
        return Math.max(0, snapshot.radius() - menu.getTransferRange());
    }

    private boolean canReach(int dx, int dz) {
        return Math.max(Math.abs(dx), Math.abs(dz)) <= menu.getTransferRange();
    }

    private double transferDistance(int dx, int dz) {
        double x = dx - menu.getOrbitDx();
        double z = dz - menu.getOrbitDz();
        return Math.sqrt(x * x + z * z);
    }

    private int transferCost(int dx, int dz) {
        return menu.getTransferBaseFuel() + (int) Math.ceil(transferDistance(dx, dz) * menu.getFuelPerChunk());
    }

    private boolean isMoving() {
        return menu.getTransferState() != 0;
    }

    // offset under the mouse, null outside the map
    @Nullable
    private ChunkPos offsetAt(double mouseX, double mouseY) {
        if (!planning || padPos == null || snapshot == null) {
            return null;
        }
        ChunkPos chunk = chunkAt(mouseX, mouseY);
        if (chunk == null) {
            return null;
        }
        ChunkPos pad = ChunkPos.containing(padPos);
        return new ChunkPos(chunk.x() - pad.x(), chunk.z() - pad.z());
    }

    // dev client test: drive the planner without a mouse
    public void debugSetPlanner(boolean on) {
        if (on != planning && (canPlan() || planning)) {
            togglePlanner();
        }
    }

    public void debugPick(int dx, int dz) {
        planned = new ChunkPos(dx, dz);
    }

    public void debugLaunch() {
        if (moveButton != null && moveButton.active) {
            moveButton.onPress(null);
        }
    }

    // block coordinates of an area's middle, the way players read them
    private Component coords(int dx, int dz) {
        ChunkPos chunk = padOffsetChunk(dx, dz);
        return Component.translatable("gui.ender_laser_quarry.orbit.coords", chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
    }

    // endregion

    public boolean isFor(BlockPos controllerPos) {
        return menu.getBlockEntity().getBlockPos().equals(controllerPos);
    }

    // Paints the changed chunks into the texture, a different area starts over
    public void setMinimap(MinimapPayload payload) {
        // an answer for the view we just left
        if (payload.planner() != planning) {
            return;
        }
        MinimapSnapshot next = payload.snapshot();
        boolean hadArea = snapshot != null;
        boolean sameArea = next.sameArea(snapshot);
        boolean resized = snapshot == null || snapshot.sizeInBlocks() != next.sizeInBlocks();
        snapshot = next;
        padPos = payload.padPos().orElse(null);
        if (resized) {
            zoom = 1;
            viewX = 0;
            viewZ = 0;
        }

        int size = snapshot.sizeInBlocks();
        if (!sameArea || texture == null || texture.getPixels() == null) {
            releaseTexture();
            texture = new DynamicTexture("ender_laser_quarry_minimap", size, size, true);
            minecraft.getTextureManager().register(MINIMAP_TEXTURE, texture);
            imageRevision = 0;
        }

        NativeImage pixels = texture.getPixels();
        var min = snapshot.minChunk();
        for (MinimapPayload.ChunkImage image : payload.images()) {
            ChunkPos chunk = ChunkPos.unpack(image.chunk());
            if (!snapshot.contains(chunk)) {
                continue;
            }
            int originX = (chunk.x() - min.x()) * 16;
            int originZ = (chunk.z() - min.z()) * 16;
            byte[] data = image.pixels();
            for (int i = 0; i < 256; i++) {
                // MapColor is ARGB in 26.1
                pixels.setPixel(originX + (i & 15), originZ + (i >> 4), MapColor.getColorFromPackedId(data[i] & 0xFF));
            }
        }
        texture.upload();

        if (hadArea && !sameArea) {
            requestMinimap();
        } else {
            imageRevision = payload.revision();
            if (payload.more()) {
                requestMinimap();
            }
        }
    }

    public void setSpaceMap(SpaceMapPayload payload) {
        spaceView.set(payload);
    }

    private void releaseTexture() {
        if (texture != null) {
            minecraft.getTextureManager().release(MINIMAP_TEXTURE);
            texture = null;
        }
    }

    @Override
    public void removed() {
        super.removed();
        releaseTexture();
        if (noDataTexture != null) {
            minecraft.getTextureManager().release(NO_DATA_TEXTURE);
            noDataTexture = null;
        }
        spaceView.release();
    }

    // endregion

    @Override
    protected void containerTick() {
        super.containerTick();
        if (pendingFloorY != null && (pendingFloorY == menu.getFloorY() || --pendingFloorTicks <= 0)) {
            pendingFloorY = null;
        }
        updateButtons();

        if (--ticksUntilRefresh <= 0) {
            requestMinimap();
        }

        // scan finished, fetch the asteroids
        float scan = menu.getSpaceScanProgress();
        if (lastScanProgress >= 0 && scan < 0) {
            requestMinimap();
        }
        lastScanProgress = scan;

        // analysis done: fetch the rock
        int analyzing = menu.getAnalyzeId();
        if (lastAnalyzeId >= 0 && analyzing != lastAnalyzeId) {
            requestMinimap();
        }
        if (analyzing >= 0 && !menu.isAnalyzeStalled() && ticksUntilRefresh % 5 == 0) {
            // quiet computer chirps while the data comes in
            float pitch = 1.3f + (float) Math.random() * 0.7f;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), pitch, 0.12f));
        }
        lastAnalyzeId = analyzing;

        tickOrbitPlanner();
    }

    private void tickOrbitPlanner() {
        if (planning && !canPlan()) {
            planning = false;
            planned = null;
            imageRevision = 0;
            requestMinimap();
        }
        int state = menu.getTransferState();
        if (state != 2) {
            // the menu has the new spot as soon as the fly-over starts
            fromDx = menu.getOrbitDx();
            fromDz = menu.getOrbitDz();
        }
        if (state == 1 && lastTransferState == 0) {
            planned = null;
        }
        if (state == 0 && lastTransferState == 2) {
            arrivalFlashStart = System.currentTimeMillis();
            playUi(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.8f);
            playUi(SoundEvents.PLAYER_LEVELUP, 1.6f, 0.25f);
            // the new area's pictures
            requestMinimap();
        }
        if (state == 2 && ticksUntilRefresh % 4 == 0) {
            playUi(SoundEvents.NOTE_BLOCK_BIT.value(), 1.6f + (float) Math.random() * 0.5f, 0.08f);
        }
        if (state != lastTransferState && planning) {
            requestMinimap();
        }
        lastTransferState = state;
    }

    private void updateButtons() {
        boolean running = menu.isRunning();
        startStopButton.setMessage(Component.translatable(running
            ? "gui.ender_laser_quarry.controller.stop"
            : "gui.ender_laser_quarry.controller.start"));
        boolean voidMode = menu.isVoidMode();
        boolean hasQueue = voidMode ? !menu.getAsteroidQueue().isEmpty() : !menu.getQueue().isEmpty();
        startStopButton.active = running || hasQueue;
        if (clearButton != null) {
            clearButton.active = !running && hasQueue;
        }
        if (depthButton != null) {
            depthButton.active = !running && !voidMode && menu.getBlockEntity().getTier() != QuarryTier.BASIC;
        }
        boolean floorShown = showsFloorControl();
        for (Button arrow : new Button[] { floorUpButton, floorDownButton }) {
            if (arrow != null) {
                arrow.visible = floorShown;
                arrow.active = floorShown && !running;
            }
        }
        if (analyzeButton != null) {
            SpaceMapPayload.AsteroidInfo picked = pickedInfo();
            if (picked == null || picked.analyzed()) {
                pickedAsteroid = -1;
            }
            analyzeButton.visible = voidMode && picked != null && !picked.analyzed() && menu.getAnalyzeId() != picked.id()
                && spaceView.hasData() && (ioConfigOverlay == null || !ioConfigOverlay.visible);
            analyzeButton.active = menu.getAnalyzeId() < 0 && menu.getSpaceScanProgress() < 0;
        }
        boolean ioOpen = ioConfigOverlay != null && ioConfigOverlay.visible;
        if (plannerButton != null) {
            plannerButton.visible = !voidMode && padPos != null && !ioOpen;
            plannerButton.active = canPlan() || planning;
            plannerButton.setMessage(plannerLabel(planning));
        }
        if (moveButton != null) {
            moveButton.visible = planning && planned != null && !isMoving() && !ioOpen;
            moveButton.active = planned != null && menu.isRocketReady() && menu.hasSatelliteInOrbit() && !running
                && menu.getPadFuel() >= transferCost(planned.x(), planned.z());
        }
        if (refreshButton != null) {
            refreshButton.setMessage(Component.translatable(voidMode
                ? "gui.ender_laser_quarry.controller.scan_space"
                : "gui.ender_laser_quarry.controller.refresh"));
            refreshButton.active = !voidMode || (!running && menu.getSpaceScanProgress() < 0 && menu.getAnalyzeId() < 0
                && menu.getSatelliteDew() >= menu.getScanDew());
        }
        if (dewTank != null) {
            dewTank.visible = showsDew();
        }
    }

    private void send(QuarryActionPayload.Action action) {
        ClientPacketDistributor.sendToServer(QuarryActionPayload.of(menu.getBlockEntity().getBlockPos(), action));
    }

    // region Map view (zoom + pan)

    private static final double ZOOM_STEP = 1.25;
    // max zoom still shows this many chunks
    private static final int MIN_VIEW_CHUNKS = 5;

    // 1 = everything fits
    private double zoom = 1;
    // view corner in blocks from the snapshot corner
    private double viewX;
    private double viewZ;

    private double viewSize() {
        return snapshot.sizeInBlocks() / zoom;
    }

    private double maxZoom() {
        return Math.max(1, (snapshot.radius() * 2 + 1) / (double) MIN_VIEW_CHUNKS);
    }

    private void clampView() {
        double max = snapshot.sizeInBlocks() - viewSize();
        viewX = Mth.clamp(viewX, 0, max);
        viewZ = Mth.clamp(viewZ, 0, max);
    }

    private int screenX(double blockX) {
        return leftPos + MAP_X + (int) Math.round((blockX - viewX) * MAP_SIZE / viewSize());
    }

    private int screenY(double blockZ) {
        return topPos + MAP_Y + (int) Math.round((blockZ - viewZ) * MAP_SIZE / viewSize());
    }

    // endregion

    // region Input

    // box select: adds or removes, decided by the first chunk. null = not dragging
    @Nullable
    private Boolean dragQueues;
    @Nullable
    private ChunkPos boxStart;
    @Nullable
    private ChunkPos boxEnd;
    private boolean panning;

    // not while the IO config view covers the map
    private boolean isMapInteractive() {
        return snapshot != null && (ioConfigOverlay == null || !ioConfigOverlay.visible);
    }

    private boolean isOverMap(double mouseX, double mouseY) {
        double localX = mouseX - (leftPos + MAP_X);
        double localY = mouseY - (topPos + MAP_Y);
        return localX >= 0 && localY >= 0 && localX < MAP_SIZE && localY < MAP_SIZE;
    }

    @Nullable
    private SpaceMapPayload.AsteroidInfo pickedInfo() {
        for (var asteroid : spaceView.asteroids()) {
            if (asteroid.id() == pickedAsteroid) {
                return asteroid;
            }
        }
        return null;
    }

    private boolean isSpaceInteractive() {
        return menu.isVoidMode() && spaceView.hasData() && (ioConfigOverlay == null || !ioConfigOverlay.visible);
    }

    @Nullable
    private SpaceMapPayload.AsteroidInfo asteroidAt(double mouseX, double mouseY) {
        if (!isSpaceInteractive() || !isOverMap(mouseX, mouseY)) {
            return null;
        }
        return spaceView.asteroidAt(mouseX - (leftPos + MAP_X), mouseY - (topPos + MAP_Y));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (menu.isVoidMode()) {
            boolean overButton = analyzeButton != null && analyzeButton.visible && analyzeButton.isMouseOver(mouseX, mouseY);
            SpaceMapPayload.AsteroidInfo asteroid = button == 0 && !overButton ? asteroidAt(mouseX, mouseY) : null;
            if (asteroid != null && !asteroid.analyzed()) {
                // unknown rock: pick it for Analyze (also while mining)
                pickedAsteroid = pickedAsteroid == asteroid.id() ? -1 : asteroid.id();
                return true;
            }
            if (asteroid != null && !menu.isRunning()) {
                ClientPacketDistributor.sendToServer(QuarryActionPayload.setAsteroidQueued(menu.getBlockEntity().getBlockPos(),
                    asteroid.id(), !menu.getAsteroidQueue().contains(asteroid.id())));
                return true;
            }
            if (button == 1 && isSpaceInteractive() && isOverMap(mouseX, mouseY) && spaceView.zoom() > 1) {
                panning = true;
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        boolean overPlannerButtons = (plannerButton != null && plannerButton.visible && plannerButton.isMouseOver(mouseX, mouseY))
            || (moveButton != null && moveButton.visible && moveButton.isMouseOver(mouseX, mouseY));
        if (planning && !overPlannerButtons && isMapInteractive() && isOverMap(mouseX, mouseY)) {
            if (button == 0) {
                ChunkPos offset = offsetAt(mouseX, mouseY);
                if (offset != null && !isMoving()) {
                    boolean here = offset.x() == menu.getOrbitDx() && offset.z() == menu.getOrbitDz();
                    if (canReach(offset.x(), offset.z()) && !here) {
                        planned = offset.equals(planned) ? null : offset;
                        playUi(SoundEvents.NOTE_BLOCK_CHIME.value(), planned != null ? 1.5f : 1.0f, 0.4f);
                    } else {
                        playUi(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.4f);
                    }
                }
                return true;
            }
            if (button == 1 && zoom > 1) {
                panning = true;
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (!planning && isMapInteractive() && isOverMap(mouseX, mouseY) && !overPlannerButtons) {
            if (button == 0 && !menu.isRunning()) {
                ChunkPos chunk = chunkAt(mouseX, mouseY);
                if (chunk != null) {
                    // after a finished queue the drag always adds
                    List<ChunkPos> queue = menu.getQueue();
                    boolean finished = !queue.isEmpty() && menu.getChunkIndex() >= queue.size();
                    dragQueues = finished || !queue.contains(chunk);
                    boxStart = chunk;
                    boxEnd = chunk;
                    return true;
                }
            }
            if (button == 1 && zoom > 1) {
                panning = true;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == 0 && dragQueues != null) {
            ChunkPos chunk = chunkAtClamped(mouseX, mouseY);
            if (chunk != null) {
                boxEnd = chunk;
            }
            return true;
        }
        if (button == 1 && panning && menu.isVoidMode()) {
            spaceView.pan(dragX, dragY);
            return true;
        }
        if (button == 1 && panning && snapshot != null) {
            viewX -= dragX * viewSize() / MAP_SIZE;
            viewZ -= dragY * viewSize() / MAP_SIZE;
            clampView();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == 0 && dragQueues != null) {
            BlockPos controller = menu.getBlockEntity().getBlockPos();
            if (boxStart != null && boxStart.equals(boxEnd)) {
                // single click toggles one chunk
                if (snapshot.scanState(boxStart) == MinimapSnapshot.SCANNED) {
                    ClientPacketDistributor.sendToServer(QuarryActionPayload.setQueued(controller, boxStart, dragQueues));
                }
            } else if (boxStart != null && boxEnd != null && (!dragQueues || boxTotal() <= menu.getMaxChunks())) {
                // over the limit adds nothing (the preview is red)
                ClientPacketDistributor.sendToServer(QuarryActionPayload.setBoxQueued(controller, boxStart, boxEnd, dragQueues));
            }
            dragQueues = null;
            boxStart = null;
            boxEnd = null;
            return true;
        }
        if (button == 1 && panning) {
            panning = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && isOverFloorControl(mouseX, mouseY)) {
            stepFloorY(scrollY > 0 ? 1 : -1);
            return true;
        }
        if (scrollY != 0 && isSpaceInteractive() && isOverMap(mouseX, mouseY)) {
            spaceView.scroll(mouseX - (leftPos + MAP_X), mouseY - (topPos + MAP_Y), scrollY);
            return true;
        }
        if (scrollY != 0 && !menu.isVoidMode() && isMapInteractive() && isOverMap(mouseX, mouseY)) {
            // zoom around the mouse
            double localX = mouseX - (leftPos + MAP_X);
            double localY = mouseY - (topPos + MAP_Y);
            double anchorX = viewX + localX * viewSize() / MAP_SIZE;
            double anchorZ = viewZ + localY * viewSize() / MAP_SIZE;
            zoom = Mth.clamp(zoom * Math.pow(ZOOM_STEP, scrollY), 1, maxZoom());
            viewX = anchorX - localX * viewSize() / MAP_SIZE;
            viewZ = anchorZ - localY * viewSize() / MAP_SIZE;
            clampView();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // Chunks the box would add (scanned, not queued yet) or remove (queued)
    private int boxCount() {
        if (boxStart == null || boxEnd == null || dragQueues == null) {
            return 0;
        }
        List<ChunkPos> queue = menu.getQueue();
        boolean fresh = !queue.isEmpty() && menu.getChunkIndex() >= queue.size();
        Set<ChunkPos> queued = fresh ? Set.of() : new HashSet<>(queue);
        int count = 0;
        for (int z = Math.min(boxStart.z(), boxEnd.z()); z <= Math.max(boxStart.z(), boxEnd.z()); z++) {
            for (int x = Math.min(boxStart.x(), boxEnd.x()); x <= Math.max(boxStart.x(), boxEnd.x()); x++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (dragQueues ? !queued.contains(chunk) && snapshot.scanState(chunk) == MinimapSnapshot.SCANNED : queued.contains(chunk)) {
                    count++;
                }
            }
        }
        return count;
    }

    // queue size after adding the box
    private int boxTotal() {
        List<ChunkPos> queue = menu.getQueue();
        boolean fresh = !queue.isEmpty() && menu.getChunkIndex() >= queue.size();
        return (fresh ? 0 : queue.size()) + boxCount();
    }

    // the chunk under the mouse, or the nearest one on the map edge while dragging outside it
    @Nullable
    private ChunkPos chunkAtClamped(double mouseX, double mouseY) {
        int x0 = leftPos + MAP_X;
        int y0 = topPos + MAP_Y;
        return chunkAt(Mth.clamp(mouseX, x0, x0 + MAP_SIZE - 1), Mth.clamp(mouseY, y0, y0 + MAP_SIZE - 1));
    }

    @Nullable
    private ChunkPos chunkAt(double mouseX, double mouseY) {
        if (menu.isVoidMode() || !isMapInteractive() || !isOverMap(mouseX, mouseY)) {
            return null;
        }

        double blockX = viewX + (mouseX - (leftPos + MAP_X)) * viewSize() / MAP_SIZE;
        double blockZ = viewZ + (mouseY - (topPos + MAP_Y)) * viewSize() / MAP_SIZE;
        int chunks = snapshot.radius() * 2 + 1;
        int cx = Mth.clamp(Mth.floor(blockX / 16), 0, chunks - 1);
        int cz = Mth.clamp(Mth.floor(blockZ / 16), 0, chunks - 1);
        var min = snapshot.minChunk();
        return new ChunkPos(min.x() + cx, min.z() + cz);
    }

    // endregion

    // region Rendering

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        GuiPanels.panel(g, leftPos, topPos, imageWidth, imageHeight);
        GuiPanels.inset(g, leftPos + MAP_X - 1, topPos + MAP_Y - 1, MAP_SIZE + 2, MAP_SIZE + 2);

        for (int i = 0; i < QuarryControllerBlockEntity.OUTPUT_SLOTS; i++) {
            GuiPanels.slot(g,
                leftPos + QuarryControllerMenu.OUTPUT_X + (i % QuarryControllerMenu.OUTPUT_COLUMNS) * 18,
                topPos + QuarryControllerMenu.OUTPUT_Y + (i / QuarryControllerMenu.OUTPUT_COLUMNS) * 18);
        }

        GuiPanels.slot(g, leftPos + QuarryControllerMenu.FILTER_X, topPos + QuarryControllerMenu.FILTER_Y);
        GuiPanels.inset(g, leftPos + QuarryControllerMenu.ENERGY_X - 1, topPos + QuarryControllerMenu.ENERGY_Y - 1, 11,
            QuarryControllerMenu.ENERGY_HEIGHT + 2);
        GuiPanels.slot(g, leftPos + QuarryControllerMenu.CAPACITOR_X, topPos + QuarryControllerMenu.CAPACITOR_Y);
        GuiPanels.inset(g, leftPos + QuarryControllerMenu.COOLANT_X - 1, topPos + QuarryControllerMenu.COOLANT_Y - 1, 11,
            QuarryControllerMenu.COOLANT_HEIGHT + 2);
        GuiPanels.inset(g, leftPos + QuarryControllerMenu.XP_X - 1, topPos + QuarryControllerMenu.COOLANT_Y - 1, 11,
            QuarryControllerMenu.COOLANT_HEIGHT + 2);

        GuiPanels.playerInventory(g, leftPos + QuarryControllerMenu.PLAYER_INV_X, topPos + QuarryControllerMenu.PLAYER_INV_Y);

        if (showsDew()) {
            // amber frame while there isn't enough for one scan
            if (menu.getSatelliteDew() < menu.getScanDew()) {
                g.fill(leftPos + QuarryControllerMenu.DEW_X - 2, topPos + QuarryControllerMenu.COOLANT_Y - 2,
                    leftPos + QuarryControllerMenu.DEW_X + 11, topPos + QuarryControllerMenu.COOLANT_Y + QuarryControllerMenu.COOLANT_HEIGHT + 2,
                    0xFFE0A030);
            }
            GuiPanels.inset(g, leftPos + QuarryControllerMenu.DEW_X - 1, topPos + QuarryControllerMenu.COOLANT_Y - 1, 11,
                QuarryControllerMenu.COOLANT_HEIGHT + 2);
        }

        renderMinimap(g, mouseX, mouseY);
    }

    private void renderMinimap(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x0 = leftPos + MAP_X;
        int y0 = topPos + MAP_Y;

        // checker background for unknown chunks
        for (int y = 0; y < MAP_SIZE; y += 8) {
            for (int x = 0; x < MAP_SIZE; x += 8) {
                g.fill(x0 + x, y0 + y, x0 + x + 8, y0 + y + 8, ((x + y) / 8) % 2 == 0 ? UNKNOWN_A : UNKNOWN_B);
            }
        }

        if (menu.isVoidMode()) {
            if (!spaceView.hasData()) {
                g.centeredText(font, Component.translatable("gui.ender_laser_quarry.controller.loading_map"),
                    x0 + MAP_SIZE / 2, y0 + MAP_SIZE / 2 - 4, 0xFFAAAAAA);
                return;
            }
            g.enableScissor(x0, y0, x0 + MAP_SIZE, y0 + MAP_SIZE);
            spaceView.render(g, font, x0, y0, menu.getAsteroidQueue(), menu.isRunning(),
                isSpaceInteractive() ? mouseX : -1000, isSpaceInteractive() ? mouseY : -1000,
                new SpaceMapView.Analysis(menu.getAnalyzeId(), menu.getAnalyzeProgress(), menu.isAnalyzeStalled(), pickedAsteroid));
            g.disableScissor();
            return;
        }

        if (snapshot == null || texture == null) {
            g.centeredText(font, Component.translatable("gui.ender_laser_quarry.controller.loading_map"),
                x0 + MAP_SIZE / 2, y0 + MAP_SIZE / 2 - 4, 0xFFAAAAAA);
            return;
        }

        g.enableScissor(x0, y0, x0 + MAP_SIZE, y0 + MAP_SIZE);

        int size = snapshot.sizeInBlocks();
        int mapLeft = screenX(0);
        int mapTop = screenY(0);
        g.blit(RenderPipelines.GUI_TEXTURED, MINIMAP_TEXTURE, mapLeft, mapTop, 0, 0, screenX(size) - mapLeft, screenY(size) - mapTop, size, size, size, size);

        int chunks = snapshot.radius() * 2 + 1;
        var min = snapshot.minChunk();

        renderScanCoverage(g, chunks, min);

        // no grid when the chunks get too small
        boolean grid = MAP_SIZE / viewSize() * 16 >= 4;
        for (int i = 1; grid && i < chunks; i++) {
            int lineX = screenX(i * 16);
            int lineY = screenY(i * 16);
            g.fill(lineX, y0, lineX + 1, y0 + MAP_SIZE, GRID);
            g.fill(x0, lineY, x0 + MAP_SIZE, lineY + 1, GRID);
        }

        if (planning) {
            renderPlanner(g, mouseX, mouseY, min);
            if (padPos != null) {
                marker(g, padPos, PAD_MARKER);
            }
            marker(g, menu.getBlockEntity().getBlockPos(), CONTROLLER_MARKER);
            g.disableScissor();
            return;
        }

        // queued chunks
        List<ChunkPos> queue = menu.getQueue();
        int current = menu.getChunkIndex();
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 150.0);
        for (int i = 0; i < queue.size(); i++) {
            ChunkPos selected = queue.get(i);
            int sx = selected.x() - min.x();
            int sz = selected.z() - min.z();
            if (sx < 0 || sz < 0 || sx >= chunks || sz >= chunks) {
                continue;
            }
            int left = screenX(sx * 16);
            int top = screenY(sz * 16);
            int right = screenX((sx + 1) * 16);
            int bottom = screenY((sz + 1) * 16);
            if (i < current) {
                g.fill(left, top, right, bottom, DONE_FILL);
                g.outline(left, top, right - left, bottom - top, DONE_EDGE);
            } else {
                g.fill(left, top, right, bottom, SELECTED_FILL);
                int edge = i == current && menu.isRunning()
                    ? ((int) (0x80 + 0x7F * pulse) << 24) | (SELECTED_EDGE & 0xFFFFFF)
                    : SELECTED_EDGE;
                g.outline(left, top, right - left, bottom - top, edge);
                if (i == current) {
                    g.outline(left + 1, top + 1, right - left - 2, bottom - top - 2, edge);
                }
            }
            if (right - left >= 8) {
                g.centeredText(font, String.valueOf(i + 1), (left + right) / 2, (top + bottom) / 2 - 4,
                    i < current ? 0xFFAAAAAA : 0xFFFFFFFF);
            }
        }

        if (dragQueues != null && boxStart != null && boxEnd != null) {
            renderBox(g, min);
        }

        ChunkPos hovered = menu.isRunning() || dragQueues != null ? null : chunkAt(mouseX, mouseY);
        if (hovered != null && snapshot.scanState(hovered) == MinimapSnapshot.SCANNED) {
            int hx = hovered.x() - min.x();
            int hz = hovered.z() - min.z();
            g.fill(screenX(hx * 16), screenY(hz * 16), screenX((hx + 1) * 16), screenY((hz + 1) * 16), HOVER);
        }

        if (padPos != null) {
            marker(g, padPos, PAD_MARKER);
        }
        marker(g, menu.getBlockEntity().getBlockPos(), CONTROLLER_MARKER);

        g.disableScissor();
    }

    // region Orbit planner drawing

    private void renderPlanner(GuiGraphicsExtractor g, int mouseX, int mouseY, ChunkPos min) {
        if (padPos == null) {
            return;
        }
        long now = System.currentTimeMillis();
        float t = (now % 100_000) / 1000f;
        int range = menu.getTransferRange();
        int r = footprintRadius();
        ChunkPos pad = ChunkPos.containing(padPos);

        // where the area's middle may go
        int[] reach = rect(min, pad.x() - range, pad.z() - range, pad.x() + range, pad.z() + range);
        dashedRect(g, reach, REACH_EDGE, t * 8, 4);

        // where it is now (gliding along while it flies over)
        int state = menu.getTransferState();
        float progress = menu.getTransferProgress();
        double curDx = menu.getOrbitDx();
        double curDz = menu.getOrbitDz();
        if (state == 2) {
            float s = progress * progress * (3 - 2 * progress);
            curDx = Mth.lerp(s, fromDx, menu.getOrbitDx());
            curDz = Mth.lerp(s, fromDz, menu.getOrbitDz());
        }
        double[] centre = chunkCentreScreen(min, pad.x() + curDx, pad.z() + curDz);
        int[] area = rect(min, pad.x() + curDx - r, pad.z() + curDz - r, pad.x() + curDx + r, pad.z() + curDz + r);
        g.fill(area[0], area[1], area[2], area[3], ORBIT_AREA_FILL);
        g.outline(area[0], area[1], area[2] - area[0], area[3] - area[1], ORBIT_AREA_EDGE);
        g.outline(area[0] + 1, area[1] + 1, area[2] - area[0] - 2, area[3] - area[1] - 2, (ORBIT_AREA_EDGE & 0xFFFFFF) | 0x60000000);

        // tether from the pad
        double[] padScreen = chunkCentreScreen(min, pad.x(), pad.z());
        dottedLine(g, padScreen[0], padScreen[1], centre[0], centre[1], TETHER, 0, 3);

        // the flight: path, target and the satellite on its way
        if (state != 0) {
            int tdx = menu.getTransferDx();
            int tdz = menu.getTransferDz();
            double[] target = chunkCentreScreen(min, pad.x() + tdx, pad.z() + tdz);
            double[] start = chunkCentreScreen(min, pad.x() + (state == 2 ? fromDx : menu.getOrbitDx()), pad.z() + (state == 2 ? fromDz : menu.getOrbitDz()));
            dottedLine(g, start[0], start[1], target[0], target[1], TARGET_EDGE, t * 12, 4);
            int[] goal = rect(min, pad.x() + tdx - r, pad.z() + tdz - r, pad.x() + tdx + r, pad.z() + tdz + r);
            dashedRect(g, goal, TARGET_EDGE, -t * 10, 3);
            if (state == 1) {
                // the rocket brings fuel: rings pulse around the satellite
                for (int i = 0; i < 2; i++) {
                    float ring = ((t * 1.2f + i * 0.5f) % 1f);
                    int radius = 3 + (int) (ring * 9);
                    int alpha = (int) ((1 - ring) * 200);
                    ring(g, (int) centre[0], (int) centre[1], radius, (alpha << 24) | 0xFFC23A);
                }
            } else {
                // sparkly trail behind it
                for (int i = 1; i <= 6; i++) {
                    float back = Math.max(0, progress - i * 0.03f);
                    float s = back * back * (3 - 2 * back);
                    double[] p = chunkCentreScreen(min, pad.x() + Mth.lerp(s, fromDx, tdx), pad.z() + Mth.lerp(s, fromDz, tdz));
                    int alpha = 200 - i * 30;
                    g.fill((int) p[0], (int) p[1], (int) p[0] + 1, (int) p[1] + 1, (alpha << 24) | 0xFFFFFF);
                }
            }
        }

        // what a click would do
        ChunkPos hovered = isMoving() ? null : offsetAt(mouseX, mouseY);
        if (hovered != null) {
            boolean ok = canReach(hovered.x(), hovered.z());
            int[] ghost = rect(min, pad.x() + hovered.x() - r, pad.z() + hovered.z() - r, pad.x() + hovered.x() + r, pad.z() + hovered.z() + r);
            dashedRect(g, ghost, ok ? GHOST_EDGE : GHOST_BAD, t * 6, 2);
            double[] c = chunkCentreScreen(min, pad.x() + hovered.x(), pad.z() + hovered.z());
            crosshair(g, (int) c[0], (int) c[1], ok ? GHOST_EDGE : GHOST_BAD);
        }

        // the picked target, pulsing
        if (planned != null && state == 0) {
            float pulse = 0.5f + 0.5f * Mth.sin(t * 6);
            int edge = ((int) (0x90 + 0x6F * pulse) << 24) | (TARGET_EDGE & 0xFFFFFF);
            int[] goal = rect(min, pad.x() + planned.x() - r, pad.z() + planned.z() - r, pad.x() + planned.x() + r, pad.z() + planned.z() + r);
            g.fill(goal[0], goal[1], goal[2], goal[3], 0x18FFC23A);
            g.outline(goal[0], goal[1], goal[2] - goal[0], goal[3] - goal[1], edge);
            double[] c = chunkCentreScreen(min, pad.x() + planned.x(), pad.z() + planned.z());
            dottedLine(g, centre[0], centre[1], c[0], c[1], TARGET_EDGE, t * 12, 4);
            crosshair(g, (int) c[0], (int) c[1], TARGET_EDGE);
        }

        satelliteGlyph(g, (int) centre[0], (int) centre[1], t, state == 2);

        // arrival: white flash and a ring going out
        if (arrivalFlashStart >= 0) {
            float age = (now - arrivalFlashStart) / (float) FLASH_MS;
            if (age >= 1) {
                arrivalFlashStart = -1;
            } else {
                int alpha = (int) ((1 - age) * 140);
                g.fill(area[0], area[1], area[2], area[3], (alpha << 24) | 0xFFFFFF);
                ring(g, (int) centre[0], (int) centre[1], 4 + (int) (age * 40), ((int) ((1 - age) * 255) << 24) | 0x9FE8F0);
            }
        }
    }

    // screen rectangle of the chunks from (x1, z1) to (x2, z2), inclusive, fractions allowed
    private int[] rect(ChunkPos min, double x1, double z1, double x2, double z2) {
        return new int[] { screenX((x1 - min.x()) * 16), screenY((z1 - min.z()) * 16), screenX((x2 - min.x() + 1) * 16), screenY((z2 - min.z() + 1) * 16) };
    }

    private double[] chunkCentreScreen(ChunkPos min, double x, double z) {
        return new double[] { screenX((x - min.x()) * 16 + 8), screenY((z - min.z()) * 16 + 8) };
    }

    // marching ants: dash of `dash` px, moving with offset
    // marching ants; one fill per dash, only the part inside the map
    private void dashedRect(GuiGraphicsExtractor g, int[] r, int color, float offset, int dash) {
        int left = r[0];
        int top = r[1];
        int right = r[2] - 1;
        int bottom = r[3] - 1;
        int period = dash * 2;
        int o = Math.floorMod((int) offset, period);
        int minX = leftPos + MAP_X;
        int minY = topPos + MAP_Y;
        int maxX = minX + MAP_SIZE - 1;
        int maxY = minY + MAP_SIZE - 1;
        // top and right run forwards, bottom and left backwards, so the dashes travel around the rectangle
        dashes(left, right, minX, maxX, x -> Math.floorMod(x - left + o, period) < dash, (a, b) -> g.fill(a, top, b + 1, top + 1, color),
            top >= minY && top <= maxY);
        dashes(left, right, minX, maxX, x -> Math.floorMod(right - x + o, period) < dash,
            (a, b) -> g.fill(a, bottom, b + 1, bottom + 1, color), bottom >= minY && bottom <= maxY);
        dashes(top, bottom, minY, maxY, y -> Math.floorMod(bottom - y + o, period) < dash,
            (a, b) -> g.fill(left, a, left + 1, b + 1, color), left >= minX && left <= maxX);
        dashes(top, bottom, minY, maxY, y -> Math.floorMod(y - top + o, period) < dash,
            (a, b) -> g.fill(right, a, right + 1, b + 1, color), right >= minX && right <= maxX);
    }

    // runs of "on" positions between from and to, clipped to clipFrom .. clipTo, each drawn once
    private static void dashes(int from, int to, int clipFrom, int clipTo, java.util.function.IntPredicate on,
                               java.util.function.BiConsumer<Integer, Integer> draw, boolean visible) {
        if (!visible) {
            return;
        }
        int start = Integer.MIN_VALUE;
        int end = Math.min(to, clipTo);
        for (int i = Math.max(from, clipFrom); i <= end; i++) {
            boolean lit = on.test(i);
            if (lit && start == Integer.MIN_VALUE) {
                start = i;
            } else if (!lit && start != Integer.MIN_VALUE) {
                draw.accept(start, i - 1);
                start = Integer.MIN_VALUE;
            }
        }
        if (start != Integer.MIN_VALUE) {
            draw.accept(start, end);
        }
    }

    private static void dottedLine(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2, int color, float offset, int spacing) {
        double length = Math.hypot(x2 - x1, y2 - y1);
        if (length < 1) {
            return;
        }
        double shift = Math.floorMod((int) offset, spacing);
        for (double d = shift; d <= length; d += spacing) {
            int x = (int) Math.round(x1 + (x2 - x1) * d / length);
            int y = (int) Math.round(y1 + (y2 - y1) * d / length);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void crosshair(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x - 3, y, x - 1, y + 1, color);
        g.fill(x + 2, y, x + 4, y + 1, color);
        g.fill(x, y - 3, x + 1, y - 1, color);
        g.fill(x, y + 2, x + 1, y + 4, color);
    }

    private static void ring(GuiGraphicsExtractor g, int cx, int cy, int radius, int color) {
        int steps = Math.max(12, radius * 6);
        for (int i = 0; i < steps; i++) {
            double a = i * Math.PI * 2 / steps;
            int x = cx + (int) Math.round(Math.cos(a) * radius);
            int y = cy + (int) Math.round(Math.sin(a) * radius);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    // tiny satellite: body, two solar wings, blinking light; bobs a little
    private static void satelliteGlyph(GuiGraphicsExtractor g, int x, int y, float t, boolean moving) {
        int bob = Math.round(Mth.sin(t * 2.5f));
        y += bob;
        g.fill(x - 7, y - 2, x + 8, y + 3, 0xC0000000);
        // wings
        g.fill(x - 7, y - 1, x - 2, y + 2, 0xFF2F5FA8);
        g.fill(x + 3, y - 1, x + 8, y + 2, 0xFF2F5FA8);
        g.fill(x - 6, y, x - 3, y + 1, 0xFF6FA8FF);
        g.fill(x + 4, y, x + 7, y + 1, 0xFF6FA8FF);
        // body
        g.fill(x - 2, y - 2, x + 3, y + 3, 0xFFC8C8D0);
        g.fill(x - 1, y - 1, x + 2, y + 2, 0xFFE8D27A);
        // blinking beacon
        if ((int) (t * 3) % 3 == 0) {
            g.fill(x, y - 3, x + 1, y - 2, 0xFFFF4040);
        }
        if (moving) {
            // thruster puff behind it
            g.fill(x - 1, y + 3, x + 2, y + 4, 0xA0FFFFFF);
            g.fill(x, y + 4, x + 1, y + 6, 0x60FFFFFF);
        }
    }

    // endregion

    // hatching over unmapped chunks, the one being scanned in green
    // box select preview: outline + count, red when it would go over the queue limit
    private void renderBox(GuiGraphicsExtractor g, ChunkPos min) {
        int left = screenX((Math.min(boxStart.x(), boxEnd.x()) - min.x()) * 16);
        int top = screenY((Math.min(boxStart.z(), boxEnd.z()) - min.z()) * 16);
        int right = screenX((Math.max(boxStart.x(), boxEnd.x()) - min.x() + 1) * 16);
        int bottom = screenY((Math.max(boxStart.z(), boxEnd.z()) - min.z() + 1) * 16);
        boolean tooMany = dragQueues && boxTotal() > menu.getMaxChunks();
        int edge = tooMany ? BOX_TOO_MANY : dragQueues ? BOX_ADD : BOX_REMOVE;
        g.fill(left, top, right, bottom, (edge & 0xFFFFFF) | 0x30000000);
        g.outline(left, top, right - left, bottom - top, edge);
        String label = dragQueues ? boxTotal() + " / " + menu.getMaxChunks() : "-" + boxCount();
        int width = font.width(label);
        int x = Mth.clamp((left + right - width) / 2, leftPos + MAP_X + 2, leftPos + MAP_X + MAP_SIZE - width - 2);
        int y = Mth.clamp((top + bottom) / 2 - 4, topPos + MAP_Y + 2, topPos + MAP_Y + MAP_SIZE - 10);
        g.fill(x - 2, y - 2, x + width + 1, y + 9, 0xB0000000);
        g.text(font, label, x, y, edge, false);
    }

    // The hatch over chunks without data is baked into a texture, per-pixel fills are slow
    private static final Identifier NO_DATA_TEXTURE = EnderLaserQuarry.rl("dynamic/quarry_minimap_no_data");
    @Nullable
    private DynamicTexture noDataTexture;
    @Nullable
    private MinimapSnapshot noDataSnapshot;
    private String noDataView = "";

    private void renderScanCoverage(GuiGraphicsExtractor g, int chunks, ChunkPos min) {
        int x0 = leftPos + MAP_X;
        int y0 = topPos + MAP_Y;
        String view = screenX(0) + "," + screenY(0) + "," + screenX(16) + "," + screenY(16);
        if (noDataTexture == null || noDataSnapshot != snapshot || !view.equals(noDataView)) {
            bakeNoData(chunks, min, x0, y0);
            noDataSnapshot = snapshot;
            noDataView = view;
        }
        g.blit(RenderPipelines.GUI_TEXTURED, NO_DATA_TEXTURE, x0, y0, 0, 0, MAP_SIZE, MAP_SIZE, MAP_SIZE, MAP_SIZE, MAP_SIZE, MAP_SIZE);

        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 200.0);
        for (int cz = 0; cz < chunks; cz++) {
            for (int cx = 0; cx < chunks; cx++) {
                if (snapshot.scanState(new ChunkPos(min.x() + cx, min.z() + cz)) != MinimapSnapshot.SCANNING) {
                    continue;
                }
                int left = screenX(cx * 16);
                int top = screenY(cz * 16);
                int right = screenX((cx + 1) * 16);
                int bottom = screenY((cz + 1) * 16);
                if (right <= x0 || bottom <= y0 || left >= x0 + MAP_SIZE || top >= y0 + MAP_SIZE) {
                    continue;
                }
                int alpha = (int) (0x40 + 0x60 * pulse) << 24;
                g.fill(left, top, right, bottom, alpha | (SCANNING_FILL & 0xFFFFFF));
                g.outline(left, top, right - left, bottom - top, SCANNING_EDGE);
            }
        }
    }

    // one map-sized picture (1 texel = 1 GUI pixel) of the dark fill + diagonal lines over every chunk without data
    private void bakeNoData(int chunks, ChunkPos min, int x0, int y0) {
        if (noDataTexture == null) {
            noDataTexture = new DynamicTexture("ender_laser_quarry_minimap_no_data", MAP_SIZE, MAP_SIZE, true);
            minecraft.getTextureManager().register(NO_DATA_TEXTURE, noDataTexture);
        }
        NativeImage pixels = noDataTexture.getPixels();
        pixels.fillRect(0, 0, MAP_SIZE, MAP_SIZE, 0);
        int line = over(NO_DATA_LINE, NO_DATA);
        for (int cz = 0; cz < chunks; cz++) {
            for (int cx = 0; cx < chunks; cx++) {
                if (snapshot.scanState(new ChunkPos(min.x() + cx, min.z() + cz)) == MinimapSnapshot.SCANNED) {
                    continue;
                }
                int left = screenX(cx * 16);
                int top = screenY(cz * 16);
                int right = Math.min(screenX((cx + 1) * 16), x0 + MAP_SIZE);
                int bottom = Math.min(screenY((cz + 1) * 16), y0 + MAP_SIZE);
                for (int y = Math.max(top, y0); y < bottom; y++) {
                    for (int x = Math.max(left, x0); x < right; x++) {
                        // a diagonal line every 4 px: x + y = 2 (mod 4) from the chunk's corner
                        boolean onLine = ((x - left) + (y - top) + 1) % 4 == 2;
                        pixels.setPixel(x - x0, y - y0, onLine ? line : NO_DATA);
                    }
                }
            }
        }
        noDataTexture.upload();
    }

    // ARGB "a over b"
    private static int over(int a, int b) {
        float aa = (a >>> 24) / 255f;
        float ba = (b >>> 24) / 255f;
        float out = aa + ba * (1 - aa);
        int r = Math.round((((a >> 16) & 255) * aa + ((b >> 16) & 255) * ba * (1 - aa)) / out);
        int gr = Math.round((((a >> 8) & 255) * aa + ((b >> 8) & 255) * ba * (1 - aa)) / out);
        int bl = Math.round(((a & 255) * aa + (b & 255) * ba * (1 - aa)) / out);
        return Math.round(out * 255) << 24 | r << 16 | gr << 8 | bl;
    }

    private void marker(GuiGraphicsExtractor g, BlockPos pos, int color) {
        if (snapshot == null) {
            return;
        }

        int size = snapshot.sizeInBlocks();
        var min = snapshot.minChunk();
        int bx = pos.getX() - min.getMinBlockX();
        int bz = pos.getZ() - min.getMinBlockZ();
        if (bx < 0 || bz < 0 || bx >= size || bz >= size) {
            return;
        }

        int px = screenX(bx + 0.5);
        int py = screenY(bz + 0.5);
        g.fill(px - 2, py - 2, px + 2, py + 2, 0xFF000000);
        g.fill(px - 1, py - 1, px + 1, py + 1, color);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);

        if (menu.isVoidMode()) {
            renderSpaceLabels(g);
            return;
        }
        if (planning && padPos != null) {
            renderPlannerLabels(g);
            return;
        }

        // status strip at the bottom
        List<ChunkPos> queue = menu.getQueue();
        int stripY = MAP_Y + MAP_SIZE - 11;
        int stripTop = queue.isEmpty() ? stripY : stripY - 10;
        g.fill(MAP_X, stripTop, MAP_X + MAP_SIZE, MAP_Y + MAP_SIZE, 0xB0000000);

        if (!queue.isEmpty()) {
            int shown = Math.min(menu.getChunkIndex() + 1, queue.size());
            var line = Component.translatable("gui.ender_laser_quarry.controller.queue_line", shown, queue.size(), menu.getBlocksPerTick());
            g.text(font, font.plainSubstrByWidth(line.getString(), MAP_SIZE - 4), MAP_X + 2, stripTop + 2, 0xFF9FE8F0, false);
        }

        Component status;
        ChunkPos target = menu.getTargetChunk();
        int statusColor = 0xFFFFFFFF;
        if (menu.getMachineStates().contains(QuarryStates.CRYSTAL_TIER_TOO_LOW)) {
            status = Component.translatable("gui.ender_laser_quarry.controller.crystal_too_weak");
            statusColor = 0xFFFF5555;
        } else if (padPos == null && snapshot != null) {
            status = Component.translatable("gui.ender_laser_quarry.controller.link_hint");
        } else if (snapshot != null && !hasScannedChunk()) {
            status = Component.translatable("gui.ender_laser_quarry.controller.scan_hint");
        } else if (queue.isEmpty()) {
            status = Component.translatable("gui.ender_laser_quarry.controller.pick_chunk");
        } else if (target == null) {
            status = Component.translatable("gui.ender_laser_quarry.status.finished");
        } else if (menu.isRunning()) {
            int y = menu.getCursorY();
            status = y == Integer.MAX_VALUE
                ? Component.translatable("gui.ender_laser_quarry.controller.locking_on", target.x(), target.z())
                : Component.translatable(menu.isBuildingWall() ? "gui.ender_laser_quarry.controller.walling" : "gui.ender_laser_quarry.controller.mining",
                    target.x(), target.z(), y, compact(menu.getBlocksMined()));
        } else {
            status = Component.translatable("gui.ender_laser_quarry.controller.selected", queue.size(), compact(menu.getBlocksMined()));
        }
        g.text(font, font.plainSubstrByWidth(status.getString(), MAP_SIZE - 4), MAP_X + 2, stripY + 2, statusColor, false);

        if (isMapInteractive()) {
            renderZoomLabel(g, zoom);
        }
    }

    // two lines: where the satellite is, then what's going on
    private void renderPlannerLabels(GuiGraphicsExtractor g) {
        int stripY = MAP_Y + MAP_SIZE - 11;
        int stripTop = stripY - 10;
        g.fill(MAP_X, stripTop, MAP_X + MAP_SIZE, MAP_Y + MAP_SIZE, 0xB0000000);

        int state = menu.getTransferState();
        Component where = Component.translatable("gui.ender_laser_quarry.orbit.over", coords(menu.getOrbitDx(), menu.getOrbitDz()));
        g.text(font, font.plainSubstrByWidth(where.getString(), MAP_SIZE - 4), MAP_X + 2, stripTop + 2, 0xFF9FE8F0, false);

        Component status;
        int color = 0xFFFFFFFF;
        int percent = Math.round(menu.getTransferProgress() * 100);
        if (state == 1) {
            status = Component.translatable("gui.ender_laser_quarry.orbit.fuelling", percent);
            color = 0xFFFFC23A;
        } else if (state == 2) {
            status = Component.translatable("gui.ender_laser_quarry.orbit.moving", percent);
            color = 0xFFFFC23A;
        } else if (!menu.hasSatelliteInOrbit()) {
            status = Component.translatable("gui.ender_laser_quarry.orbit.no_satellite");
        } else if (planned != null) {
            int cost = transferCost(planned.x(), planned.z());
            boolean enough = menu.getPadFuel() >= cost;
            status = Component.translatable("gui.ender_laser_quarry.orbit.target", coords(planned.x(), planned.z()), FUEL_FORMAT.format(cost));
            color = enough ? 0xFFFFC23A : 0xFFFF6A6A;
        } else {
            status = Component.translatable("gui.ender_laser_quarry.orbit.hint");
        }
        g.text(font, font.plainSubstrByWidth(status.getString(), MAP_SIZE - 4), MAP_X + 2, stripY + 2, color, false);

        if (isMapInteractive()) {
            renderZoomLabel(g, zoom);
        }
    }

    private static final java.text.NumberFormat FUEL_FORMAT = java.text.NumberFormat.getIntegerInstance(Locale.ROOT);

    // 99999, 123k, 1.2M
    private static String compact(long count) {
        if (count < 100_000) {
            return String.valueOf(count);
        }
        return count < 1_000_000 ? count / 1000 + "k" : String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
    }

    private void renderZoomLabel(GuiGraphicsExtractor g, double zoom) {
        if (zoom > 1.001) {
            String label = String.format(Locale.ROOT, "%.1fx", zoom);
            int labelX = MAP_X + MAP_SIZE - font.width(label) - 2;
            g.fill(labelX - 2, MAP_Y, MAP_X + MAP_SIZE, MAP_Y + 11, 0xB0000000);
            g.text(font, label, labelX, MAP_Y + 2, 0xFF9FE8F0, false);
        }
    }

    private void renderSpaceLabels(GuiGraphicsExtractor g) {
        List<Integer> queue = menu.getAsteroidQueue();
        int stripY = MAP_Y + MAP_SIZE - 11;
        int stripTop = queue.isEmpty() ? stripY : stripY - 10;
        g.fill(MAP_X, stripTop, MAP_X + MAP_SIZE, MAP_Y + MAP_SIZE, 0xB0000000);

        SpaceMapPayload.AsteroidInfo mining = null;
        if (!queue.isEmpty()) {
            for (var asteroid : spaceView.asteroids()) {
                if (asteroid.id() == queue.get(0)) {
                    mining = asteroid;
                }
            }
            var line = Component.translatable("gui.ender_laser_quarry.controller.asteroid_line", queue.size(), menu.getBlocksPerTick());
            g.text(font, font.plainSubstrByWidth(line.getString(), MAP_SIZE - 4), MAP_X + 2, stripTop + 2, 0xFF9FE8F0, false);
        }

        float scan = menu.getSpaceScanProgress();
        Component status;
        if (padPos == null) {
            status = Component.translatable("gui.ender_laser_quarry.controller.link_hint");
        } else if (menu.getAnalyzeId() >= 0) {
            status = menu.isAnalyzeStalled()
                ? Component.translatable("gui.ender_laser_quarry.controller.analyze_waiting")
                : Component.translatable("gui.ender_laser_quarry.controller.analyzing", Math.round(menu.getAnalyzeProgress() * 100));
        } else if (scan >= 0) {
            status = menu.isScanStalled()
                ? Component.translatable(padPowerShort(menu.getScanEnergy() / menu.getScanTicksTotal())
                    ? "gui.ender_laser_quarry.controller.space_scan_waiting_power"
                    : "gui.ender_laser_quarry.controller.space_scan_waiting")
                : Component.translatable("gui.ender_laser_quarry.controller.space_scan", Math.round(scan * 100));
        } else if (spaceView.asteroids().isEmpty()) {
            status = Component.translatable("gui.ender_laser_quarry.controller.space_scan_hint");
        } else if (queue.isEmpty()) {
            status = Component.translatable("gui.ender_laser_quarry.controller.pick_asteroid");
        } else if (menu.isRunning() && mining != null) {
            status = Component.translatable("gui.ender_laser_quarry.controller.mining_asteroid",
                Math.round(100.0 * mining.remaining() / Math.max(1, mining.total())), compact(menu.getBlocksMined()));
        } else {
            status = Component.translatable("gui.ender_laser_quarry.controller.asteroids_selected", queue.size());
        }
        g.text(font, font.plainSubstrByWidth(status.getString(), MAP_SIZE - 4), MAP_X + 2, stripY + 2, 0xFFFFFFFF, false);

        if (isSpaceInteractive()) {
            renderZoomLabel(g, spaceView.zoom());
        }
    }

    private boolean hasScannedChunk() {
        for (byte state : snapshot.scan()) {
            if (state == MinimapSnapshot.SCANNED) {
                return true;
            }
        }
        return false;
    }

    // room on the left for the icon
    private static Component plannerLabel(boolean planning) {
        return Component.literal("   ").append(Component.translatable(planning ? "gui.ender_laser_quarry.orbit.back" : "gui.ender_laser_quarry.orbit.button"));
    }

    // Orbit button: the satellite that's up there (bobbing a little), back button: a map
    private void renderPlannerIcon(GuiGraphicsExtractor g) {
        if (plannerButton == null || !plannerButton.visible) {
            return;
        }
        ItemStack icon;
        float bob = 0;
        if (planning) {
            icon = new ItemStack(Items.FILLED_MAP);
        } else {
            QuarryTier tier = menu.getBlockEntity().getTier();
            BlockPos linked = menu.getBlockEntity().getPadPos();
            if (linked != null && minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(linked) instanceof LaunchPadBlockEntity pad && pad.hasSatelliteInOrbit()) {
                tier = pad.getDrawnSatelliteTier();
            }
            icon = new ItemStack(ModItems.satellite(tier).get());
            if (plannerButton.active && minecraft != null && minecraft.level != null) {
                bob = Mth.sin((minecraft.level.getGameTime() % 80) / 80f * Mth.TWO_PI) * 0.6f;
            }
        }
        g.pose().pushMatrix();
        g.pose().translate(plannerButton.getX() + 3, plannerButton.getY() + 1 + bob);
        g.pose().scale(PLANNER_ICON_SCALE, PLANNER_ICON_SCALE);
        g.item(icon, 0, 0);
        g.pose().popMatrix();
    }

    // Dew of the Void in the Void Satellite: a 4th tank beside energy, coolant and XP
    private static final java.util.function.Supplier<net.minecraft.world.level.material.Fluid> DEW_FLUID = com.google.common.base.Suppliers.memoize(
        () -> net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(
            Identifier.fromNamespaceAndPath("enderio", "fluid_dew_of_the_void_still")));

    private boolean showsDew() {
        return menu.isVoidMode() && menu.getSatelliteDew() >= 0;
    }

    private com.enderio.enderio.foundation.fluid.FluidStorageInfo dewInfo() {
        int dew = Math.max(0, menu.getSatelliteDew());
        return new com.enderio.enderio.foundation.fluid.FluidStorageInfo(
            dew > 0 ? new net.neoforged.neoforge.fluids.FluidStack(DEW_FLUID.get(), dew) : net.neoforged.neoforge.fluids.FluidStack.EMPTY,
            Math.max(1, menu.getDewCapacity()));
    }

    // one tick mark per scan on the tank's left edge, over the fluid
    private void renderDewNotches(GuiGraphicsExtractor g) {
        int scans = menu.getDewCapacity() / Math.max(1, menu.getScanDew());
        if (scans < 2 || scans > 24) {
            return;
        }
        int x = leftPos + QuarryControllerMenu.DEW_X;
        int bottom = topPos + QuarryControllerMenu.COOLANT_Y + QuarryControllerMenu.COOLANT_HEIGHT;
        for (int i = 1; i < scans; i++) {
            int y = bottom - Math.round(QuarryControllerMenu.COOLANT_HEIGHT * i / (float) scans);
            g.fill(x, y, x + 3, y + 1, 0xC0000000);
        }
    }

    private boolean isOverDewGauge(int mouseX, int mouseY) {
        int x = leftPos + QuarryControllerMenu.DEW_X;
        int y = topPos + QuarryControllerMenu.COOLANT_Y;
        return showsDew() && mouseX >= x - 1 && mouseX < x + 10 && mouseY >= y - 1 && mouseY < y + QuarryControllerMenu.COOLANT_HEIGHT + 1;
    }

    private List<Component> dewLines() {
        List<Component> lines = new ArrayList<>();
        int dew = Math.max(0, menu.getSatelliteDew());
        lines.add(Component.translatable("gui.ender_laser_quarry.controller.dew", formatMb(dew), formatMb(menu.getDewCapacity())));
        lines.add(Component.translatable("gui.ender_laser_quarry.controller.dew_per_scan", formatMb(menu.getScanDew()))
            .withStyle(ChatFormatting.GRAY));
        if (dew < menu.getScanDew()) {
            lines.add(QuarryStates.SATELLITE_NO_DEW.component().copy().withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    // where scans and Analyze take their power from: the linked launch pad, topped up over the antenna
    private List<Component> padPowerLines() {
        List<Component> lines = new ArrayList<>();
        int stored = menu.getPadEnergy();
        if (stored < 0) {
            lines.add(Component.translatable("gui.ender_laser_quarry.controller.no_pad_power").withStyle(ChatFormatting.RED));
            return lines;
        }
        lines.add(Component.translatable("gui.ender_laser_quarry.controller.pad_power", formatMb(stored), formatMb(menu.getPadCapacity()))
            .withStyle(stored > 0 ? ChatFormatting.AQUA : ChatFormatting.RED));
        lines.add(Component.translatable("gui.ender_laser_quarry.controller.pad_power_hint").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    // a scan / analysis that waits: say so when it's the launch pad's power
    private boolean padPowerShort(int perTick) {
        return menu.getPadEnergy() >= 0 && menu.getPadEnergy() < perTick;
    }

    private static String formatMb(int mb) {
        return java.text.NumberFormat.getIntegerInstance(java.util.Locale.ROOT).format(mb);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        renderPlannerIcon(g);
        if (showsDew()) {
            renderDewNotches(g);
        }

        if (menu.isVoidMode() && isOverDewGauge(mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, dewLines(), mouseX, mouseY);
            return;
        }
        if (menu.isVoidMode() && refreshButton != null && refreshButton.isMouseOver(mouseX, mouseY)) {
            List<Component> lines = dewLines();
            lines.add(Component.translatable("gui.ender_laser_quarry.controller.scan_cost", formatMb(menu.getScanEnergy()),
                menu.getScanTicksTotal() / 20, formatMb(menu.getScanEnergy() / menu.getScanTicksTotal())).withStyle(ChatFormatting.DARK_AQUA));
            lines.addAll(padPowerLines());
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        if (menu.isVoidMode() && analyzeButton != null && analyzeButton.visible && analyzeButton.isMouseOver(mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.ender_laser_quarry.controller.analyze_cost", formatMb(menu.getAnalyzeEnergy()),
                Math.max(1, menu.getAnalyzeTicks() / 20)).withStyle(ChatFormatting.DARK_AQUA));
            lines.addAll(padPowerLines());
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }

        // tooltip for the empty filter slot
        int fx = leftPos + QuarryControllerMenu.FILTER_X;
        int fy = topPos + QuarryControllerMenu.FILTER_Y;
        if (mouseX >= fx && mouseX < fx + 16 && mouseY >= fy && mouseY < fy + 16 && menu.getCarried().isEmpty()
            && menu.getMachineInventory().getStack(QuarryControllerBlockEntity.FILTER).isEmpty()) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.ender_laser_quarry.controller.void_filter"),
                Component.translatable("gui.ender_laser_quarry.controller.void_filter.hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }

        if (depthButton != null && (depthButton.isHovered() || isOverFloorControl(mouseX, mouseY))) {
            DepthMode mode = menu.getDepthMode();
            int floorY = shownFloorY();
            boolean ender = menu.getBlockEntity().getTier() == QuarryTier.ENDER;
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.ender_laser_quarry.depth", mode.displayName(floorY)));
            lines.add(mode.description(floorY, ender).copy().withStyle(ChatFormatting.GRAY));
            if (menu.getBlockEntity().getTier() == QuarryTier.BASIC) {
                lines.add(Component.translatable("gui.ender_laser_quarry.depth.needs_tier").withStyle(ChatFormatting.RED));
            } else if (menu.isRunning()) {
                lines.add(Component.translatable("gui.ender_laser_quarry.depth.stop_first").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                lines.add(Component.translatable("gui.ender_laser_quarry.depth.click", mode.next().displayName(floorY))
                    .withStyle(ChatFormatting.DARK_GRAY));
                if (showsFloorControl()) {
                    lines.add(Component.translatable("gui.ender_laser_quarry.depth.floor_hint").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }

        SpaceMapPayload.AsteroidInfo asteroid = asteroidAt(mouseX, mouseY);
        if (asteroid != null && menu.getCarried().isEmpty()) {
            List<Component> lines = new ArrayList<>(SpaceMapView.tooltip(asteroid));
            int queued = menu.getAsteroidQueue().indexOf(asteroid.id());
            if (queued >= 0) {
                lines.add(Component.translatable("gui.ender_laser_quarry.controller.queued", queued + 1).withStyle(ChatFormatting.AQUA));
            }
            if (asteroid.id() == menu.getAnalyzeId()) {
                lines.add(Component.translatable(menu.isAnalyzeStalled() ? "gui.ender_laser_quarry.controller.analyze_waiting"
                    : "gui.ender_laser_quarry.controller.analyzing", Math.round(menu.getAnalyzeProgress() * 100)).withStyle(ChatFormatting.AQUA));
            } else if (!asteroid.analyzed()) {
                lines.add(Component.translatable("gui.ender_laser_quarry.controller.analyze_hint").withStyle(ChatFormatting.DARK_GRAY));
            } else if (!menu.isRunning()) {
                lines.add(Component.translatable("gui.ender_laser_quarry.controller.asteroid_hint").withStyle(ChatFormatting.DARK_GRAY));
            }
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }

        if (plannerButton != null && plannerButton.visible && plannerButton.isHovered()) {
            List<Component> lines = new ArrayList<>();
            if (planning) {
                lines.add(Component.translatable("gui.ender_laser_quarry.orbit.back_tooltip"));
            } else if (menu.getTransferRange() <= 0) {
                lines.add(Component.translatable("gui.ender_laser_quarry.orbit.title"));
                lines.add(Component.translatable("gui.ender_laser_quarry.orbit.what").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.ender_laser_quarry.orbit.basic").withStyle(ChatFormatting.RED));
            } else {
                lines.add(Component.translatable("gui.ender_laser_quarry.orbit.title"));
                lines.add(Component.translatable("gui.ender_laser_quarry.orbit.what").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.ender_laser_quarry.orbit.over", coords(menu.getOrbitDx(), menu.getOrbitDz()))
                    .withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable("gui.ender_laser_quarry.orbit.reach", menu.getTransferRange()).withStyle(ChatFormatting.GRAY));
            }
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        if (moveButton != null && moveButton.visible && moveButton.isHovered() && planned != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.ender_laser_quarry.orbit.launch_tooltip", coords(planned.x(), planned.z())));
            int cost = transferCost(planned.x(), planned.z());
            lines.add(Component.translatable("gui.ender_laser_quarry.orbit.fuel", FUEL_FORMAT.format(cost), FUEL_FORMAT.format(menu.getPadFuel()))
                .withStyle(menu.getPadFuel() >= cost ? ChatFormatting.GRAY : ChatFormatting.RED));
            if (!menu.isRocketReady()) {
                lines.add(Component.translatable("gui.ender_laser_quarry.orbit.needs_rocket").withStyle(ChatFormatting.RED));
            }
            if (menu.isRunning()) {
                lines.add(Component.translatable("message.ender_laser_quarry.launch.transfer_stop_quarry").withStyle(ChatFormatting.RED));
            }
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        if (planning) {
            ChunkPos offset = offsetAt(mouseX, mouseY);
            if (offset != null && menu.getCarried().isEmpty()) {
                List<Component> lines = new ArrayList<>();
                ChunkPos chunk = padOffsetChunk(offset.x(), offset.z());
                lines.add(Component.translatable("gui.ender_laser_quarry.orbit.spot", coords(offset.x(), offset.z())));
                lines.add(Component.translatable("gui.ender_laser_quarry.orbit.chunk", chunk.x(), chunk.z()).withStyle(ChatFormatting.DARK_GRAY));
                boolean here = offset.x() == menu.getOrbitDx() && offset.z() == menu.getOrbitDz();
                if (here) {
                    lines.add(Component.translatable("gui.ender_laser_quarry.orbit.here").withStyle(ChatFormatting.AQUA));
                } else if (!canReach(offset.x(), offset.z())) {
                    lines.add(Component.translatable("gui.ender_laser_quarry.orbit.too_far", menu.getTransferRange()).withStyle(ChatFormatting.RED));
                } else if (!isMoving()) {
                    int cost = transferCost(offset.x(), offset.z());
                    lines.add(Component.translatable("gui.ender_laser_quarry.orbit.distance",
                        String.format(Locale.ROOT, "%.1f", transferDistance(offset.x(), offset.z()))).withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable("gui.ender_laser_quarry.orbit.fuel", FUEL_FORMAT.format(cost), FUEL_FORMAT.format(menu.getPadFuel()))
                        .withStyle(menu.getPadFuel() >= cost ? ChatFormatting.GRAY : ChatFormatting.RED));
                    lines.add(Component.translatable("gui.ender_laser_quarry.orbit.click").withStyle(ChatFormatting.DARK_GRAY));
                }
                g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            }
            return;
        }

        ChunkPos hovered = chunkAt(mouseX, mouseY);
        if (hovered != null && menu.getCarried().isEmpty()) {
            if (snapshot.scanState(hovered) != MinimapSnapshot.SCANNED) {
                g.setTooltipForNextFrame(font, Component.translatable("gui.ender_laser_quarry.controller.chunk_unscanned", hovered.x(), hovered.z()), mouseX, mouseY);
                return;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.ender_laser_quarry.controller.chunk_tooltip",
                hovered.x(), hovered.z(), hovered.getMinBlockX(), hovered.getMinBlockZ()));
            int queued = menu.getQueue().indexOf(hovered);
            if (queued >= 0) {
                lines.add(Component.translatable(queued < menu.getChunkIndex()
                    ? "gui.ender_laser_quarry.controller.queued_done"
                    : "gui.ender_laser_quarry.controller.queued", queued + 1).withStyle(ChatFormatting.AQUA));
            }
            if (!menu.isRunning() && dragQueues == null) {
                lines.add(Component.translatable("gui.ender_laser_quarry.controller.map_hint").withStyle(ChatFormatting.DARK_GRAY));
            }
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    // endregion
}
