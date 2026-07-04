package com.example.addon.modules;

import com.example.addon.ExampleAddon;
import autismclient.modules.Module;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.StringSetting;
import autismclient.util.AutismWorldGeometry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.AutismRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SeedXRay extends Module {
    private static SeedXRay INSTANCE;

    public enum Ore {
        DIAMOND("Diamond", 0xFF00FFFF, 7, 4, 4, 7, -64, 16),
        GOLD("Gold", 0xFFFFD700, 4, 4, 9, 4, -64, 32),
        IRON("Iron", 0xFFD3D3D3, 2, 4, 9, 10, -16, 80),
        COAL("Coal", 0xFF505050, 0, 4, 17, 30, 0, 192),
        REDSTONE("Redstone", 0xFFFF0000, 5, 4, 8, 8, -64, 16),
        LAPIS("Lapis", 0xFF0000FF, 6, 4, 7, 4, -64, 64),
        EMERALD("Emerald", 0xFF00FF00, 8, 4, 1, 4, -16, 320),
        COPPER("Copper", 0xFFD2691E, 9, 4, 10, 16, -16, 112);

        public final String name;
        public final int color;
        public final int index;
        public final int step;
        public final int size;
        public final int count;
        public final int minY;
        public final int maxY;
        
        Ore(String name, int color, int index, int step, int size, int count, int minY, int maxY) {
            this.name = name;
            this.color = color;
            this.index = index;
            this.step = step;
            this.size = size;
            this.count = count;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    private final StringSetting seed = add(new StringSetting("seed", "World Seed", "0"));
    private final IntSetting chunkRange = add(new IntSetting("chunkRange", "Chunk Range", 5, 1, 16, 1));

    private final BoolSetting showDiamond = add(new BoolSetting("showDiamond", "Show Diamond", true));
    private final BoolSetting showGold = add(new BoolSetting("showGold", "Show Gold", true));
    private final BoolSetting showIron = add(new BoolSetting("showIron", "Show Iron", true));
    private final BoolSetting showCoal = add(new BoolSetting("showCoal", "Show Coal", false));
    private final BoolSetting showRedstone = add(new BoolSetting("showRedstone", "Show Redstone", false));
    private final BoolSetting showLapis = add(new BoolSetting("showLapis", "Show Lapis", false));
    private final BoolSetting showEmerald = add(new BoolSetting("showEmerald", "Show Emerald", true));
    private final BoolSetting showCopper = add(new BoolSetting("showCopper", "Show Copper", false));

    private final Map<Long, Map<Ore, List<Vec3>>> chunkRenderers = new ConcurrentHashMap<>();

    public static long chunkKey(int x, int z) {
        return ((long) z << 32) | ((long) x & 0xFFFFFFFFL);
    }

    public SeedXRay() {
        super(ExampleAddon.ID + ":seedxray", "SeedX-Ray", "Simulates and displays ore locations based on world seed.");
        INSTANCE = this;

        // Register the COLLECT_SUBMITS event globally once
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            if (INSTANCE == null || !INSTANCE.isEnabled() || MC.player == null || MC.level == null) return;

            Vec3 camera = context.levelState().cameraRenderState.pos;
            int playerChunkX = MC.player.chunkPosition().x();
            int playerChunkZ = MC.player.chunkPosition().z();
            int range = INSTANCE.chunkRange.get();

            List<RenderTask> renderTasks = new ArrayList<>();

            // Scan local chunks and submit tasks
            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    int cx = playerChunkX + dx;
                    int cz = playerChunkZ + dz;
                    long chunkKey = chunkKey(cx, cz);

                    if (!INSTANCE.chunkRenderers.containsKey(chunkKey)) {
                        LevelChunk chunk = MC.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                        if (chunk != null) {
                            INSTANCE.doMathOnChunk(chunk);
                        }
                    }

                    Map<Ore, List<Vec3>> chunkOres = INSTANCE.chunkRenderers.get(chunkKey);
                    if (chunkOres != null) {
                        for (Map.Entry<Ore, List<Vec3>> entry : chunkOres.entrySet()) {
                            Ore ore = entry.getKey();
                            if (INSTANCE.isOreEnabled(ore)) {
                                for (Vec3 pos : entry.getValue()) {
                                    BlockPos bp = BlockPos.containing(pos.x, pos.y, pos.z);
                                    if (MC.level.getBlockState(bp).isAir()) continue;

                                    AABB box = new AABB(bp).move(-camera.x, -camera.y, -camera.z);
                                    renderTasks.add(new RenderTask(box, ore.color));
                                }
                            }
                        }
                    }
                }
            }

            if (!renderTasks.isEmpty()) {
                context.submitNodeCollector().submitCustomGeometry(
                    context.poseStack(),
                    AutismRenderTypes.storageEspLinesSeeThrough(),
                    (pose, buffer) -> {
                        for (RenderTask task : renderTasks) {
                            renderBox(pose, buffer, task.box, task.color);
                        }
                    }
                );
            }
        });
    }

    private boolean isOreEnabled(Ore ore) {
        return switch (ore) {
            case DIAMOND -> showDiamond.get();
            case GOLD -> showGold.get();
            case IRON -> showIron.get();
            case COAL -> showCoal.get();
            case REDSTONE -> showRedstone.get();
            case LAPIS -> showLapis.get();
            case EMERALD -> showEmerald.get();
            case COPPER -> showCopper.get();
        };
    }

    private long getWorldSeed() {
        try {
            return Long.parseLong(seed.get().trim());
        } catch (NumberFormatException e) {
            return seed.get().hashCode();
        }
    }

    private void doMathOnChunk(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        long chunkKey = chunkKey(chunkPos.x(), chunkPos.z());
        if (chunkRenderers.containsKey(chunkKey)) return;

        long seedValue = getWorldSeed();
        int chunkX = chunkPos.x() << 4;
        int chunkZ = chunkPos.z() << 4;

        WorldgenRandom random = new WorldgenRandom(WorldgenRandom.Algorithm.XOROSHIRO.newInstance(0));
        long populationSeed = random.setDecorationSeed(seedValue, chunkX, chunkZ);

        Map<Ore, List<Vec3>> chunkOres = new HashMap<>();

        for (Ore ore : Ore.values()) {
            List<Vec3> ores = new ArrayList<>();
            random.setFeatureSeed(populationSeed, ore.index, ore.step);

            int repeat = ore.count;
            for (int i = 0; i < repeat; i++) {
                int x = random.nextInt(16) + chunkX;
                int z = random.nextInt(16) + chunkZ;
                
                int y;
                if (ore.minY >= ore.maxY) {
                    y = ore.minY;
                } else {
                    y = random.nextInt(ore.maxY - ore.minY + 1) + ore.minY;
                }
                
                BlockPos origin = new BlockPos(x, y, z);
                ores.addAll(generateNormal(MC.level, random, origin, ore.size));
            }
            if (!ores.isEmpty()) {
                chunkOres.put(ore, ores);
            }
        }
        chunkRenderers.put(chunkKey, chunkOres);
    }

    private ArrayList<Vec3> generateNormal(ClientLevel world, WorldgenRandom random, BlockPos blockPos, int veinSize) {
        float f = random.nextFloat() * 3.1415927F;
        float g = (float) veinSize / 8.0F;
        int i = Mth.ceil(((float) veinSize / 16.0F * 2.0F + 1.0F) / 2.0F);
        double d = (double) blockPos.getX() + Math.sin(f) * (double) g;
        double e = (double) blockPos.getX() - Math.sin(f) * (double) g;
        double h = (double) blockPos.getZ() + Math.cos(f) * (double) g;
        double j = (double) blockPos.getZ() - Math.cos(f) * (double) g;
        double l = (blockPos.getY() + random.nextInt(3) - 2);
        double m = (blockPos.getY() + random.nextInt(3) - 2);
        int n = blockPos.getX() - Mth.ceil(g) - i;
        int o = blockPos.getY() - 2 - i;
        int p = blockPos.getZ() - Mth.ceil(g) - i;
        int q = 2 * (Mth.ceil(g) + i);
        int r = 2 * (2 + i);

        for (int s = n; s <= n + q; ++s) {
            for (int t = p; t <= p + q; ++t) {
                if (o <= world.getHeight(Heightmap.Types.MOTION_BLOCKING, s, t)) {
                    return generateVeinPart(world, random, veinSize, d, e, h, j, l, m, n, o, p, q, r);
                }
            }
        }

        return new ArrayList<>();
    }

    private ArrayList<Vec3> generateVeinPart(ClientLevel world, WorldgenRandom random, int veinSize, double startX, double endX, double startZ, double endZ, double startY, double endY, int x, int y, int z, int size, int i) {
        BitSet bitSet = new BitSet(size * i * size);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        double[] ds = new double[veinSize * 4];

        ArrayList<Vec3> poses = new ArrayList<>();

        int n;
        double p;
        double q;
        double r;
        double s;
        for (n = 0; n < veinSize; ++n) {
            float f = (float) n / (float) veinSize;
            p = Mth.lerp(f, startX, endX);
            q = Mth.lerp(f, startY, endY);
            r = Mth.lerp(f, startZ, endZ);
            s = random.nextDouble() * (double) veinSize / 16.0D;
            double m = ((double) (Mth.sin(3.1415927F * f) + 1.0F) * s + 1.0D) / 2.0D;
            ds[n * 4] = p;
            ds[n * 4 + 1] = q;
            ds[n * 4 + 2] = r;
            ds[n * 4 + 3] = m;
        }

        for (n = 0; n < veinSize - 1; ++n) {
            if (!(ds[n * 4 + 3] <= 0.0D)) {
                for (int o = n + 1; o < veinSize; ++o) {
                    if (!(ds[o * 4 + 3] <= 0.0D)) {
                        p = ds[n * 4] - ds[o * 4];
                        q = ds[n * 4 + 1] - ds[o * 4 + 1];
                        r = ds[n * 4 + 2] - ds[o * 4 + 2];
                        s = ds[n * 4 + 3] - ds[o * 4 + 3];
                        if (s * s > p * p + q * q + r * r) {
                            if (s > 0.0D) {
                                ds[o * 4 + 3] = -1.0D;
                            } else {
                                ds[n * 4 + 3] = -1.0D;
                            }
                        }
                    }
                }
            }
        }

        for (n = 0; n < veinSize; ++n) {
            double u = ds[n * 4 + 3];
            if (!(u < 0.0D)) {
                double v = ds[n * 4];
                double w = ds[n * 4 + 1];
                double aa = ds[n * 4 + 2];
                int ab = Math.max(Mth.floor(v - u), x);
                int ac = Math.max(Mth.floor(w - u), y);
                int ad = Math.max(Mth.floor(aa - u), z);
                int ae = Math.max(Mth.floor(v + u), ab);
                int af = Math.max(Mth.floor(w + u), ac);
                int ag = Math.max(Mth.floor(aa + u), ad);

                for (int ah = ab; ah <= ae; ++ah) {
                    double ai = ((double) ah + 0.5D - v) / u;
                    if (ai * ai < 1.0D) {
                        for (int aj = ac; aj <= af; ++aj) {
                            double ak = ((double) aj + 0.5D - w) / u;
                            if (ai * ai + ak * ak < 1.0D) {
                                for (int al = ad; al <= ag; ++al) {
                                    double am = ((double) al + 0.5D - aa) / u;
                                    if (ai * ai + ak * ak + am * am < 1.0D) {
                                        int an = ah - x + (aj - y) * size + (al - z) * size * i;
                                        if (!bitSet.get(an)) {
                                            bitSet.set(an);
                                            mutable.set(ah, aj, al);
                                            if (aj >= -64 && aj < 320) {
                                                poses.add(new Vec3(ah, aj, al));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return poses;
    }

    private static void renderBox(com.mojang.blaze3d.vertex.PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, AABB box, int color) {
        double x1 = box.minX;
        double y1 = box.minY;
        double z1 = box.minZ;
        double x2 = box.maxX;
        double y2 = box.maxY;
        double z2 = box.maxZ;
        final float width = 2.0f;
        AutismWorldGeometry.line(pose, buffer, x1, y1, z1, x2, y1, z1, color, width);
        AutismWorldGeometry.line(pose, buffer, x2, y1, z1, x2, y1, z2, color, width);
        AutismWorldGeometry.line(pose, buffer, x2, y1, z2, x1, y1, z2, color, width);
        AutismWorldGeometry.line(pose, buffer, x1, y1, z2, x1, y1, z1, color, width);
        AutismWorldGeometry.line(pose, buffer, x1, y2, z1, x2, y2, z1, color, width);
        AutismWorldGeometry.line(pose, buffer, x2, y2, z1, x2, y2, z2, color, width);
        AutismWorldGeometry.line(pose, buffer, x2, y2, z2, x1, y2, z2, color, width);
        AutismWorldGeometry.line(pose, buffer, x1, y2, z2, x1, y2, z1, color, width);
        AutismWorldGeometry.line(pose, buffer, x1, y1, z1, x1, y2, z1, color, width);
        AutismWorldGeometry.line(pose, buffer, x2, y1, z1, x2, y2, z1, color, width);
        AutismWorldGeometry.line(pose, buffer, x2, y1, z2, x2, y2, z2, color, width);
        AutismWorldGeometry.line(pose, buffer, x1, y1, z2, x1, y2, z2, color, width);
    }

    @Override
    public void onDisable() {
        chunkRenderers.clear();
    }

    private static class RenderTask {
        public final AABB box;
        public final int color;

        public RenderTask(AABB box, int color) {
            this.box = box;
            this.color = color;
        }
    }
}
