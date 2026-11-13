package com.abilitywar.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PillarBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * 자유 모양 (나무 · 바위 · 버섯 · 수정): 블록을 모아 두었다가 프림 하나로 설계도에 넣음.
 * 줄기 (통나무) 는 나뭇잎이 덮어쓰지 못함.
 */
public final class Shape {
    final Map<Long, BlockState> blocks = new LinkedHashMap<>();
    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    public Shape set(int x, int y, int z, BlockState s) {
        if (s == null) return this;
        blocks.put(BlockPos.asLong(x, y, z), s);
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
        maxZ = Math.max(maxZ, z);
        return this;
    }

    public BlockState get(int x, int y, int z) {
        return blocks.get(BlockPos.asLong(x, y, z));
    }

    /** 비었거나 나뭇잎 · 덩굴 같은 것만 덮어씀 (줄기 · 돌은 그대로). */
    public Shape soft(int x, int y, int z, BlockState s) {
        BlockState cur = get(x, y, z);
        if (cur != null && (cur.isIn(BlockTags.LOGS) || !cur.isIn(BlockTags.LEAVES) && !cur.isReplaceable())) return this;
        return set(x, y, z, s);
    }

    /** 타원체 (반지름 rx, ry, rz). holes = 가장자리에서 빠지는 비율. */
    public Shape ball(double cx, double cy, double cz, double rx, double ry, double rz, BlockState s, Random r, double holes) {
        for (int x = (int) Math.floor(cx - rx); x <= (int) Math.ceil(cx + rx); x++)
            for (int y = (int) Math.floor(cy - ry); y <= (int) Math.ceil(cy + ry); y++)
                for (int z = (int) Math.floor(cz - rz); z <= (int) Math.ceil(cz + rz); z++) {
                    double dx = (x - cx) / rx, dy = (y - cy) / ry, dz = (z - cz) / rz;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d > 1.0) continue;
                    if (d > 0.62 && r.nextDouble() < holes) continue;
                    soft(x, y, z, s);
                }
        return this;
    }

    /** 통나무 줄 (방향에 맞춰 축). */
    public Shape line(double x0, double y0, double z0, double x1, double y1, double z1, Block log) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        int n = (int) Math.ceil(Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)))) + 1;
        Direction.Axis axis = Math.abs(dy) >= Math.max(Math.abs(dx), Math.abs(dz)) ? Direction.Axis.Y : Math.abs(dx) > Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z;
        BlockState st = log.getDefaultState();
        if (st.contains(PillarBlock.AXIS)) st = st.with(PillarBlock.AXIS, axis);
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : (double) i / n;
            set((int) Math.round(x0 + dx * t), (int) Math.round(y0 + dy * t), (int) Math.round(z0 + dz * t), st);
        }
        return this;
    }

    /** 아무 블록으로 선 (거미줄 · 사슬 등). onlyEmpty 면 빈 곳만. */
    public Shape strand(double x0, double y0, double z0, double x1, double y1, double z1, BlockState s, boolean onlyEmpty) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        int n = (int) Math.ceil(Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)))) + 1;
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            int x = (int) Math.round(x0 + dx * t), y = (int) Math.round(y0 + dy * t), z = (int) Math.round(z0 + dz * t);
            if (!onlyEmpty || get(x, y, z) == null) set(x, y, z, s);
        }
        return this;
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public void addTo(Blueprint b) {
        if (blocks.isEmpty()) return;
        long[] pos = new long[blocks.size()];
        BlockState[] st = new BlockState[blocks.size()];
        int i = 0;
        for (Map.Entry<Long, BlockState> e : blocks.entrySet()) {
            pos[i] = e.getKey();
            st[i++] = e.getValue();
        }
        b.add(new Cluster(new BlockBox(minX, minY, minZ, maxX, maxY, maxZ), pos, st));
    }

    record Cluster(BlockBox box, long[] pos, BlockState[] st) implements Blueprint.Prim {
        @Override
        public void place(Blueprint.Placer p, BlockBox clip) {
            for (int i = 0; i < pos.length; i++) {
                int x = BlockPos.unpackLongX(pos[i]), y = BlockPos.unpackLongY(pos[i]), z = BlockPos.unpackLongZ(pos[i]);
                if (x < clip.getMinX() || x > clip.getMaxX() || z < clip.getMinZ() || z > clip.getMaxZ() || y < clip.getMinY() || y > clip.getMaxY()) continue;
                p.set(x, y, z, st[i]);
            }
        }
    }
}
