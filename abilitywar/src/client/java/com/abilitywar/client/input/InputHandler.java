package com.abilitywar.client.input;

import com.abilitywar.ability.SkillSlot;
import com.abilitywar.client.ClientState;
import com.abilitywar.client.anim.ClientClock;
import com.abilitywar.client.anim.PlayerAnimator;
import com.abilitywar.client.screen.AbilitySelectScreen;
import com.abilitywar.data.AWPlayer;
import com.abilitywar.net.AWNet;
import com.abilitywar.registry.AWEffects;
import com.abilitywar.registry.AWSounds;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

/**
 * 1 2 3 4 R T F 키 처리. 능력이 있을 때만 바닐라 키(단축바/채팅/손 바꾸기)를 가로챈다.
 */
public final class InputHandler {
    public static final String CATEGORY = "key.categories.abilitywar";
    public static KeyBinding SKILL1, SKILL2, SKILL3, SKILL4, ULTIMATE, PASSIVE, BLOCK, SELECT, DASH, GRIMOIRE;
    private static KeyBinding[] SLOT_KEYS;

    private static boolean blockHeld;
    private static long nextAttack;
    private static long lastAttack = -100;
    private static int combo;
    // 꾹 누르기 (용기사 T)
    private static SkillSlot holdSlot;
    private static long holdStart;
    private static int holdTicks;
    private static boolean holdTest;

    public static void init() {
        SKILL1 = reg("key.abilitywar.skill1", GLFW.GLFW_KEY_1);
        SKILL2 = reg("key.abilitywar.skill2", GLFW.GLFW_KEY_2);
        SKILL3 = reg("key.abilitywar.skill3", GLFW.GLFW_KEY_3);
        SKILL4 = reg("key.abilitywar.skill4", GLFW.GLFW_KEY_4);
        ULTIMATE = reg("key.abilitywar.ultimate", GLFW.GLFW_KEY_R);
        PASSIVE = reg("key.abilitywar.passive", GLFW.GLFW_KEY_T);
        BLOCK = reg("key.abilitywar.block", GLFW.GLFW_KEY_F);
        SELECT = reg("key.abilitywar.select", GLFW.GLFW_KEY_B);
        DASH = reg("key.abilitywar.dash", GLFW.GLFW_KEY_Q);
        GRIMOIRE = reg("key.abilitywar.grimoire", GLFW.GLFW_KEY_G);
        SLOT_KEYS = new KeyBinding[]{SKILL1, SKILL2, SKILL3, SKILL4, ULTIMATE, PASSIVE};
    }

    private static KeyBinding reg(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(name, InputUtil.Type.KEYSYM, key, CATEGORY));
    }

    public static boolean isOurs(KeyBinding k) {
        return k != null && CATEGORY.equals(k.getCategory());
    }

    /** 키 충돌 시 바닐라 키가 우선 등록되도록 (능력이 없을 땐 바닐라 동작 유지). */
    public static void fixKeyMap() {
        Map<InputUtil.Key, KeyBinding> map = KeyBinding.KEY_TO_BINDINGS;
        for (KeyBinding k : KeyBinding.KEYS_BY_ID.values()) {
            if (!isOurs(k)) map.put(k.boundKey, k);
        }
    }

    public static boolean active() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.currentScreen == null && !ClientState.ability.isEmpty() && !mc.player.isSpectator();
    }

    public static boolean isBlockHeld() {
        return blockHeld;
    }

    // ------------------------------------------------------------------ raw input

    /** @return true 면 바닐라 처리 취소. */
    public static boolean onKey(int key, int scancode, int action) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null) return false;
        if (InputUtil.isKeyPressed(mc.getWindow().getHandle(), GLFW.GLFW_KEY_F3)) return false;
        if (SELECT.matchesKey(key, scancode)) {
            if (action == GLFW.GLFW_PRESS) mc.setScreen(new AbilitySelectScreen());
            return true;
        }
        if (!active()) return false;
        if (GRIMOIRE.matchesKey(key, scancode) && "warlock".equals(ClientState.ability)) {
            // 흑마법사: 흑마법서 (해골 장비 / 영혼 목록)
            if (action == GLFW.GLFW_PRESS) ClientPlayNetworking.send(AWNet.C2S_GRIMOIRE, net.fabricmc.fabric.api.networking.v1.PacketByteBufs.empty());
            return true;
        }
        for (int i = 0; i < SLOT_KEYS.length; i++) {
            if (SLOT_KEYS[i].matchesKey(key, scancode)) {
                if (action == GLFW.GLFW_PRESS) pressSlot(SkillSlot.VALUES[i]);
                else if (action == GLFW.GLFW_RELEASE) releaseSlot(SkillSlot.VALUES[i]);
                return true;
            }
        }
        if (BLOCK.matchesKey(key, scancode)) {
            if (action == GLFW.GLFW_PRESS) setBlock(true);
            else if (action == GLFW.GLFW_RELEASE) setBlock(false);
            return true;
        }
        if (DASH.matchesKey(key, scancode)) {
            if (action == GLFW.GLFW_PRESS) DashController.tryDash();
            return true;
        }
        return false;
    }

    public static boolean onMouse(int button, int action) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!active()) return false;
        for (int i = 0; i < SLOT_KEYS.length; i++) {
            if (SLOT_KEYS[i].matchesMouse(button)) {
                if (action == GLFW.GLFW_PRESS) pressSlot(SkillSlot.VALUES[i]);
                else if (action == GLFW.GLFW_RELEASE) releaseSlot(SkillSlot.VALUES[i]);
                return true;
            }
        }
        if (BLOCK.matchesMouse(button)) {
            if (action == GLFW.GLFW_PRESS) setBlock(true);
            else if (action == GLFW.GLFW_RELEASE) setBlock(false);
            return true;
        }
        if (DASH.matchesMouse(button)) {
            if (action == GLFW.GLFW_PRESS) DashController.tryDash();
            return true;
        }
        return false;
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (holdSlot != null) {
            ClientPlayerEntity p = mc.player;
            if (p == null || !active() || locked(p) || !ClientState.ready(holdSlot)) {
                holdSlot = null;
            } else {
                long t = ClientClock.ticks() - holdStart;
                if (t % 4 == 0) {
                    float pitch = 0.6f + 0.8f * Math.min(1, t / (float) holdTicks);
                    mc.getSoundManager().play(PositionedSoundInstance.master(AWSounds.UI_CHARGE, pitch, 0.35f));
                }
                if (t >= holdTicks) {
                    SkillSlot s = holdSlot;
                    holdSlot = null;
                    useSkill(s);
                }
            }
        }
        if (blockHeld && !com.abilitywar.client.dev.TestDriver.active()) {
            boolean still = active();
            if (still && BLOCK.boundKey.getCategory() == InputUtil.Type.KEYSYM) {
                still = InputUtil.isKeyPressed(mc.getWindow().getHandle(), BLOCK.boundKey.getCode());
            }
            if (!still) setBlock(false);
        }
    }

    // ------------------------------------------------------------------ actions

    public static boolean locked(ClientPlayerEntity p) {
        AWPlayer awp = (AWPlayer) p;
        return p.hasStatusEffect(AWEffects.STUN) || awp.aw$hasFlag(AWPlayer.FLAG_CINEMATIC);
    }

    /** 키 누름: 꾹 눌러야 하는 스킬이면 충전 시작. */
    public static void pressSlot(SkillSlot slot) {
        MinecraftClient mc = MinecraftClient.getInstance();
        com.abilitywar.ability.Ability a = com.abilitywar.ability.Abilities.byString(ClientState.ability);
        int hold = slot == SkillSlot.PASSIVE && a != null && mc.player != null && !ClientState.shadowForm ? a.passiveHoldTicks(mc.player) : 0;
        if (hold <= 0) {
            useSkill(slot);
            return;
        }
        if (mc.player == null || locked(mc.player) || blockHeld || !ClientState.ready(slot) || ClientState.busy > 0) {
            deny();
            return;
        }
        holdSlot = slot;
        holdStart = ClientClock.ticks();
        holdTicks = hold;
        ClientState.pressFlash[slot.ordinal()] = 1f;
    }

    public static void releaseSlot(SkillSlot slot) {
        if (holdSlot == slot && !holdTest) holdSlot = null;
    }

    /** 테스트: 키를 계속 누르고 있는 것처럼 (충전이 끝날 때까지). */
    public static void testHold(SkillSlot slot) {
        holdTest = true;
        pressSlot(slot);
    }

    /** 0..1 꾹 누르기 진행도 (HUD). */
    public static float holdProgress(SkillSlot slot, float td) {
        if (holdSlot != slot || holdTicks <= 0) return 0;
        return Math.min(1, (ClientClock.ticks() - holdStart + td) / holdTicks);
    }

    public static void useSkill(SkillSlot slot) {
        holdTest = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(slot.ordinal());
        if (ClientState.shadowForm) {
            if (slot != SkillSlot.PASSIVE) return;
            ShadowTarget t = ShadowTarget.compute(p);
            t.write(buf);
            ClientPlayNetworking.send(AWNet.C2S_SKILL, buf);
            return;
        }
        com.abilitywar.ability.Ability ab = com.abilitywar.ability.Abilities.byString(ClientState.ability);
        boolean busyOk = ab != null && ab.clientAllowWhileBusy(p, slot);
        if (locked(p) || blockHeld || !ClientState.ready(slot) || (ClientState.busy > 0 && !busyOk)
                || (slot != SkillSlot.DASH && p.hasStatusEffect(AWEffects.SILENCE))) {
            deny();
            return;
        }
        ClientState.pressFlash[slot.ordinal()] = 1f;
        ClientPlayNetworking.send(AWNet.C2S_SKILL, buf);
    }

    public static void deny() {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(AWSounds.UI_DENY, 1.0f, 0.5f));
    }

    public static void testBlock(boolean on) {
        setBlock(on);
    }

    static void setBlock(boolean on) {
        if (blockHeld == on) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (on && (mc.player == null || locked(mc.player) || ClientState.shadowForm || ClientState.busy > 0)) return;
        blockHeld = on;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(on);
        ClientPlayNetworking.send(AWNet.C2S_BLOCK, buf);
    }

    /** 왼쪽 클릭 (MinecraftClient.doAttack). @return true 면 바닐라 공격 취소. */
    public static boolean onAttack() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || ClientState.ability.isEmpty()) return false;
        if (ClientState.shadowForm) {
            useSkill(SkillSlot.PASSIVE);
            return true;
        }
        // RPG 무기를 들고 있으면 능력 평타 (무기 능력치가 붙음), 다른 아이템이면 바닐라 공격
        if (!p.getMainHandStack().isEmpty() && !(p.getMainHandStack().getItem() instanceof com.abilitywar.gear.GearWeaponItem)) return locked(p);
        HitResult hit = mc.crosshairTarget;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) return locked(p);
        if (locked(p) || blockHeld || ClientState.busy > 0) return true;
        long now = ClientClock.ticks();
        if (now < nextAttack) return true;
        if (now - lastAttack > 24) combo = 0;
        Entity target = hit instanceof EntityHitResult ehr ? ehr.getEntity() : aimAssist(p, 4.0);
        com.abilitywar.ability.Ability ab = com.abilitywar.ability.Abilities.byString(ClientState.ability);
        String anim = ab != null ? ab.attackAnim(p, combo) : ClientState.ability + ".attack" + (combo + 1);
        PlayerAnimator.play(p.getId(), anim, 1f);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(target instanceof LivingEntity ? target.getId() : -1);
        ClientPlayNetworking.send(AWNet.C2S_ATTACK, buf);
        nextAttack = now + AWEffects.attackInterval(p, ab != null ? ab.attackInterval(combo) : (combo == 2 ? 13 : 7));
        lastAttack = now;
        combo = (combo + 1) % 3;
        p.resetLastAttackedTicks();
        return true;
    }

    @Nullable
    static Entity aimAssist(ClientPlayerEntity p, double range) {
        Vec3d eye = p.getCameraPosVec(1f);
        Vec3d look = p.getRotationVec(1f);
        Vec3d end = eye.add(look.multiply(range));
        Box box = p.getBoundingBox().stretch(look.multiply(range)).expand(1.0);
        EntityHitResult r = ProjectileUtil.raycast(p, eye, end, box.expand(0.4), e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != p, range * range);
        return r == null ? null : r.getEntity();
    }

    /** 그림자 탈출 위치 계산 (조준 지점). */
    public static final class ShadowTarget {
        public int type; // 0 = 제자리, 1 = 엔티티, 2 = 블록
        public int entityId;
        public BlockPos stand;
        public Vec3d pos;
        public boolean valid;

        public void write(PacketByteBuf buf) {
            buf.writeByte(type);
            if (type == 1) buf.writeVarInt(entityId);
            if (type == 2) buf.writeBlockPos(stand);
        }

        public static ShadowTarget compute(ClientPlayerEntity p) {
            ShadowTarget t = new ShadowTarget();
            MinecraftClient mc = MinecraftClient.getInstance();
            double range = 40;
            Vec3d eye = p.getCameraPosVec(1f);
            Vec3d look = p.getRotationVec(1f);
            Vec3d end = eye.add(look.multiply(range));
            BlockHitResult bh = p.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            double blockDist = bh.getType() == HitResult.Type.MISS ? range : bh.getPos().distanceTo(eye);
            // 엔티티의 그림자 (조준 원뿔 안)
            Entity best = null;
            double bestScore = 1e9;
            for (Entity e : p.getWorld().getOtherEntities(p, p.getBoundingBox().expand(range), e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
                Vec3d c = e.getPos();
                Vec3d to = c.subtract(eye);
                double d = to.length();
                if (d > range || d > blockDist + 3) continue;
                Vec3d toC = e.getBoundingBox().getCenter().subtract(eye);
                double ang = Math.min(Math.acos(Math.max(-1, Math.min(1, to.normalize().dotProduct(look)))),
                        Math.acos(Math.max(-1, Math.min(1, toC.normalize().dotProduct(look)))));
                double slack = Math.atan2(e.getWidth() + 0.6, d);
                if (ang > slack + Math.toRadians(2.5)) continue;
                double score = ang + d * 0.002;
                if (score < bestScore) {
                    bestScore = score;
                    best = e;
                }
            }
            if (best != null) {
                t.type = 1;
                t.entityId = best.getId();
                t.pos = best.getPos();
                t.valid = true;
                return t;
            }
            if (bh.getType() == HitResult.Type.BLOCK) {
                BlockPos hitPos = bh.getBlockPos();
                BlockPos stand = bh.getSide() == Direction.UP ? hitPos.up() : findStand(p, bh);
                if (stand != null) {
                    t.type = 2;
                    t.stand = stand;
                    t.pos = Vec3d.ofBottomCenter(stand);
                    t.valid = isShadow(p, stand);
                    if (!t.valid) t.type = 0;
                    return t;
                }
            }
            t.type = 0;
            t.pos = p.getPos();
            t.valid = false;
            return t;
        }

        @Nullable
        static BlockPos findStand(ClientPlayerEntity p, BlockHitResult bh) {
            BlockPos side = bh.getBlockPos().offset(bh.getSide());
            for (int dy = 0; dy >= -3; dy--) {
                BlockPos s = side.up(dy);
                if (!p.getWorld().getBlockState(s.down()).isAir() && p.getWorld().getBlockState(s).isAir()) return s;
            }
            return null;
        }

        public static boolean isShadow(ClientPlayerEntity p, BlockPos stand) {
            var w = p.getWorld();
            if (!w.isSkyVisible(stand)) return true;
            return w.getLightLevel(stand) <= 7;
        }
    }

    private InputHandler() {
    }
}
