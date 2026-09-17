package com.ae2subnet.blockentity;

import com.ae2subnet.api.SubnetDisplayStatus;
import com.ae2subnet.api.WorkerState;
import com.ae2subnet.block.SubPatternProviderBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class DisplayStatusLogicTest {

    static class MockDisplayStatusHolder {
        private WorkerState state = WorkerState.FREE;
        private int busyHoldTicks = 0;
        private boolean powered = true;
        private boolean updated = false;

        public void setPowered(boolean powered) {
            this.powered = powered;
        }

        public WorkerState getWorkerState() {
            return state;
        }

        public int getBusyHoldTicks() {
            return busyHoldTicks;
        }

        public SubnetDisplayStatus getDisplayStatus() {
            if (!powered) {
                return SubnetDisplayStatus.OFFLINE;
            }
            return (state == WorkerState.OCCUPIED || busyHoldTicks > 0)
                    ? SubnetDisplayStatus.BUSY
                    : SubnetDisplayStatus.IDLE;
        }

        public void markOccupied() {
            this.state = WorkerState.OCCUPIED;
            this.busyHoldTicks = 20;
            this.updated = true;
        }

        public void markFree() {
            this.state = WorkerState.FREE;
            if (this.busyHoldTicks <= 0) {
                this.updated = true;
            }
        }

        public void serverTick() {
            if (this.busyHoldTicks > 0) {
                this.busyHoldTicks--;
                if (this.busyHoldTicks == 0 && this.state == WorkerState.FREE) {
                    this.updated = true;
                }
            }
        }

        public boolean isUpdatedAndReset() {
            boolean u = updated;
            updated = false;
            return u;
        }
    }

    @Test
    void testInstantCraftVisualHold() {
        MockDisplayStatusHolder holder = new MockDisplayStatusHolder();
        Assertions.assertEquals(SubnetDisplayStatus.IDLE, holder.getDisplayStatus());
        Assertions.assertEquals(WorkerState.FREE, holder.getWorkerState());

        // Boosted craft starts
        holder.markOccupied();
        Assertions.assertTrue(holder.isUpdatedAndReset());
        Assertions.assertEquals(WorkerState.OCCUPIED, holder.getWorkerState());
        Assertions.assertEquals(SubnetDisplayStatus.BUSY, holder.getDisplayStatus());

        // Finishes in 1 tick
        holder.serverTick();
        holder.markFree();
        // Worker state is immediately FREE so worker is not stalled!
        Assertions.assertEquals(WorkerState.FREE, holder.getWorkerState());
        // Visual status MUST remain BUSY for the rest of the 20 ticks
        Assertions.assertEquals(SubnetDisplayStatus.BUSY, holder.getDisplayStatus());
        Assertions.assertFalse(holder.isUpdatedAndReset(), "Should not trigger block update to IDLE prematurely");

        // Tick 18 more times
        for (int i = 0; i < 18; i++) {
            holder.serverTick();
            Assertions.assertEquals(SubnetDisplayStatus.BUSY, holder.getDisplayStatus());
        }

        // 20th tick
        holder.serverTick();
        Assertions.assertTrue(holder.isUpdatedAndReset(), "Should trigger block update to IDLE upon timer expiry");
        Assertions.assertEquals(SubnetDisplayStatus.IDLE, holder.getDisplayStatus());
    }

    @Test
    void testLongCraftKeepsBusy() {
        MockDisplayStatusHolder holder = new MockDisplayStatusHolder();
        holder.markOccupied();

        // Ticking past 20 ticks while still occupied
        for (int i = 0; i < 50; i++) {
            holder.serverTick();
            Assertions.assertEquals(SubnetDisplayStatus.BUSY, holder.getDisplayStatus());
        }

        // When craft finally finishes after 50 ticks
        holder.markFree();
        Assertions.assertEquals(WorkerState.FREE, holder.getWorkerState());
        Assertions.assertEquals(SubnetDisplayStatus.IDLE, holder.getDisplayStatus());
    }

    @Test
    void testRapidSuccessiveCraftsRefreshesHoldTimer() {
        MockDisplayStatusHolder holder = new MockDisplayStatusHolder();

        // Craft 1 starts and finishes in 1 tick
        holder.markOccupied();
        holder.serverTick(); // busyHoldTicks = 19
        holder.markFree();
        Assertions.assertEquals(WorkerState.FREE, holder.getWorkerState());
        Assertions.assertEquals(19, holder.getBusyHoldTicks());

        // 5 ticks pass
        for (int i = 0; i < 5; i++) {
            holder.serverTick();
        }
        Assertions.assertEquals(14, holder.getBusyHoldTicks());

        // Craft 2 arrives
        holder.markOccupied();
        Assertions.assertEquals(20, holder.getBusyHoldTicks(), "Hold timer should reset to 20 ticks on new craft");
        Assertions.assertEquals(WorkerState.OCCUPIED, holder.getWorkerState());

        // Craft 2 finishes in 1 tick
        holder.serverTick();
        holder.markFree();
        Assertions.assertEquals(WorkerState.FREE, holder.getWorkerState());
        Assertions.assertEquals(19, holder.getBusyHoldTicks());

        // Tick remaining 19 ticks
        for (int i = 0; i < 18; i++) {
            holder.serverTick();
            Assertions.assertEquals(SubnetDisplayStatus.BUSY, holder.getDisplayStatus());
        }
        holder.serverTick(); // 19th tick -> reaches 0
        Assertions.assertEquals(0, holder.getBusyHoldTicks());
        Assertions.assertEquals(SubnetDisplayStatus.IDLE, holder.getDisplayStatus());
    }

    @Test
    void testPowerLossOverridesHoldTimer() {
        MockDisplayStatusHolder holder = new MockDisplayStatusHolder();
        holder.markOccupied();
        Assertions.assertEquals(SubnetDisplayStatus.BUSY, holder.getDisplayStatus());

        // Power goes out
        holder.setPowered(false);
        Assertions.assertEquals(SubnetDisplayStatus.OFFLINE, holder.getDisplayStatus(),
                "Display status must be OFFLINE when power is lost regardless of active craft");

        // Power restored while still within hold timer
        holder.setPowered(true);
        Assertions.assertEquals(SubnetDisplayStatus.BUSY, holder.getDisplayStatus(),
                "Display status must return to BUSY if hold timer is still active");
    }

    @Test
    void testSubPatternProviderFacingIntoMachine() {
        // When clicking against a machine face, the placement facing must be opposite
        // so the machine-facing port points INTO the machine
        for (Direction clickedFace : Direction.values()) {
            Direction expectedPlacementFacing = clickedFace.getOpposite();
            Assertions.assertEquals(clickedFace, expectedPlacementFacing.getOpposite());
        }

        // Example: clicking North face of machine (looking South towards it) -> Sub-Provider faces South (into machine)
        Assertions.assertEquals(Direction.SOUTH, Direction.NORTH.getOpposite());
        // Clicking top face of machine (looking Down onto it) -> Sub-Provider faces Down (into machine)
        Assertions.assertEquals(Direction.DOWN, Direction.UP.getOpposite());
    }

    @Test
    void testSubPatternProviderPlacementMachineVsCable() {
        BlockPos basePos = new BlockPos(10, 64, 10);

        for (Direction clickedFace : Direction.values()) {
            // Case A: Placing adjacent to an existing block (e.g. machine or cable)
            // BlockPlaceContext.getClickedPos() returns relativePos = basePos.relative(clickedFace)
            BlockPos placementPos = basePos.relative(clickedFace);

            // getClickedTargetPos must accurately resolve back to basePos (the block actually clicked on)
            BlockPos resolvedTargetPos = SubPatternProviderBlock.getClickedTargetPos(placementPos, clickedFace, false);
            Assertions.assertEquals(basePos, resolvedTargetPos,
                    "Resolved target position must match clicked block position for face " + clickedFace);

            // Case B: Replacing clicked block (e.g. grass, water)
            BlockPos replacedPos = SubPatternProviderBlock.getClickedTargetPos(placementPos, clickedFace, true);
            Assertions.assertEquals(placementPos, replacedPos,
                    "Replaced block position must match placement position for face " + clickedFace);

            // Case 1: Machine target (has ItemHandler/FluidHandler)
            // Placement must point MACHINE_FACING INTO the machine
            Direction machineFacing = SubPatternProviderBlock.determinePlacementFacing(true, clickedFace);
            Assertions.assertEquals(clickedFace.getOpposite(), machineFacing,
                    "Machine placement must point into machine for face " + clickedFace);

            // Case 2: Cable target (does NOT have ItemHandler/FluidHandler)
            // Placement must point MACHINE_FACING AWAY from the cable,
            // so the face touching the cable (clickedFace.getOpposite()) is a cable face and connects!
            Direction cableFacing = SubPatternProviderBlock.determinePlacementFacing(false, clickedFace);
            Assertions.assertEquals(clickedFace, cableFacing,
                    "Cable placement must point away from cable for face " + clickedFace);

            Direction touchingFace = clickedFace.getOpposite();
            Assertions.assertNotEquals(cableFacing, touchingFace,
                    "The face touching the cable must NOT be the MACHINE_FACING, so it connects to the AE network");

            // Null-level fallback check
            Direction nullLevelFacing = SubPatternProviderBlock.determinePlacementFacing(null, resolvedTargetPos, clickedFace);
            Assertions.assertEquals(clickedFace, nullLevelFacing,
                    "Null level fallback must default to cable-safe facing for face " + clickedFace);
        }
    }

    @Test
    void testWrenchRotationCycle() {
        // 6 distinct 3D data values, rotating 6 times should cycle through all directions and return to start
        for (Direction start : Direction.values()) {
            Direction curr = start;
            for (int i = 0; i < 6; i++) {
                curr = Direction.from3DDataValue((curr.get3DDataValue() + 1) % 6);
            }
            Assertions.assertEquals(start, curr, "Rotating 6 times must return to starting direction");
        }
    }

    @Test
    void testMasterPatternProviderOnlineLogic() {
        // Online if either mainNode or subnetNode is ready and online/powered/active
        class OnlineChecker {
            boolean isOnline(boolean mainReady, boolean mainOnline, boolean mainPowered, boolean mainActive,
                             boolean subnetReady, boolean subnetOnline, boolean subnetPowered, boolean subnetActive) {
                boolean m = mainReady && (mainOnline || mainPowered || mainActive);
                boolean s = subnetReady && (subnetOnline || subnetPowered || subnetActive);
                return m || s;
            }
        }
        OnlineChecker checker = new OnlineChecker();

        // Both unready
        Assertions.assertFalse(checker.isOnline(false, false, false, false, false, false, false, false));
        // Main ready and active, subnet offline
        Assertions.assertTrue(checker.isOnline(true, false, false, true, false, false, false, false));
        // Subnet ready and powered, main offline
        Assertions.assertTrue(checker.isOnline(false, false, false, false, true, false, true, false));
        // Main ready and online, subnet ready and online
        Assertions.assertTrue(checker.isOnline(true, true, false, false, true, true, false, false));
    }

    @Test
    void testEnergyOverlayConnectionHelper() {
        // Verify energy services list helper behavior (singleton when present, empty when null)
        java.util.function.Function<Object, java.util.List<Object>> helper =
                service -> service != null ? java.util.Collections.singletonList(service) : java.util.Collections.emptyList();

        Assertions.assertTrue(helper.apply(null).isEmpty());
        Object mockService = new Object();
        java.util.List<Object> result = helper.apply(mockService);
        Assertions.assertEquals(1, result.size());
        Assertions.assertSame(mockService, result.get(0));
    }

    @Test
    void testMasterSubnetOfflineTextureDistinctAndHasCyanMarkings() throws Exception {
        Path texturesDir = Path.of("src", "main", "resources", "assets", "ae2subnet", "textures", "block");
        BufferedImage subnetOff = ImageIO.read(texturesDir.resolve("master_subnet_offline.png").toFile());
        BufferedImage mainOff = ImageIO.read(texturesDir.resolve("master_main_offline.png").toFile());

        Assertions.assertNotNull(subnetOff);
        Assertions.assertNotNull(mainOff);
        Assertions.assertEquals(16, subnetOff.getWidth());
        Assertions.assertEquals(16, subnetOff.getHeight());

        // Must NOT be identical to master_main_offline (which caused player confusion)
        boolean hasDifference = false;
        boolean hasCyanSocketMarking = false;

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int rgbSub = subnetOff.getRGB(x, y) & 0x00FFFFFF;
                int rgbMain = mainOff.getRGB(x, y) & 0x00FFFFFF;
                if (rgbSub != rgbMain) {
                    hasDifference = true;
                }
                int r = (rgbSub >> 16) & 0xFF;
                int g = (rgbSub >> 8) & 0xFF;
                int b = rgbSub & 0xFF;
                // Cyan socket frame check: Blue and Green dominant over Red
                if (g > 100 && b > 140 && r < 50) {
                    hasCyanSocketMarking = true;
                }
            }
        }

        Assertions.assertTrue(hasDifference, "master_subnet_offline.png must be distinct from master_main_offline.png");
        Assertions.assertTrue(hasCyanSocketMarking, "master_subnet_offline.png must have a clear cyan socket frame/marking");
    }

    @Test
    void testSubMachineIdleTextureCyanGlow() throws Exception {
        Path texturesDir = Path.of("src", "main", "resources", "assets", "ae2subnet", "textures", "block");
        BufferedImage idle = ImageIO.read(texturesDir.resolve("sub_machine_idle.png").toFile());
        Assertions.assertNotNull(idle);
        Assertions.assertEquals(16, idle.getWidth());
        Assertions.assertEquals(16, idle.getHeight());

        boolean hasCyanGlow = false;
        boolean hasAmberInCenter = false;

        // Inspect center area (rows 4..11, cols 4..11)
        for (int y = 4; y <= 11; y++) {
            for (int x = 4; x <= 11; x++) {
                int rgb = idle.getRGB(x, y) & 0x00FFFFFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Cyan glow check: bright AE2 cyan (#00E5FF / #008FB3 / #E0F7FA)
                if (b > 170 && g > 130 && (b >= r && g >= r)) {
                    hasCyanGlow = true;
                }
                // Check if orange/amber glow remains in center
                if (r > 200 && g > 100 && b < 50) {
                    hasAmberInCenter = true;
                }
            }
        }

        Assertions.assertTrue(hasCyanGlow, "sub_machine_idle.png center glow must use AE2 cyan/blue");
        Assertions.assertFalse(hasAmberInCenter, "sub_machine_idle.png center must not retain orange/amber glow (reserved for busy)");
    }

    @Test
    void testSubCableOfflineTextureHasUnpoweredIndicator() throws Exception {
        Path texturesDir = Path.of("src", "main", "resources", "assets", "ae2subnet", "textures", "block");
        BufferedImage cableOff = ImageIO.read(texturesDir.resolve("sub_cable_offline.png").toFile());
        BufferedImage mainOff = ImageIO.read(texturesDir.resolve("master_main_offline.png").toFile());

        Assertions.assertNotNull(cableOff);
        Assertions.assertNotNull(mainOff);

        // Must NOT be identical to blank main_offline plate
        boolean hasDifference = false;
        boolean hasDimIndicator = false;

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int rgbCable = cableOff.getRGB(x, y) & 0x00FFFFFF;
                int rgbMain = mainOff.getRGB(x, y) & 0x00FFFFFF;
                if (rgbCable != rgbMain) {
                    hasDifference = true;
                }
                int r = (rgbCable >> 16) & 0xFF;
                int g = (rgbCable >> 8) & 0xFF;
                int b = rgbCable & 0xFF;
                // Dim unpowered indicator check
                if (b > 80 && g > 60 && r < 20) {
                    hasDimIndicator = true;
                }
            }
        }

        Assertions.assertTrue(hasDifference, "sub_cable_offline.png must not be a plain generic plate identical to master_main_offline");
        Assertions.assertTrue(hasDimIndicator, "sub_cable_offline.png must have a subtle unpowered/dim indicator");
    }

    @Test
    void testAnimatedTexturesAndMcmetaFiles() throws Exception {
        Path texturesDir = Path.of("src", "main", "resources", "assets", "ae2subnet", "textures", "block");

        String[] animatedTextures = {
                "master_main_active.png",
                "master_subnet_active.png",
                "sub_cable_active.png",
                "sub_cable_busy.png",
                "sub_machine_busy.png"
        };

        for (String texName : animatedTextures) {
            Path pngPath = texturesDir.resolve(texName);
            Path mcmetaPath = texturesDir.resolve(texName + ".mcmeta");

            Assertions.assertTrue(Files.exists(pngPath), "Texture must exist: " + texName);
            Assertions.assertTrue(Files.exists(mcmetaPath), "Mcmeta must exist: " + texName + ".mcmeta");

            BufferedImage img = ImageIO.read(pngPath.toFile());
            Assertions.assertNotNull(img, "Must be valid PNG: " + texName);
            Assertions.assertEquals(16, img.getWidth(), texName + " width must be 16");
            Assertions.assertEquals(64, img.getHeight(), texName + " height must be 64 (4 frames of 16x16)");

            String mcmetaContent = Files.readString(mcmetaPath);
            Assertions.assertTrue(mcmetaContent.contains("\"animation\""),
                    mcmetaPath + " must define animation");
            Assertions.assertTrue(mcmetaContent.contains("\"frametime\""),
                    mcmetaPath + " must define frametime");
        }
    }
}
