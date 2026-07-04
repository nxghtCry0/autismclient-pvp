package com.example.addon.modules;

import com.github.cubiomes.Cubiomes;
import com.github.cubiomes.Generator;
import com.github.cubiomes.OreConfig;
import com.github.cubiomes.Pos3;
import com.github.cubiomes.Pos3List;
import com.github.cubiomes.SurfaceNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.*;

public final class SeedMapperHelper {
    public static long getSeedValue(long defaultSeed) {
        try {
            Class<?> configsClass = Class.forName("dev.xpple.seedmapper.config.Configs");
            java.lang.reflect.Field seedField = configsClass.getField("Seed");
            Object seedConfig = seedField.get(null);
            if (seedConfig != null) {
                java.lang.reflect.Method seedMethod = seedConfig.getClass().getMethod("seed");
                return (long) seedMethod.invoke(seedConfig);
            }
        } catch (Throwable ignored) {}
        return defaultSeed;
    }

    public static int getCubiomesVersion() {
        String versionStr = net.minecraft.SharedConstants.getCurrentVersion().name();
        if (versionStr.startsWith("26.2")) {
            return Cubiomes.MC_1_21_11();
        }
        if (versionStr.startsWith("26.1")) {
            return Cubiomes.MC_1_21_11();
        }
        if (versionStr.startsWith("1.21.4")) {
            return Cubiomes.MC_1_21_4();
        }
        if (versionStr.startsWith("1.21.3")) {
            return Cubiomes.MC_1_21_3();
        }
        if (versionStr.startsWith("1.21")) {
            return Cubiomes.MC_1_21_1();
        }
        if (versionStr.startsWith("1.20")) {
            return Cubiomes.MC_1_20();
        }
        return Cubiomes.MC_1_21_11();
    }

    public static Map<SeedXRay.Ore, List<Vec3>> generateOres(int chunkX, int chunkZ, long seedValue, int version, int dimension) {
        int generatorFlags = 0;
        try {
            Class<?> managerClass = Class.forName("dev.xpple.seedmapper.world.WorldPresetManager");
            java.lang.reflect.Method activePresetMethod = managerClass.getMethod("activePreset");
            Object activePreset = activePresetMethod.invoke(null);
            if (activePreset != null) {
                java.lang.reflect.Method generatorFlagsMethod = activePreset.getClass().getMethod("generatorFlags");
                generatorFlags = (int) generatorFlagsMethod.invoke(activePreset);
            }
        } catch (Throwable ignored) {}

        Map<SeedXRay.Ore, List<Vec3>> chunkOres = new HashMap<>();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment generator = Generator.allocate(arena);
            Cubiomes.setupGenerator(generator, version, generatorFlags);
            Cubiomes.applySeed(generator, dimension, seedValue);
            MemorySegment surfaceNoise = SurfaceNoise.allocate(arena);
            Cubiomes.initSurfaceNoise(surfaceNoise, dimension, seedValue);

            List<Integer> biomes;
            if (version <= Cubiomes.MC_1_17()) {
                biomes = List.of(Cubiomes.getBiomeForOreGen(generator, chunkX, chunkZ, 0));
            } else {
                biomes = java.util.stream.IntStream.of(-30, 64, 120)
                    .map(y -> Cubiomes.getBiomeForOreGen(generator, chunkX, chunkZ, y))
                    .boxed()
                    .toList();
            }

            Map<BlockPos, Integer> generatedOres = new HashMap<>();
            List<MemorySegment> configs = new ArrayList<>();
            int oreNum = Cubiomes.ORE_NUM();
            for (int oreType = 0; oreType < oreNum; oreType++) {
                boolean viable = false;
                for (int biome : biomes) {
                    if (Cubiomes.isViableOreBiome(version, oreType, biome) != 0) {
                        viable = true;
                        break;
                    }
                }
                if (!viable) continue;

                MemorySegment oreConfig = OreConfig.allocate(arena);
                if (Cubiomes.getOreConfig(oreType, version, biomes.get(0), oreConfig) != 0) {
                    configs.add(oreConfig);
                }
            }

            configs.sort(Comparator.comparingInt(OreConfig::index));

            for (MemorySegment oreConfig : configs) {
                int oreBlock = OreConfig.oreBlock(oreConfig);
                int numReplaceBlocks = OreConfig.numReplaceBlocks(oreConfig);
                MemorySegment replaceBlocks = OreConfig.replaceBlocks(oreConfig);
                MemorySegment pos3List = Cubiomes.generateOres(arena, generator, surfaceNoise, oreConfig, chunkX, chunkZ);
                int size = Pos3List.size(pos3List);
                MemorySegment pos3s = Pos3List.pos3s(pos3List);
                try {
                    for (int i = 0; i < size; i++) {
                        MemorySegment pos3 = Pos3.asSlice(pos3s, i);
                        BlockPos pos = new BlockPos(Pos3.x(pos3), Pos3.y(pos3), Pos3.z(pos3));
                        
                        Integer previouslyGeneratedOre = generatedOres.get(pos);
                        if (previouslyGeneratedOre != null) {
                            boolean contains = false;
                            for (int j = 0; j < numReplaceBlocks; j++) {
                                int replaceBlock = replaceBlocks.getAtIndex(Cubiomes.C_INT, j);
                                if (replaceBlock == previouslyGeneratedOre) {
                                    contains = true;
                                    break;
                                }
                            }
                            if (!contains) {
                                continue;
                            }
                        }
                        generatedOres.put(pos, oreBlock);
                    }
                } finally {
                    Cubiomes.freePos3List(pos3List);
                }
            }

            for (Map.Entry<BlockPos, Integer> entry : generatedOres.entrySet()) {
                BlockPos pos = entry.getKey();
                int blockId = entry.getValue();
                SeedXRay.Ore matchedOre = findOreByCubiomesBlock(blockId);
                if (matchedOre != null) {
                    chunkOres.computeIfAbsent(matchedOre, k -> new ArrayList<>()).add(new Vec3(pos.getX(), pos.getY(), pos.getZ()));
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return chunkOres;
    }

    private static SeedXRay.Ore findOreByCubiomesBlock(int blockId) {
        if (blockId == Cubiomes.DIAMOND_ORE()) return SeedXRay.Ore.DIAMOND;
        if (blockId == Cubiomes.GOLD_ORE()) return SeedXRay.Ore.GOLD;
        if (blockId == Cubiomes.IRON_ORE()) return SeedXRay.Ore.IRON;
        if (blockId == Cubiomes.COAL_ORE()) return SeedXRay.Ore.COAL;
        if (blockId == Cubiomes.REDSTONE_ORE()) return SeedXRay.Ore.REDSTONE;
        if (blockId == Cubiomes.LAPIS_ORE()) return SeedXRay.Ore.LAPIS;
        if (blockId == Cubiomes.EMERALD_ORE()) return SeedXRay.Ore.EMERALD;
        if (blockId == Cubiomes.COPPER_ORE()) return SeedXRay.Ore.COPPER;
        return null;
    }

    public static int getNetherDim() {
        return Cubiomes.DIM_NETHER();
    }

    public static int getEndDim() {
        return Cubiomes.DIM_END();
    }

    public static int getOverworldDim() {
        return Cubiomes.DIM_OVERWORLD();
    }
}
