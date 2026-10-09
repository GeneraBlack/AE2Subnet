# Changelog

All notable changes to the **AE2 Sub-Pattern Provider** mod will be documented in this file.

---

## [1.1.0] - 2026-10-09

### Feature Backport: Machine Energy Delivery, Capacity Expansion & GUI Overhaul (Minecraft 1.21.1)

Backported all major features from v2.1.0 to Minecraft 1.21.1 (NeoForge 21.1.x / AE2 19.2.0-beta):

* **Sub-Pattern Provider Machine Energy Delivery**:
  * Delivers Forge Energy (FE) directly to connected machines from the network (both active push on server tick and passive pull via `Capabilities.EnergyStorage.BLOCK` on the machine face).
  * **AE2 Energy Card Support**: Installing up to 4 Energy Cards scales the transfer limit from 2,000 FE/t up to 512,000 FE/t (2k / 8k / 32k / 128k / 512k FE/t).
  * Enforced as a true per-tick energy budget across both push and pull interactions.
  * **Brownout Protection Guard**: Automatically pauses machine energy delivery if the combined network buffer (main + subnet energy overlay) drops below 20%.
* **Master Pattern Provider Multi-Page Capacity Expansion**:
  * Added 4 upgrade slots for **AE2 Capacity Cards**.
  * Scales processing pattern storage from 16 up to **80 patterns** across 5 pages (16 patterns per page).
  * Intuitive `<` and `>` page navigation buttons with dynamic page indicator (`Patterns X/Y`).
  * All patterns across all unlocked pages are simultaneously registered with the ME autocrafting engine.
  * Fail-safe drop protection: removing a Capacity Card safely ejects excess patterns on top of the block.
* **Master Pattern Provider Direct FE Input**:
  * High-power direct energy input port on all non-subnet faces, accepting FE via `Capabilities.EnergyStorage.BLOCK` and converting it into network AE energy.
  * Real-time HUD indicator reflects active direct FE power input.
* **Dedicated AE2-Style GUIs & Visual Polish**:
  * Sub-Pattern Provider now has its own dedicated interactive GUI featuring facing rotation, unlock mode toggling (`On Return` vs. `On Empty`), auto-pull toggle, live telemetry HUD, and 4 Energy Card upgrade slots.
  * Widened 232px AE2-style GUI panels with dedicated upgrade card columns cleanly isolated from player inventory.
  * Collision-free telemetry HUD and status indicators with dynamic font-width alignment.
* **Bug Fixes & Refactoring**:
  * Brownout protection correctly measures combined energy overlay totals instead of single subnet buffer.
  * Fixed transaction snapshot accounting and demand over-commit.
  * Corrected default auto-pull persistence flag on world load.

---

## [1.0.0] - 2026-09-17

### Initial Release for Minecraft 1.21.1 (NeoForge)

Supercharge Applied Energistics 2 autocrafting for massive machine arrays with 1-to-many pattern distribution across dedicated ME subnets without channel limits, pattern duplication, or TPS lag!

#### Added
* **Master Pattern Provider**:
  * Hosts up to 16 processing patterns to distribute across any number of machines.
  * Consumes only **1 channel** on your main network while serving infinite machines.
  * Dedicated **Cyan Subnet Port** connecting to your worker subnet.
  * Automatic **Energy Overlay Grid** power bridging to subnets (no quartz fibers, batteries, or channels needed).
  * Real-time **Telemetry HUD**: Displays subnet health, channel availability, live power state, total workers, and idle vs. busy counts.
  * Custom 210x230 AE2-style GUI with recessed slot grids.
* **Sub-Pattern Provider**:
  * Placed adjacent to any machine (furnaces, molecular assemblers, inscribers, modded machines).
  * Uses **0 channels** on the main network.
  * Smart placement heuristic: automatically points into machines when placed against them, or away when placed against cables.
  * Multi-directional fallback insertion for strict-sided inventories (e.g. vanilla furnaces).
  * Automatic craft output interception returning products directly to main storage.
  * Built-in auto-pull fallback for non-ejecting machines.
* **$O(1)$ Event-Driven Subnet Worker Queue**:
  * Lightning-fast worker claiming and dispatching with zero polling overhead and zero TPS lag.
* **Visuals & Animations**:
  * Animated Fluix purple conduits on the Master Provider.
  * Animated 4-frame glowing cyan pulse on the Subnet Port.
  * State indicators on Sub-Pattern Providers: Cyan loop in `IDLE`, glowing amber data-flow pulse in `BUSY`, and deactivated LED in `OFFLINE`.
  * 20-tick visual hold timer so animations remain clearly visible even on 1-tick boosted crafts (e.g. Molecular Assemblers with 5 Acceleration Cards).
  * Wrench rotatable facings.
* **Survival Polish**:
  * Complete crafting recipes using AE2 materials and Quartz Fibers.
  * Pickaxe mineable tags and explosion-resistant block drop loot tables.
  * Pattern drop protection when breaking the Master Provider in-world.
