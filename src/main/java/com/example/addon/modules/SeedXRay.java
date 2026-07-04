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
        DIAMOND("Diamond", 0xFF00FFFF),
        GOLD("Gold", 0xFFFFD700),
        IRON("Iron", 0xFFD3D3D3),
        COAL("Coal", 0xFF505050),
        REDSTONE("Redstone", 0xFFFF0000),
        LAPIS("Lapis", 0xFF0000FF),
        EMERALD("Emerald", 0xFF00FF00),
        COPPER("Copper", 0xFFD2691E);

        public final String name;
        public final int color;

        Ore(String name, int color) {
            this.name = name;
            this.color = color;
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
    private final Set<Long> processingChunks = ConcurrentHashMap.newKeySet();

    public static long chunkKey(int x, int z) {
        return ((long) z << 32) | ((long) x & 0xFFFFFFFFL);
    }

    public SeedXRay() {
        super(ExampleAddon.ID + ":seedxray", "SeedX-Ray", "Simulates and displays ore locations based on world seed.");
        INSTANCE = this;

        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            if (INSTANCE == null || !INSTANCE.isEnabled() || MC.player == null || MC.level == null) return;

            Vec3 camera = context.levelState().cameraRenderState.pos;
            int playerChunkX = MC.player.chunkPosition().x();
            int playerChunkZ = MC.player.chunkPosition().z();
            int range = INSTANCE.chunkRange.get();

            List<RenderTask> renderTasks = new ArrayList<>();

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
                                    if (!INSTANCE.isValidOreTarget(MC.level.getBlockState(bp), ore)) continue;

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
        if (seed.get().equals("0") || seed.get().trim().isEmpty()) {
            if (MC.getSingleplayerServer() != null) {
                var worldGenSettings = MC.getSingleplayerServer().getWorldGenSettings();
                if (worldGenSettings != null) {
                    return worldGenSettings.options().seed();
                }
            }
        }
        try {
            return Long.parseLong(seed.get().trim());
        } catch (NumberFormatException e) {
            return seed.get().hashCode();
        }
    }

    private void doMathOnChunk(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        long chunkKey = chunkKey(chunkPos.x(), chunkPos.z());
        if (chunkRenderers.containsKey(chunkKey) || !processingChunks.add(chunkKey)) return;

        long seedValue = SeedMapperHelper.getSeedValue(getWorldSeed());
        int version = SeedMapperHelper.getCubiomesVersion();
        int dimension = getCubiomesDimension(SeedMapperHelper.getNetherDim(), SeedMapperHelper.getEndDim(), SeedMapperHelper.getOverworldDim());
        int cx = chunkPos.x();
        int cz = chunkPos.z();

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                var ores = SeedMapperHelper.generateOres(cx, cz, seedValue, version, dimension);
                chunkRenderers.put(chunkKey, ores);
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                processingChunks.remove(chunkKey);
            }
        });
    }

    private int getCubiomesDimension(int nether, int end, int overworld) {
        if (MC.level == null) return overworld;
        var key = MC.level.dimension();
        if (key == net.minecraft.world.level.Level.NETHER) {
            return nether;
        } else if (key == net.minecraft.world.level.Level.END) {
            return end;
        }
        return overworld;
    }

    private void doJavaMath(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        long seedValue = getWorldSeed();
        int chunkX = chunkPos.x() << 4;
        int chunkZ = chunkPos.z() << 4;

        WorldgenRandom random = new WorldgenRandom(WorldgenRandom.Algorithm.XOROSHIRO.newInstance(0));
        long populationSeed = random.setDecorationSeed(seedValue, chunkX, chunkZ);

        Map<Ore, List<Vec3>> chunkOres = new HashMap<>();

        for (Ore ore : Ore.values()) {
            List<Vec3> ores = new ArrayList<>();
            int index = getOreIndex(ore);
            int step = getOreStep(ore);
            int size = getOreSize(ore);
            int count = getOreCount(ore);

            random.setFeatureSeed(populationSeed, index, step);

            for (int i = 0; i < count; i++) {
                int x = random.nextInt(16) + chunkX;
                int z = random.nextInt(16) + chunkZ;
                int y = sampleHeight(random, ore);
                
                BlockPos origin = new BlockPos(x, y, z);
                ores.addAll(generateNormal(MC.level, random, origin, size));
            }
            if (!ores.isEmpty()) {
                chunkOres.put(ore, ores);
            }
        }
        chunkRenderers.put(chunkKey(chunkPos.x(), chunkPos.z()), chunkOres);
    }

    private int getOreIndex(Ore ore) {
        return switch (ore) {
            case COAL -> 0;
            case IRON -> 2;
            case GOLD -> 4;
            case REDSTONE -> 5;
            case LAPIS -> 6;
            case DIAMOND -> 7;
            case EMERALD -> 8;
            case COPPER -> 9;
        };
    }

    private int getOreStep(Ore ore) {
        return 4;
    }

    private int getOreSize(Ore ore) {
        return switch (ore) {
            case COAL -> 17;
            case IRON -> 9;
            case GOLD -> 9;
            case REDSTONE -> 8;
            case LAPIS -> 7;
            case DIAMOND -> 4;
            case EMERALD -> 1;
            case COPPER -> 10;
        };
    }

    private int getOreCount(Ore ore) {
        return switch (ore) {
            case COAL -> 30;
            case IRON -> 10;
            case GOLD -> 4;
            case REDSTONE -> 8;
            case LAPIS -> 4;
            case DIAMOND -> 7;
            case EMERALD -> 4;
            case COPPER -> 16;
        };
    }

    private int sampleHeight(WorldgenRandom random, Ore ore) {
        return switch (ore) {
            case DIAMOND -> {
                int y = 0 + random.nextInt(81) - random.nextInt(81);
                yield Math.max(-64, Math.min(16, y));
            }
            case GOLD -> {
                yield random.nextInt(32 - (-64) + 1) - 64;
            }
            case IRON -> {
                int y = 16 + random.nextInt(41) - random.nextInt(41);
                yield Math.max(-64, Math.min(320, y));
            }
            case COAL -> {
                yield random.nextInt(192 + 1);
            }
            case REDSTONE -> {
                int y = 0 + random.nextInt(81) - random.nextInt(81);
                yield Math.max(-64, Math.min(-32, y));
            }
            case LAPIS -> {
                int y = 32 + random.nextInt(65) - random.nextInt(65);
                yield Math.max(-64, Math.min(64, y));
            }
            case EMERALD -> {
                int y = 232 + random.nextInt(249) - random.nextInt(249);
                yield Math.max(-16, Math.min(320, y));
            }
            case COPPER -> {
                int y = 48 + random.nextInt(65) - random.nextInt(65);
                yield Math.max(-16, Math.min(112, y));
            }
        };
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

    private boolean isValidOreTarget(net.minecraft.world.level.block.state.BlockState state, Ore ore) {
        net.minecraft.world.level.block.Block block = state.getBlock();
        String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath().toLowerCase();
        
        if (name.contains(ore.name.toLowerCase())) {
            return true;
        }

        return block == net.minecraft.world.level.block.Blocks.STONE
            || block == net.minecraft.world.level.block.Blocks.DEEPSLATE
            || block == net.minecraft.world.level.block.Blocks.TUFF
            || block == net.minecraft.world.level.block.Blocks.ANDESITE
            || block == net.minecraft.world.level.block.Blocks.DIORITE
            || block == net.minecraft.world.level.block.Blocks.GRANITE
            || block == net.minecraft.world.level.block.Blocks.NETHERRACK;
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
