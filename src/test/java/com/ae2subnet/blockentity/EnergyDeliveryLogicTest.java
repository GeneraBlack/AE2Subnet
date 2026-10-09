package com.ae2subnet.blockentity;

import appeng.api.config.PowerUnit;
import com.ae2subnet.util.EnergyLogic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EnergyDeliveryLogicTest {

    private double originalRatio;

    @BeforeEach
    void setUp() {
        originalRatio = PowerUnit.FE.conversionRatio;
        // Standard AE2 default ratio (2 FE = 1 AE, so FE ratio = 0.5)
        PowerUnit.FE.conversionRatio = 0.5;
    }

    @AfterEach
    void tearDown() {
        PowerUnit.FE.conversionRatio = originalRatio;
    }

    public static int calculateMaxTransferRate(int energyCards) {
        return EnergyLogic.maxTransferRate(energyCards);
    }

    public static int calculateDeliverableFE(int energyCards, double storedAE, double maxStoredAE) {
        return EnergyLogic.deliverableFE(EnergyLogic.maxTransferRate(energyCards), storedAE, maxStoredAE);
    }

    @Test
    void testBrownoutFlagIncludesMissingStorage() {
        Assertions.assertTrue(EnergyLogic.isBrownout(0, 0), "no storage at all means nothing can be delivered");
        Assertions.assertTrue(EnergyLogic.isBrownout(19_999, 100_000));
        Assertions.assertFalse(EnergyLogic.isBrownout(20_000, 100_000));
        Assertions.assertFalse(EnergyLogic.isBrownout(100_000, 100_000));
    }

    @Test
    void testRemainingBudgetLimitsDelivery() {
        double maxStoredAE = 1_000_000.0;
        // Budget already partly used in this tick
        Assertions.assertEquals(500, EnergyLogic.deliverableFE(500, maxStoredAE, maxStoredAE));
        Assertions.assertEquals(0, EnergyLogic.deliverableFE(0, maxStoredAE, maxStoredAE));
        Assertions.assertEquals(0, EnergyLogic.deliverableFE(-10, maxStoredAE, maxStoredAE));
    }

    @Test
    void testSafeAEKeepsReserve() {
        Assertions.assertEquals(0.0, EnergyLogic.safeAE(10_000, 100_000));
        Assertions.assertEquals(5_000.0, EnergyLogic.safeAE(25_000, 100_000), 1e-9);
        Assertions.assertEquals(80_000.0, EnergyLogic.safeAE(100_000, 100_000), 1e-9);
    }

    @Test
    void testEnergyCardTransferRates() {
        Assertions.assertEquals(2_000, calculateMaxTransferRate(0));
        Assertions.assertEquals(8_000, calculateMaxTransferRate(1));
        Assertions.assertEquals(32_000, calculateMaxTransferRate(2));
        Assertions.assertEquals(128_000, calculateMaxTransferRate(3));
        Assertions.assertEquals(512_000, calculateMaxTransferRate(4));

        // Test clamping bounds
        Assertions.assertEquals(2_000, calculateMaxTransferRate(-1));
        Assertions.assertEquals(512_000, calculateMaxTransferRate(5));
        Assertions.assertEquals(512_000, calculateMaxTransferRate(99));
    }

    @Test
    void testBrownoutProtectionHaltsUnderTwentyPercent() {
        double maxStoredAE = 100_000.0; // 200,000 FE capacity
        // 19% stored power -> should halt completely (0 FE)
        double stored19 = 19_000.0;
        Assertions.assertEquals(0, calculateDeliverableFE(0, stored19, maxStoredAE));
        Assertions.assertEquals(0, calculateDeliverableFE(4, stored19, maxStoredAE));

        // 10% stored power -> should halt completely (0 FE)
        double stored10 = 10_000.0;
        Assertions.assertEquals(0, calculateDeliverableFE(0, stored10, maxStoredAE));

        // 0% stored power -> 0 FE
        Assertions.assertEquals(0, calculateDeliverableFE(0, 0, maxStoredAE));

        // Exactly 20% stored power -> safeAE is 0 -> 0 FE
        Assertions.assertEquals(0, calculateDeliverableFE(0, 20_000.0, maxStoredAE));
    }

    @Test
    void testDeliveryAboveTwentyPercent() {
        double maxStoredAE = 100_000.0; // 200,000 FE capacity

        // 25% stored power -> 5,000 AE (10,000 FE) above threshold
        double stored25 = 25_000.0;

        // 0 cards: cap is 2,000 FE/t, 10,000 FE safe available -> delivers 2,000 FE
        Assertions.assertEquals(2_000, calculateDeliverableFE(0, stored25, maxStoredAE));

        // 1 card: cap is 8,000 FE/t, 10,000 FE safe available -> delivers 8,000 FE
        Assertions.assertEquals(8_000, calculateDeliverableFE(1, stored25, maxStoredAE));

        // 2 cards: cap is 32,000 FE/t, only 10,000 FE safe available -> delivers 10,000 FE (clamped by safe buffer)
        Assertions.assertEquals(10_000, calculateDeliverableFE(2, stored25, maxStoredAE));

        // 4 cards: cap is 512,000 FE/t, only 10,000 FE safe available -> delivers 10,000 FE
        Assertions.assertEquals(10_000, calculateDeliverableFE(4, stored25, maxStoredAE));
    }

    @Test
    void testFullEnergyBufferTransferRates() {
        double maxStoredAE = 1_000_000.0; // 2,000,000 FE capacity
        double storedFull = 1_000_000.0; // 100% full

        // All card tiers can reach their maximum specified FE/t
        Assertions.assertEquals(2_000, calculateDeliverableFE(0, storedFull, maxStoredAE));
        Assertions.assertEquals(8_000, calculateDeliverableFE(1, storedFull, maxStoredAE));
        Assertions.assertEquals(32_000, calculateDeliverableFE(2, storedFull, maxStoredAE));
        Assertions.assertEquals(128_000, calculateDeliverableFE(3, storedFull, maxStoredAE));
        Assertions.assertEquals(512_000, calculateDeliverableFE(4, storedFull, maxStoredAE));
    }

    @Test
    void testPowerUnitConversion() {
        // Standard AE2 conversion: 1 AE = 2 FE
        Assertions.assertEquals(1000.0, PowerUnit.FE.convertTo(PowerUnit.AE, 2000.0));
        Assertions.assertEquals(2000.0, PowerUnit.AE.convertTo(PowerUnit.FE, 1000.0));
        Assertions.assertEquals(256_000.0, PowerUnit.FE.convertTo(PowerUnit.AE, 512_000.0));
    }
}
