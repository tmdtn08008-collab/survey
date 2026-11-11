package com.abilitywar.rpg;

import com.abilitywar.ability.Abilities;
import com.abilitywar.ability.Ability;
import com.abilitywar.data.AWPlayer;
import com.abilitywar.data.AbilityData;
import com.abilitywar.gear.GearAttributes;
import com.abilitywar.gear.GearGrade;
import com.abilitywar.gear.GearItems;
import com.abilitywar.net.AWNet;
import com.abilitywar.registry.AWGameRules;
import com.abilitywar.server.AbilityManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * RPG 진행 (서버): 몹을 잡으면 경험치 → 레벨 업 → 스탯 포인트. 스탯은 능력치로 바뀌어 플레이어에게 붙는다.
 * 전직 교관 NPC 에게서 2차 전직 퀘스트를 받아 목표를 채우고 돌아오면 2차 직업을 얻는다.
 */
public final class RpgManager {
    public static final int MAX_LEVEL = 100;
    public static final String[] STAT_KEYS = {"str", "dex", "int", "vit", "wis"};
    public static final Identifier S2C_RPG = com.abilitywar.AbilityWar.id("rpg");
    public static final Identifier C2S_RPG = com.abilitywar.AbilityWar.id("rpg_action");
    public static final Identifier S2C_OPEN_NPC = com.abilitywar.AbilityWar.id("open_npc");

    public static RpgData rpg(PlayerEntity p) {
        return ((AWPlayer) p).aw$data().rpg;
    }

    /** 레벨 L 에서 다음 레벨까지 필요한 경험치. */
    public static long need(int level) {
        return Math.round(8 + 3 * Math.pow(level, 1.45));
    }

    // ================================================================ 경험치 / 레벨

    /** 보스 (바닐라 보스 + 이 모드 보스 + 다른 모드 보스: 태그 abilitywar:bosses). */
    public static final net.minecraft.registry.tag.TagKey<net.minecraft.entity.EntityType<?>> BOSSES =
            net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.ENTITY_TYPE, com.abilitywar.AbilityWar.id("bosses"));

    public static boolean isBoss(LivingEntity e) {
        return e.getType().isIn(BOSSES) || e instanceof WitherEntity || e instanceof EnderDragonEntity || e instanceof WardenEntity || e instanceof ElderGuardianEntity;
    }

    public static int mobXp(LivingEntity e) {
        if (e instanceof PlayerEntity) return 0;
        if (isBoss(e)) return Math.max(200, Math.round(e.getMaxHealth() * 2));
        if (com.abilitywar.ability.warlock.Necromancy.isMinion(e)) return 0;
        if (!(e instanceof Monster)) return 1;
        float xp = e.getMaxHealth() * 0.5f;
        if (e instanceof WitherEntity || e instanceof EnderDragonEntity || e instanceof WardenEntity || e instanceof ElderGuardianEntity) xp *= 4;
        return Math.max(1, Math.round(xp));
    }

    public static void addXp(ServerPlayerEntity p, long amount) {
        if (amount <= 0) return;
        amount = Math.max(1, amount * p.getWorld().getGameRules().getInt(AWGameRules.XP_RATE) / 100);
        RpgData r = rpg(p);
        if (r.level >= MAX_LEVEL) return;
        r.xp += amount;
        while (r.level < MAX_LEVEL && r.xp >= need(r.level)) {
            r.xp -= need(r.level);
            levelUp(p, r);
        }
        if (r.level >= MAX_LEVEL) r.xp = 0;
        p.sendMessage(Text.translatable("rpg.abilitywar.xp_gain", amount).formatted(Formatting.GREEN), true);
        r.dirty = true;
    }

    static void levelUp(ServerPlayerEntity p, RpgData r) {
        r.level++;
        int gain = 3 + (r.level % 10 == 0 ? 2 : 0);
        r.points += gain;
        p.setHealth(p.getMaxHealth());
        ServerWorld w = p.getServerWorld();
        NbtCompound n = new NbtCompound();
        n.putInt("e", p.getId());
        n.putInt("l", r.level);
        AWNet.fx(w, p.getPos(), "level_up", n);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.7f, 1.2f);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1f, 1f);
        p.sendMessage(Text.translatable("rpg.abilitywar.level_up", r.level, gain).formatted(Formatting.GOLD), false);
        int jl = p.getWorld().getGameRules().getInt(AWGameRules.JOB_LEVEL);
        if (r.level == jl && ClassTree.tier(((AWPlayer) p).aw$getAbilityKey()) == 1)
            p.sendMessage(Text.translatable("rpg.abilitywar.job_ready").formatted(Formatting.AQUA), false);
    }

    public static void setLevel(ServerPlayerEntity p, int level) {
        RpgData r = rpg(p);
        level = Math.max(1, Math.min(MAX_LEVEL, level));
        // 레벨에 맞는 포인트 (이미 찍은 것 제외)
        int total = 0;
        for (int l = 2; l <= level; l++) total += 3 + (l % 10 == 0 ? 2 : 0);
        r.level = level;
        r.xp = 0;
        r.points = Math.max(0, total - r.spent());
        r.dirty = true;
    }

    /** 몹이 죽음: 막타 친 플레이어 (또는 하수인의 주인) 와 근처 같은 팀에게 경험치, 퀘스트 처치 수. */
    public static void onDeath(LivingEntity dead, net.minecraft.entity.damage.DamageSource source) {
        if (dead.getWorld().isClient) return;
        if (isBoss(dead) && dead.getWorld() instanceof ServerWorld sw) {
            com.abilitywar.mob.BossAltar.onAnyBossDeath(dead);
            // 보스: 가까이서 싸운 모두가 경험치 + 퀘스트 (마지막 한 방이 누구든)
            int xp = mobXp(dead);
            for (ServerPlayerEntity o : sw.getEntitiesByClass(ServerPlayerEntity.class, dead.getBoundingBox().expand(48), o -> !o.isSpectator() && o.isAlive())) {
                addXp(o, xp);
                questAmount(o, JobQuests.Type.BOSS, 1);
                questKill(o, dead);
                o.sendMessage(Text.translatable("rpg.abilitywar.boss_down", dead.getDisplayName()).formatted(Formatting.GOLD), false);
            }
            return;
        }
        Entity att = source.getAttacker();
        ServerPlayerEntity killer = null;
        if (att instanceof ServerPlayerEntity sp) killer = sp;
        else if (att != null) {
            UUID owner = com.abilitywar.ability.warlock.Necromancy.ownerOf(att);
            if (owner != null && dead.getServer() != null) killer = dead.getServer().getPlayerManager().getPlayer(owner);
        }
        if (killer == null || killer == dead) return;
        final ServerPlayerEntity k = killer;
        int xp = mobXp(dead);
        addXp(k, xp);
        questKill(k, dead);
        if (k.getScoreboardTeam() != null) {
            for (ServerPlayerEntity o : k.getServerWorld().getEntitiesByClass(ServerPlayerEntity.class, new Box(k.getBlockPos()).expand(32), o -> o != k && o.isTeammate(k))) {
                addXp(o, Math.max(1, xp / 2));
                questKill(o, dead);
            }
        }
    }

    // ================================================================ 스탯
    static final UUID[][] STAT_UUID = new UUID[RpgData.STATS][3];

    static {
        for (int i = 0; i < RpgData.STATS; i++)
            for (int k = 0; k < 3; k++) STAT_UUID[i][k] = UUID.nameUUIDFromBytes(("abilitywar_stat:" + i + ":" + k).getBytes(StandardCharsets.UTF_8));
    }

    /** 스탯 하나의 효과: (능력치, 포인트당 값, 방식). */
    record Eff(EntityAttribute attr, double per, EntityAttributeModifier.Operation op) {
    }

    static Eff[] effects(int stat) {
        var add = EntityAttributeModifier.Operation.ADDITION;
        return switch (stat) {
            case 0 -> new Eff[]{new Eff(GearAttributes.PHYSICAL_DAMAGE, 0.015, add), new Eff(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.1, add)};
            case 1 -> new Eff[]{new Eff(GearAttributes.CRIT_CHANCE, 0.004, add), new Eff(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.002, EntityAttributeModifier.Operation.MULTIPLY_BASE)};
            case 2 -> new Eff[]{new Eff(GearAttributes.MAGIC_DAMAGE, 0.015, add)};
            case 3 -> new Eff[]{new Eff(EntityAttributes.GENERIC_MAX_HEALTH, 1.0, add)};
            default -> new Eff[]{new Eff(GearAttributes.COOLDOWN, 0.003, add), new Eff(GearAttributes.HEAL_POWER, 0.015, add)};
        };
    }

    public static void applyStats(ServerPlayerEntity p) {
        RpgData r = rpg(p);
        for (int i = 0; i < RpgData.STATS; i++) {
            Eff[] es = effects(i);
            for (int k = 0; k < es.length; k++) {
                EntityAttributeInstance inst = p.getAttributeInstance(es[k].attr());
                if (inst == null) continue;
                inst.removeModifier(STAT_UUID[i][k]);
                if (r.stats[i] > 0)
                    inst.addTemporaryModifier(new EntityAttributeModifier(STAT_UUID[i][k], "Stat " + STAT_KEYS[i], es[k].per() * r.stats[i], es[k].op()));
            }
        }
        // 3차 전직: 최대 체력 +6
        EntityAttributeInstance hp = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hp != null) {
            hp.removeModifier(GRAND_HP);
            if (isGrand(p)) hp.addTemporaryModifier(new EntityAttributeModifier(GRAND_HP, "Grand class", 6, EntityAttributeModifier.Operation.ADDITION));
        }
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    static final UUID GRAND_HP = UUID.fromString("7d3e7d2a-1c55-4f0e-9d7a-6b0b8a3e5f11");
    /** 3차 전직 배율: 주는 피해, 받는 피해, 재사용 대기, 궁극기 충전, 회복량, 버프 지속. */
    public static final float GRAND_DAMAGE = 1.3f, GRAND_TAKEN = 0.9f, GRAND_COOLDOWN = 0.85f, GRAND_ULT = 1.2f, GRAND_HEAL = 0.3f, GRAND_BUFF = 1.3f;

    public static void spend(ServerPlayerEntity p, int stat, int amount) {
        RpgData r = rpg(p);
        if (stat < 0 || stat >= RpgData.STATS) return;
        amount = Math.min(amount, r.points);
        if (amount <= 0) return;
        r.points -= amount;
        r.stats[stat] += amount;
        applyStats(p);
        r.dirty = true;
        p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.4f);
    }

    public static void resetStats(ServerPlayerEntity p) {
        RpgData r = rpg(p);
        r.points += r.spent();
        java.util.Arrays.fill(r.stats, 0);
        applyStats(p);
        r.dirty = true;
    }

    /** 힐러 회복량 배율 (지혜). */
    public static float healPower(LivingEntity healer) {
        EntityAttributeInstance i = healer.getAttributeInstance(GearAttributes.HEAL_POWER);
        float g = healer instanceof PlayerEntity pl && isGrand(pl) ? GRAND_HEAL : 0f;
        return 1f + g + (i == null ? 0f : (float) i.getValue());
    }

    // ================================================================ 퀘스트 진행
    @Nullable
    public static JobQuests.Quest active(PlayerEntity p) {
        RpgData r = rpg(p);
        return r.quest.isEmpty() ? null : JobQuests.ALL.get(r.quest);
    }

    static boolean matches(String target, LivingEntity e) {
        if (target.equals("#hostile")) return e instanceof Monster;
        if (target.equals("#undead")) return e.getGroup() == EntityGroup.UNDEAD;
        return Registries.ENTITY_TYPE.getId(e.getType()).toString().equals(target);
    }

    static void questKill(ServerPlayerEntity p, LivingEntity dead) {
        JobQuests.Quest q = active(p);
        if (q == null) return;
        boolean night = p.getWorld().isNight();
        for (int i = 0; i < q.objectives().size(); i++) {
            JobQuests.Objective o = q.objectives().get(i);
            if (o.type() == JobQuests.Type.KILL || (o.type() == JobQuests.Type.KILL_NIGHT && night)) {
                if (matches(o.target(), dead)) progress(p, q, i, 1);
            }
        }
    }

    static void questAmount(ServerPlayerEntity p, JobQuests.Type type, float amount) {
        JobQuests.Quest q = active(p);
        if (q == null) return;
        for (int i = 0; i < q.objectives().size(); i++) {
            if (q.objectives().get(i).type() == type) progress(p, q, i, amount);
        }
    }

    static final float[] FRAC = new float[1];

    static void progress(ServerPlayerEntity p, JobQuests.Quest q, int i, float amount) {
        RpgData r = rpg(p);
        JobQuests.Objective o = q.objectives().get(i);
        int before = r.progress[i];
        if (before >= o.count()) return;
        // 소수 (회복량 등) 는 반올림해서 쌓음
        int add = Math.max(1, Math.round(amount));
        r.progress[i] = Math.min(o.count(), before + add);
        r.dirty = true;
        if (r.progress[i] >= o.count()) {
            p.sendMessage(Text.translatable("rpg.abilitywar.objective_done").formatted(Formatting.YELLOW), true);
            p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.8f, 1.6f);
        }
    }

    /** 회복 (힐러 계열이 회복시켰을 때). */
    public static void onHeal(@Nullable LivingEntity healer, float amount) {
        if (healer instanceof ServerPlayerEntity sp && amount > 0.05f) questAmount(sp, JobQuests.Type.HEAL, amount);
    }

    public static void onBuff(@Nullable LivingEntity caster, int count) {
        if (caster instanceof ServerPlayerEntity sp) questAmount(sp, JobQuests.Type.BUFF, count);
    }

    /** 받은 피해 / 막은 피해 (탱커 퀘스트). */
    public static void onTanked(LivingEntity victim, float amount) {
        if (victim instanceof ServerPlayerEntity sp && amount > 0.05f) questAmount(sp, JobQuests.Type.TANK, amount);
    }

    public static int count(ServerPlayerEntity p, String itemId) {
        Item it = Registries.ITEM.get(new Identifier(itemId));
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(it)) n += s.getCount();
        }
        return n;
    }

    public static boolean done(ServerPlayerEntity p, JobQuests.Quest q) {
        RpgData r = rpg(p);
        for (int i = 0; i < q.objectives().size(); i++) {
            JobQuests.Objective o = q.objectives().get(i);
            int have = o.type() == JobQuests.Type.ITEM ? count(p, o.target()) : r.progress[i];
            if (have < o.count()) return false;
        }
        return true;
    }

    /** @return 실패 이유 번역 키, 성공이면 null. */
    @Nullable
    public static String accept(ServerPlayerEntity p, String questId, String giver) {
        JobQuests.Quest q = JobQuests.ALL.get(questId);
        if (q == null || !q.giver().equals(giver)) return "rpg.abilitywar.err.no_quest";
        RpgData r = rpg(p);
        String cur = ((AWPlayer) p).aw$getAbilityKey();
        var rules = p.getWorld().getGameRules();
        String err = JobQuests.check(q, cur, r.baseClass, r.level, r.grand, r.quest, rules.getInt(AWGameRules.JOB_LEVEL), rules.getInt(AWGameRules.GRAND_LEVEL));
        if (err != null) return err;
        r.quest = q.id();
        java.util.Arrays.fill(r.progress, 0);
        r.dirty = true;
        p.sendMessage(Text.translatable("rpg.abilitywar.quest_accepted", Text.translatable("rpg.abilitywar.quest." + q.id())).formatted(Formatting.AQUA), false);
        p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1f, 1f);
        return null;
    }

    /** 관리자: 3차 전직 켜기/끄기. */
    public static void setGrand(ServerPlayerEntity p, boolean on) {
        RpgData r = rpg(p);
        r.grand = on;
        r.dirty = true;
        applyStats(p);
    }

    /** 3차 전직을 했고 지금 2차 직업 (효과가 켜짐). */
    public static boolean isGrand(PlayerEntity p) {
        return rpg(p).grand && ClassTree.tier(((AWPlayer) p).aw$getAbilityKey()) == 2;
    }

    /** 직업 이름 (3차면 "대" 를 붙임). */
    public static Text className(String key, boolean grand) {
        if (key.isEmpty()) return Text.translatable("rpg.abilitywar.hud.no_class");
        return grand && ClassTree.tier(key) == 2 ? Text.translatable("ability.abilitywar." + key + ".grand") : Text.translatable("ability.abilitywar." + key);
    }

    @Nullable
    public static String turnIn(ServerPlayerEntity p) {
        JobQuests.Quest q = active(p);
        if (q == null) return "rpg.abilitywar.err.no_quest";
        if (!done(p, q)) return "rpg.abilitywar.err.not_done";
        // 아이템 바치기
        for (JobQuests.Objective o : q.objectives()) {
            if (o.type() != JobQuests.Type.ITEM) continue;
            Item it = Registries.ITEM.get(new Identifier(o.target()));
            int left = o.count();
            for (int i = 0; i < p.getInventory().size() && left > 0; i++) {
                ItemStack s = p.getInventory().getStack(i);
                if (!s.isOf(it)) continue;
                int take = Math.min(left, s.getCount());
                s.decrement(take);
                left -= take;
            }
        }
        RpgData r = rpg(p);
        r.completed.add(q.id());
        r.quest = "";
        java.util.Arrays.fill(r.progress, 0);
        GearGrade rewardGrade;
        String titleKey;
        Text cls;
        switch (q.kind()) {
            case GRAND -> {
                r.grand = true;
                rewardGrade = GearGrade.LEGENDARY;
                titleKey = "rpg.abilitywar.grand_title";
                cls = className(q.reward(), true);
                applyStats(p);
            }
            case KING -> {
                AbilityManager.setAbility(p, Abilities.byString(q.reward()));
                rewardGrade = GearGrade.HERO;
                titleKey = "rpg.abilitywar.king_title";
                cls = className(q.reward(), r.grand);
            }
            default -> {
                AbilityManager.setAbility(p, Abilities.byString(q.reward()));
                r.baseClass = q.base();
                rewardGrade = GearGrade.RARE;
                titleKey = "rpg.abilitywar.advanced_title";
                cls = className(q.reward(), false);
            }
        }
        addXp(p, q.xp());
        // 보상 무기
        var list = GearItems.WEAPONS.get(rewardGrade);
        if (list != null && !list.isEmpty()) {
            ItemStack st = new ItemStack(list.get(p.getRandom().nextInt(list.size())));
            if (!p.getInventory().insertStack(st)) p.dropItem(st, false);
        }
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable(titleKey).formatted(Formatting.GOLD)));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(cls.copy().formatted(Formatting.YELLOW)));
        NbtCompound n = new NbtCompound();
        n.putInt("e", p.getId());
        n.putInt("l", q.kind() == JobQuests.Kind.GRAND ? -2 : -1);
        AWNet.fx(p.getServerWorld(), p.getPos(), "level_up", n);
        p.getServer().getPlayerManager().broadcast(Text.translatable("rpg.abilitywar.advanced_broadcast", p.getDisplayName(), cls).formatted(Formatting.GOLD), false);
        r.dirty = true;
        return null;
    }

    public static void abandon(ServerPlayerEntity p) {
        RpgData r = rpg(p);
        if (r.quest.isEmpty()) return;
        r.quest = "";
        java.util.Arrays.fill(r.progress, 0);
        r.dirty = true;
        p.sendMessage(Text.translatable("rpg.abilitywar.quest_abandoned").formatted(Formatting.GRAY), false);
    }

    /** 관리자: 진행 중 퀘스트 목표를 모두 채움 (아이템은 제외). */
    public static void forceProgress(ServerPlayerEntity p) {
        JobQuests.Quest q = active(p);
        if (q == null) return;
        RpgData r = rpg(p);
        for (int i = 0; i < q.objectives().size(); i++) r.progress[i] = q.objectives().get(i).count();
        r.dirty = true;
    }

    // ================================================================ 접속 / 틱 / 동기화

    public static void onJoin(ServerPlayerEntity p) {
        RpgData r = rpg(p);
        String key = ((AWPlayer) p).aw$getAbilityKey();
        if (r.baseClass.isEmpty() && !key.isEmpty()) r.baseClass = ClassTree.baseOf(key);
        applyStats(p);
        r.dirty = true;
    }

    /** 직업을 바꿈 (명령어/선택 화면): 1차 직업도 맞춤. */
    public static void onAbilitySet(ServerPlayerEntity p, String key) {
        RpgData r = rpg(p);
        p.getServer().execute(() -> applyStats(p));
        String base = ClassTree.baseOf(key);
        if (!base.isEmpty()) r.baseClass = base;
        JobQuests.Quest q = JobQuests.ALL.get(r.quest);
        if (q != null) {
            boolean stale = switch (q.kind()) {
                case JOB -> ClassTree.tier(key) >= 2;
                case GRAND -> !key.equals(q.base());
                case KING -> key.equals(q.reward());
            };
            if (stale) {
                r.quest = "";
                java.util.Arrays.fill(r.progress, 0);
            }
        }
        r.dirty = true;
    }

    public static void tick(ServerPlayerEntity p) {
        RpgData r = rpg(p);
        ((AWPlayer) p).aw$setFlag2(AWPlayer.FLAG2_GRAND, isGrand(p));
        if (r.dirty) {
            r.dirty = false;
            sync(p);
        } else if (!r.quest.isEmpty() && p.age % 20 == 0) {
            sync(p); // 아이템 개수 변화 반영
        }
    }

    public static void sync(ServerPlayerEntity p) {
        RpgData r = rpg(p);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(r.level);
        buf.writeVarLong(r.xp);
        buf.writeVarLong(need(r.level));
        buf.writeVarInt(r.points);
        for (int v : r.stats) buf.writeVarInt(v);
        buf.writeString(r.baseClass);
        buf.writeString(r.quest);
        JobQuests.Quest q = active(p);
        int n = q == null ? 0 : q.objectives().size();
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            JobQuests.Objective o = q.objectives().get(i);
            buf.writeVarInt(o.type() == JobQuests.Type.ITEM ? count(p, o.target()) : r.progress[i]);
        }
        buf.writeVarInt(p.getWorld().getGameRules().getInt(AWGameRules.JOB_LEVEL));
        buf.writeVarInt(r.completed.size());
        for (String c : r.completed) buf.writeString(c);
        buf.writeBoolean(p.getWorld().getGameRules().getBoolean(AWGameRules.SELF_SELECT));
        buf.writeBoolean(r.grand);
        buf.writeVarInt(p.getWorld().getGameRules().getInt(AWGameRules.GRAND_LEVEL));
        if (ServerPlayNetworking.canSend(p, S2C_RPG)) ServerPlayNetworking.send(p, S2C_RPG, buf);
    }

    public static void initNet() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_RPG, (server, player, handler, buf, sender) -> {
            int action = buf.readByte();
            String arg = buf.readString(64);
            int npc = buf.readVarInt();
            server.execute(() -> {
                switch (action) {
                    case 0 -> spend(player, parseInt(arg), 1);
                    case 4 -> spend(player, parseInt(arg), 5);
                    case 1, 2 -> {
                        String giver = giverNear(player, npc);
                        if (giver == null) {
                            player.sendMessage(Text.translatable("rpg.abilitywar.err.far").formatted(Formatting.RED), true);
                            return;
                        }
                        String err;
                        int needLv;
                        if (action == 1) {
                            err = accept(player, arg, giver);
                            JobQuests.Quest q = JobQuests.ALL.get(arg);
                            var rules = player.getWorld().getGameRules();
                            needLv = q == null ? 0 : JobQuests.requiredLevel(q, rules.getInt(AWGameRules.JOB_LEVEL), rules.getInt(AWGameRules.GRAND_LEVEL));
                        } else {
                            JobQuests.Quest q = active(player);
                            err = q != null && !q.giver().equals(giver) ? "rpg.abilitywar.err.other_giver" : turnIn(player);
                            needLv = 0;
                        }
                        if (err != null) player.sendMessage(Text.translatable(err, needLv).formatted(Formatting.RED), false);
                        // 결과를 반영한 대화 화면으로 다시 열기
                        openNpc(player, npc);
                    }
                    case 3 -> abandon(player);
                    default -> {
                    }
                }
            });
        });
    }

    static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 가까이 있는 퀘스트 NPC 가 주는 퀘스트 종류 (멀거나 없으면 null). */
    @Nullable
    static String giverNear(ServerPlayerEntity p, int npc) {
        Entity e = p.getWorld().getEntityById(npc);
        return e instanceof JobMasterEntity jm && e.squaredDistanceTo(p) < 10 * 10 ? jm.giver() : null;
    }

    public static void openNpc(ServerPlayerEntity p, int npc) {
        sync(p);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(npc);
        if (ServerPlayNetworking.canSend(p, S2C_OPEN_NPC)) ServerPlayNetworking.send(p, S2C_OPEN_NPC, buf);
    }

    public static List<String> statKeys() {
        return List.of(STAT_KEYS);
    }

    private RpgManager() {
    }
}
