# Changelog

All notable changes to the **AE2 Sub-Pattern Provider** mod will be documented in this file.

---

## [2.0.0] - 2026-09-17

### Release for Minecraft 26.1.2 (NeoForge 26.1.2.109 / AE2 26.1.11-beta / Java 25)

* **Ported to Minecraft 26.1.2**:
  * Compiled with Java 25 and NeoForge 26.1.2.109.
  * Native compatibility with Applied Energistics 2 `26.1.11-beta`+.
  * Upgraded to NeoForge transfer capability system (`Capabilities.Item.BLOCK`, `Capabilities.Fluid.BLOCK`).
* **Persistence & Grid Connection Fixes**:
  * Fixed grid node exposed sides and network separation across world reloads.
  * Self-healing subnet worker service with round-robin load distribution that never drops workers across chunk unloads or power cycles.
  * Registered server block entity tickers for hold-time and animation synchronization.
* **UI & Visual Polish**:
  * Fixed text opacity in Master Pattern Provider UI for Minecraft 26's ARGB text renderer.
  * Added 26.1 item model definitions for inventory and hotbar display.
* **Recipes**:
  * Updated crafting recipes to Minecraft 26 format.

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
