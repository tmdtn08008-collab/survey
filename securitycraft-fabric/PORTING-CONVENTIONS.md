# SecurityCraft Fabric port: conventions

This directory is SecurityCraft 1.10.2.1 (upstream branch `1.21.1`, MIT) being ported from NeoForge to
Fabric for Minecraft 1.21.1. Read this before editing anything.

## Target

- Minecraft 1.21.1, Fabric Loader 0.16.x, Fabric API 0.116.17+1.21.1, Java 21.
- Loom with `loom.officialMojangMappings()`: every vanilla name is the Mojang name, exactly as in the
  NeoForge source. Fabric API's own source on GitHub uses Yarn names; a Mojang-named copy of the Fabric
  API sources is unpacked in `$S/ref/fabric-api-mojmap` (see paths below). Use that one.
- Forge Config API Port (`fuzs.forgeconfigapiport`) provides NeoForge's `ModConfigSpec`, `ModConfig`
  and `ConfigurationScreen` on Fabric, under their original `net.neoforged...` package names. Those
  imports stay as they are.
- `javax.annotation.*` (jsr305) is available at compile time.

## Reference material (read-only)

`S=/tmp/claude-0/-home-user-survey/87ca93ba-7375-55fd-b0fa-dd0f8b362692/scratchpad`

- `$S/ref/mc-src`: decompiled Minecraft 1.21.1, Mojang names, with this mod's access widener applied.
- `$S/ref/fabric-api-mojmap`: Fabric API 0.116.17 sources, Mojang names.
- `$S/ref/neoforge`: NeoForge 1.21.1 source. `patches/` shows exactly where NeoForge hooks into
  vanilla, which tells you where a Fabric mixin has to go. NeoForge is LGPL: do not copy its code into
  this MIT mod; write your own implementation.
- `$S/sc-src`: the untouched upstream SecurityCraft 1.21.1 source, for comparison.
- `$S/res_*.json`: the analysis reports for each subsystem (usages, extension overrides, events,
  shims and mixins needed, open questions).

## Checking your work

`$S/sccheck.sh '<regex>'` compiles the whole mod with javac in about 10 seconds. It prints the total
error count and every error whose file path matches the regex. Several copies can run at once. Run it
with a regex covering the files you own, and finish with zero errors in them. It does not run the
Mixin annotation processor, so mixin targets are only checked by the real Gradle build and at runtime:
double-check every mixin target method and descriptor against `$S/ref/mc-src`.

## Shims that already exist (package `net.geforcemods.securitycraft.fabric`)

| Replaces (NeoForge) | Use |
|---|---|
| `DeferredRegister`, `DeferredHolder`, `DeferredBlock`, `DeferredItem` | `fabric.registry.*`. `DeferredHolder` does NOT implement `Holder`: use `.get()` for the value, or `.getDelegate()` when a real vanilla `Holder` is needed. `state.is(SCContent.X)` becomes `state.is(SCContent.X.get())`. |
| Data components | `SCContent` data component fields are plain `DataComponentType<T>` now (no `.get()`), so `stack.get(SCContent.X)` works against vanilla. |
| `PacketDistributor` | `fabric.network.PacketDistributor`, same static method names. |
| `IPayloadContext`, payload registration | `fabric.network.IPayloadContext` (`player()`, `enqueueWork(Runnable)` runs immediately, since Fabric already calls handlers on the main thread), `fabric.network.PayloadRegistrar`. |
| `IMenuTypeExtension.create`, `IContainerFactory` | `fabric.menu.*` |
| `IMenuProviderExtension#writeClientSideData` | `fabric.menu.IMenuProviderExtension` (extends Fabric's `ExtendedScreenHandlerFactory`). Every provider that opens a SecurityCraft menu type MUST implement it (anonymous `new MenuProvider()` becomes `new IMenuProviderExtension()`), otherwise Fabric throws when the menu opens. `MenuProvider.super.writeClientSideData(...)` becomes `IMenuProviderExtension.super.writeClientSideData(...)`. |
| `ServerPlayer#openMenu(provider, pos)` / `(provider, Consumer<buf>)` | `fabric.menu.MenuHelper.openMenu(serverPlayer, provider, pos / writer)` |
| `TriState`, `TriPredicate`, `NeoForgeStreamCodecs`, `ServerLifecycleHooks`, `CommonHooks.newChatWithLinks/getCraftingPlayer` | `fabric.util.*` |
| `BaseFlowingFluid` | `fabric.fluid.BaseFlowingFluid` |
| `TicketController` | `fabric.world.TicketController` |
| `NeoForge.EVENT_BUS.post(...)` for SecurityCraft's own events | `fabric.event.NeoForge`, a tiny bus. Do not route vanilla or Fabric events through it. |
| `BuildCreativeModeTabContentsEvent` | `fabric.event.BuildCreativeModeTabContentsEvent` |
| `IItemHandler`, `IItemHandlerModifiable`, `ItemStackHandler`, `InvWrapper`, `SidedInvWrapper`, `EmptyItemHandler`, `SlotItemHandler`, `VanillaHopperItemHandler` | `fabric.items.*`, clean-room versions with the same API. They are internal only: other mods and vanilla hoppers never see them. Exposing inventories to the outside goes through `fabric.items.SCItemStorages` (Fabric Transfer API) plus hopper mixins. |
| `ModelData`, `ModelProperty` | `fabric.model.*` |
| `EntityTeleportEvent` (+ nested ChorusFruit, EnderPearl, EnderEntity, TeleportCommand, SpreadPlayersCommand) | `fabric.event.EntityTeleportEvent`, a plain object with `setCanceled`/`isCanceled` |
| `ModList.get().isLoaded(id)`, `FMLEnvironment.dist`, `FMLLoader` | `FabricLoader.getInstance().isModLoaded(id)`, `FabricLoader.getInstance().getEnvironmentType()` |

## Rules

1. Edit only the files your work unit owns. If you need a change in a file you do not own, do not make
   it: report it in your result as a handoff (file, what to change, why).
2. Keep edits minimal and mechanical. Keep class names, method names and structure, so upstream diffs
   stay readable. Do not reformat code you are not changing.
3. NeoForge extension methods (`IBlockExtension`, `IItemExtension`, `IBlockEntityExtension`, ...) do not
   exist on Fabric. An override of one either stops compiling (if it has `@Override`) or silently never
   gets called (if it does not). Never just delete the `@Override` and move on. Wire the behavior back
   with a SecurityCraft-owned hook interface plus a mixin at the vanilla call site that NeoForge patched
   (see `$S/ref/neoforge/patches`), or document in your result exactly why the behavior is unneeded.
   Security behavior (owners, passcodes, protection from breaking, explosions, hoppers, pistons, mobs)
   must never be dropped.
4. Mixins: put new mixins for your unit in package `net.geforcemods.securitycraft.fabricmixin.<unit>` and
   list them in `src/main/resources/securitycraft.fabric.<unit>.mixins.json` (`mixins` for both sides,
   `client` for client-only). MixinExtras (`@WrapOperation`, `@ModifyReturnValue`, `@ModifyExpressionValue`,
   `@Local`, ...) is available and preferred over `@Redirect`. Prefix every added field or method in a
   mixin with `securitycraft$`. Target methods by Mojang name plus descriptor whenever the name is
   overloaded. Mark client-only code so it never loads on a dedicated server.
5. Client-only classes (anything touching `net.minecraft.client`) must only be reached from client
   entrypoints or client mixins.
6. Where Fabric cannot reproduce something, leave a `// PORT-NOTE:` comment at the spot explaining what
   differs, and list it in your result.
7. Do not delete files except the NeoForge-only datagen package when your unit owns it.
