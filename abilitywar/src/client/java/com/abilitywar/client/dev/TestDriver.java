package com.abilitywar.client.dev;

import com.abilitywar.AbilityWar;
import com.abilitywar.ability.SkillSlot;
import com.abilitywar.client.input.InputHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 개발용 자동 테스트 스크립트 실행기. -Dabilitywar.test=파일경로 일 때만 동작.
 * 명령: wait N | cmd /... | skill N | attack | block on/off | persp 0/1/2 | look yaw pitch | shot 이름 | quit | forward N | sprint on/off | sneak on/off | jump
 */
public final class TestDriver {
    private static List<String> lines;
    private static int idx;
    private static int waitTicks;
    private static boolean started;
    private static int worldTicks;
    public static boolean forward, sprint, sneak, jump;

    private static final java.util.List<String> CHAT = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static String waitChat;

    public static void init() {
        String path = System.getProperty("abilitywar.test");
        if (path == null) return;
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((msg, overlay) -> CHAT.add(msg.getString()));
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.CHAT.register((msg, signed, sender, params, ts) -> CHAT.add(msg.getString()));
        try {
            lines = new ArrayList<>(Files.readAllLines(new File(path).toPath(), StandardCharsets.UTF_8));
            AbilityWar.LOG.info("[TestDriver] loaded {} lines", lines.size());
        } catch (Exception e) {
            AbilityWar.LOG.error("[TestDriver] failed", e);
        }
    }

    public static boolean active() {
        return lines != null;
    }

    public static void tick(MinecraftClient mc) {
        if (lines == null) return;
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null && worldTicks > 0 && !(mc.currentScreen instanceof com.abilitywar.client.screen.GrimoireScreen)) mc.setScreen(null);
        worldTicks++;
        if (!started) {
            if (worldTicks < 60) return;
            started = true;
        }
        mc.options.forwardKey.setPressed(forward);
        mc.options.sprintKey.setPressed(sprint);
        mc.options.sneakKey.setPressed(sneak);
        mc.options.jumpKey.setPressed(jump);
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (waitChat != null) {
            boolean found = false;
            for (String m : CHAT) if (m.contains(waitChat)) found = true;
            if (!found) return;
            CHAT.clear();
            waitChat = null;
        }
        while (idx < lines.size() && waitTicks == 0 && waitChat == null) {
            String line = lines.get(idx++).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] a = line.split("\\s+", 2);
            String arg = a.length > 1 ? a[1] : "";
            AbilityWar.LOG.info("[TestDriver] {}", line);
            switch (a[0]) {
                case "wait" -> waitTicks = Integer.parseInt(arg);
                case "cmd" -> mc.player.networkHandler.sendChatCommand(arg.startsWith("/") ? arg.substring(1) : arg);
                case "skill" -> InputHandler.useSkill(SkillSlot.VALUES[Integer.parseInt(arg)]);
                case "attack" -> InputHandler.onAttack();
                case "orbitcine" -> com.abilitywar.client.camera.CameraController.debugOverCinematic = arg.equals("on");
                case "hold" -> InputHandler.testHold(SkillSlot.VALUES[Integer.parseInt(arg)]);
                case "dash" -> {
                    // dash forward|back|left|right : 이동키를 누른 채로 대시
                    mc.player.input.movementForward = arg.equals("forward") ? 1 : arg.equals("back") ? -1 : 0;
                    mc.player.input.movementSideways = arg.equals("left") ? 1 : arg.equals("right") ? -1 : 0;
                    com.abilitywar.client.input.DashController.tryDash();
                }
                case "block" -> InputHandler.testBlock(arg.equals("on"));
                case "persp" -> mc.options.setPerspective(Perspective.values()[Integer.parseInt(arg)]);
                case "look" -> {
                    String[] p = arg.split("\\s+");
                    mc.player.setYaw(Float.parseFloat(p[0]));
                    mc.player.setPitch(Float.parseFloat(p[1]));
                    mc.player.prevYaw = mc.player.getYaw();
                    mc.player.prevPitch = mc.player.getPitch();
                }
                case "shot" -> ScreenshotRecorder.saveScreenshot(mc.runDirectory, arg + ".png", mc.getFramebuffer(), t -> {});
                case "forward" -> forward = arg.equals("on");
                case "sprint" -> sprint = arg.equals("on");
                case "sneak" -> sneak = arg.equals("on");
                case "jump" -> jump = arg.equals("on");
                case "hud" -> mc.options.hudHidden = arg.equals("off");
                case "orbit" -> {
                    if (arg.equals("off")) com.abilitywar.client.camera.CameraController.debugOrbit = null;
                    else {
                        String[] p = arg.split("\\s+");
                        float[] o = new float[p.length];
                        for (int i = 0; i < p.length; i++) o[i] = Float.parseFloat(p[i]);
                        com.abilitywar.client.camera.CameraController.debugOrbit = o;
                    }
                }
                case "waitchat" -> {
                    waitChat = arg;
                    waitTicks = 0;
                    return;
                }
                case "say" -> mc.player.networkHandler.sendChatMessage(arg);
                // 흑마법서 화면 테스트: 열기 / 칸 빠른 이동 / 영혼 동작 / 닫기
                case "grimoire" -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(com.abilitywar.net.AWNet.C2S_GRIMOIRE,
                        net.fabricmc.fabric.api.networking.v1.PacketByteBufs.empty());
                case "quickmove" -> {
                    if (mc.player.currentScreenHandler != null && mc.interactionManager != null)
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, Integer.parseInt(arg), 0,
                                net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, mc.player);
                }
                case "soulaction" -> {
                    String[] p2 = arg.split(" ");
                    net.minecraft.network.PacketByteBuf b = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                    b.writeByte(Integer.parseInt(p2[0]));
                    b.writeVarInt(Integer.parseInt(p2[1]));
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(com.abilitywar.net.AWNet.C2S_SOUL_ACTION, b);
                }
                case "close" -> {
                    if (mc.currentScreen != null) mc.currentScreen.close();
                }
                case "clearchat" -> mc.inGameHud.getChatHud().clear(false);
                case "bind" -> {
                    String[] p = arg.split("\\s+");
                    net.minecraft.client.option.KeyBinding kb = switch (p[0]) {
                        case "skill1" -> InputHandler.SKILL1;
                        case "ultimate" -> InputHandler.ULTIMATE;
                        default -> InputHandler.PASSIVE;
                    };
                    mc.options.setKeyCode(kb, net.minecraft.client.util.InputUtil.fromTranslationKey(p[1]));
                    net.minecraft.client.option.KeyBinding.updateKeysByCode();
                }
                case "quit" -> mc.scheduleStop();
                default -> AbilityWar.LOG.warn("[TestDriver] unknown {}", line);
            }
        }
    }

    private TestDriver() {
    }
}
