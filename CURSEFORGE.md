# AE2 Sub-Pattern Provider

**Scale your autocrafting across massive machine arrays without channel limits, pattern duplication, or TPS lag!**

---

### 💡 The Problem: Autocrafting Bottlenecks in AE2
In complex factories and modpacks (featuring GregTech, Modern Industrialization, Mekanism, or simply large rows of Inscribers and Furnaces), you often need **10, 20, or even 50 identical machines** to process a single recipe at high speed.

In standard AE2, achieving this is painful:
- ❌ **Pattern Duplication**: You have to craft and encode dozens of identical patterns and distribute them manually across individual pattern providers.
- ❌ **Channel Waste**: Every single pattern provider consumes a precious channel on your main ME network, quickly exhausting dense cables and controllers.
- ❌ **Complex Subnet Workarounds**: Building storage bus subnets with interfaces requires complex filters, hopper chains, and can cause item-spill or round-robin stalling.

---

### ⚡ The Solution: AE2 Sub-Pattern Provider
**AE2 Sub-Pattern Provider** introduces a high-performance **1-to-many pattern distribution system** powered by a dedicated ME subnet and an $O(1)$ event-driven worker queue.

You encode your processing pattern **once** into the **Master Pattern Provider**. The mod automatically discovers all connected **Sub-Pattern Providers** on its subnet and dispatches crafting jobs to whatever machines are idle—distributing the workload evenly and running all machines at maximum throughput!

```
[ME Crafting CPU]
       │ (pushed craft)
       ▼
[Master Pattern Provider]  ─── 1 Channel on Main Network
       │
       │ (Dedicated ME Subnet - 0 Main Channels!)
       ├───► [Sub-Provider #1] ──► Machine #1 (Furnace / Assembler / Inscriber)
       ├───► [Sub-Provider #2] ──► Machine #2 (Furnace / Assembler / Inscriber)
       ├───► [Sub-Provider #3] ──► Machine #3 (Furnace / Assembler / Inscriber)
       └───► [Sub-Provider #N] ──► Machine #N ... (Infinite parallel scaling!)
```

---

### ✨ Key Features

* 🚀 **1-to-Many Pattern Distribution**:
  Place 16 processing patterns in the Master Provider, and any number of machines connected via Sub-Pattern Providers will execute them concurrently.
* ⚡ **Zero Main Network Channel Waste**:
  The Master Provider uses only **1 channel** on your main network. The entire worker subnet uses **0 main channels**, allowing you to run 50+ machines off a single main cable connection.
* ⚡ **Zero-Latency Power Bridging**:
  The Master Provider automatically bridges energy from your main ME network into the subnet using AE2's canonical Energy Overlay Grid. No quartz fibers, external batteries, or separate power cables required!
* ⏱️ **$O(1)$ Event-Driven Worker Queue**:
  Zero TPS impact! Unlike mods that poll machines in heavy tick loops, workers register themselves into an instant queue. Idle workers are claimed and returned in $O(1)$ constant time.
* 🔄 **Smart Directional Placement & Multi-IO Support**:
  - Automatically points its injection nozzle into adjacent machines (chests, furnaces, molecular assemblers, modded machines).
  - Handles strict directional inventories (like vanilla furnaces) seamlessly.
  - Automatically intercepts craft outputs and returns them to main storage.
  - Features an active auto-pull fallback for machines that do not auto-eject.
* 📊 **Live Telemetry HUD**:
  Open the Master Provider GUI to view real-time diagnostics:
  - Subnet connection status (`SUBNET ONLINE` / `NO WORKERS`)
  - Real-time Channel state (`1 Channel` / `Offline`)
  - Subnet Power state (`Active` / `Bridged` / `Unpowered`)
  - Live worker counter (`Total Workers`, `Idle`, and `Busy`)
* 🎨 **Authentic AE2 Visuals & Animations**:
  - **Master Provider**: Distinct glowing cyan subnet port with animated concentric ring pulses, and Fluix purple conduits on main network sides.
  - **Sub-Pattern Provider**: Features active state indicators—cyan energy loop in `IDLE`, glowing amber data pulse during `BUSY` crafts, and deactivated LED when offline.
  - Built-in visual hold timer ensuring animated textures remain visible even on 1-tick boosted crafts (e.g., Assemblers with 5 Acceleration Cards).

---

### 📖 Quick Start Guide

1. **Craft the Master Pattern Provider**: Place it connected to your main ME network.
2. **Connect the Subnet**: Run an ME Glass Cable or Smart Cable from the **cyan subnet port** on the Master Provider to your machine line.
   *(Tip: Right-click the Master Provider with an AE2 Wrench to rotate the cyan port if needed!)*
3. **Place Sub-Pattern Providers**: Place a Sub-Pattern Provider against each machine in your array. Connect each sub-provider with your subnet cables.
4. **Insert Patterns**: Open the Master Pattern Provider GUI and drop in your encoded processing patterns.
5. **Start Crafting**: Request the crafted item from any ME Terminal. Watch all machines light up and craft in parallel!

---

### 🔧 Configuration & Compatibility
- **Minecraft Version**: 1.21.1
- **Mod Loader**: NeoForge (21.1+)
- **Dependencies**: Applied Energistics 2 (`19.2+`)
- **Mod Compatibility**: Compatible with all modded machines supporting NeoForge `ItemHandler` or `FluidHandler` capabilities (Modern Industrialization, GregTech, Mekanism, Thermal, EnderIO, etc.).

---

### 📦 Modpack Policy
You are completely free to include **AE2 Sub-Pattern Provider** in any public or private modpack on CurseForge, Modrinth, or custom launchers. No special permission needed!
