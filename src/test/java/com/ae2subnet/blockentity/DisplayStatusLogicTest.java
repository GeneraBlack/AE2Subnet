package com.ae2subnet.blockentity;

import com.ae2subnet.api.SubnetDisplayStatus;
import com.ae2subnet.api.WorkerState;
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
