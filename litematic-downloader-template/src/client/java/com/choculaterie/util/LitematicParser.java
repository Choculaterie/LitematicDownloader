package com.choculaterie.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class LitematicParser {

    public static class BlockCount {
        public final String blockId;
        public final int count;

        public BlockCount(String blockId, int count) {
            this.blockId = blockId;
            this.count = count;
        }
    }

    public static class BlockData {
        public final int x, y, z;
        public final String blockId;
        public final Map<String, String> properties;

        public BlockData(int x, int y, int z, String blockId, Map<String, String> properties) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockId = blockId;
            this.properties = properties;
        }
    }

    public static final int MAX_PREVIEW_BLOCKS = 80_000;

    private static Map<String, String> parseProperties(CompoundTag blockState) {
        if (!blockState.contains("Properties")) {
            return Collections.emptyMap();
        }
        CompoundTag propsTag = blockState.getCompound("Properties").orElse(new CompoundTag());
        Map<String, String> props = new HashMap<>();
        for (String key : propsTag.keySet()) {
            props.put(key, propsTag.getString(key).orElse(""));
        }
        return props;
    }

    public static List<BlockData> parseBlockPositions(File litematicFile) {
        List<BlockData> positions = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(litematicFile)) {
            CompoundTag root = NbtIo.readCompressed(fis, NbtAccounter.unlimitedHeap());

            if (!root.contains("Regions")) return Collections.emptyList();

            CompoundTag regions = root.getCompound("Regions").orElse(new CompoundTag());

            for (String regionName : regions.keySet()) {
                if (positions.size() >= MAX_PREVIEW_BLOCKS) break;

                CompoundTag region = regions.getCompound(regionName).orElse(new CompoundTag());
                if (!region.contains("BlockStatePalette") || !region.contains("BlockStates")) continue;

                ListTag palette = region.getList("BlockStatePalette").orElse(new ListTag());
                Map<Integer, String> indexToBlockId = new HashMap<>();
                Map<Integer, Map<String, String>> indexToProperties = new HashMap<>();
                for (int i = 0; i < palette.size(); i++) {
                    CompoundTag blockState = palette.getCompound(i).orElse(new CompoundTag());
                    if (blockState.contains("Name")) {
                        indexToBlockId.put(i, blockState.getString("Name").orElse("minecraft:air"));
                        indexToProperties.put(i, parseProperties(blockState));
                    }
                }

                CompoundTag sizeCompound = region.getCompound("Size").orElse(new CompoundTag());
                int sizeX = Math.abs(sizeCompound.getInt("x").orElse(0));
                int sizeY = Math.abs(sizeCompound.getInt("y").orElse(0));
                int sizeZ = Math.abs(sizeCompound.getInt("z").orElse(0));
                int totalBlocks = sizeX * sizeY * sizeZ;
                if (totalBlocks == 0 || palette.isEmpty()) continue;

                long[] blockStates = region.getLongArray("BlockStates").orElse(new long[0]);
                if (blockStates.length == 0) continue;

                int bitsPerBlock = Math.max(2, (int) Math.ceil(Math.log(palette.size()) / Math.log(2)));
                long maxEntryValue = (1L << bitsPerBlock) - 1L;

                for (int i = 0; i < totalBlocks && positions.size() < MAX_PREVIEW_BLOCKS; i++) {
                    int bitIndex = i * bitsPerBlock;
                    int arrayIndex = bitIndex / 64;
                    int bitOffset = bitIndex % 64;

                    if (arrayIndex >= blockStates.length) break;

                    long value;
                    if (bitOffset + bitsPerBlock <= 64) {
                        value = (blockStates[arrayIndex] >> bitOffset) & maxEntryValue;
                    } else {
                        int bitsFromFirst = 64 - bitOffset;
                        int bitsFromSecond = bitsPerBlock - bitsFromFirst;
                        long firstPart = (blockStates[arrayIndex] >> bitOffset) & ((1L << bitsFromFirst) - 1L);
                        long secondPart = (arrayIndex + 1 < blockStates.length)
                                ? (blockStates[arrayIndex + 1] & ((1L << bitsFromSecond) - 1L))
                                : 0;
                        value = firstPart | (secondPart << bitsFromFirst);
                    }

                    int paletteIndex = (int) value;
                    if (paletteIndex < 0 || paletteIndex >= indexToBlockId.size()) continue;

                    String blockId = indexToBlockId.getOrDefault(paletteIndex, "minecraft:air");
                    if (blockId.equals("minecraft:air") || blockId.equals("minecraft:cave_air") || blockId.equals("minecraft:void_air")) continue;

                    // Index order: y * sizeX * sizeZ + z * sizeX + x
                    int bx = i % sizeX;
                    int bz = (i / sizeX) % sizeZ;
                    int by = i / (sizeX * sizeZ);
                    positions.add(new BlockData(bx, by, bz, blockId,
                            indexToProperties.getOrDefault(paletteIndex, Collections.emptyMap())));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }

        return positions;
    }

    public static List<BlockCount> parseBlockCounts(File litematicFile) {
        Map<String, Integer> blockCounts = new HashMap<>();

        try (FileInputStream fis = new FileInputStream(litematicFile)) {

            CompoundTag root = NbtIo.readCompressed(fis, NbtAccounter.unlimitedHeap());

            if (!root.contains("Regions")) {
                return Collections.emptyList();
            }

            CompoundTag regions = root.getCompound("Regions").orElse(new CompoundTag());

            for (String regionName : regions.keySet()) {
                CompoundTag region = regions.getCompound(regionName).orElse(new CompoundTag());

                if (!region.contains("BlockStatePalette")) {
                    continue;
                }

                ListTag palette = region.getList("BlockStatePalette").orElse(new ListTag());
                Map<Integer, String> indexToBlockId = new HashMap<>();

                for (int i = 0; i < palette.size(); i++) {
                    CompoundTag blockState = palette.getCompound(i).orElse(new CompoundTag());
                    if (blockState.contains("Name")) {
                        String blockId = blockState.getString("Name").orElse("minecraft:air");
                        indexToBlockId.put(i, blockId);
                    }
                }

                if (!region.contains("BlockStates")) {
                    continue;
                }

                long[] blockStates = region.getLongArray("BlockStates").orElse(new long[0]);
                if (blockStates.length == 0) {
                    continue;
                }

                CompoundTag sizeCompound = region.getCompound("Size").orElse(new CompoundTag());
                int sizeX = sizeCompound.getInt("x").orElse(0);
                int sizeY = sizeCompound.getInt("y").orElse(0);
                int sizeZ = sizeCompound.getInt("z").orElse(0);

                int absSizeX = Math.abs(sizeX);
                int absSizeY = Math.abs(sizeY);
                int absSizeZ = Math.abs(sizeZ);
                int totalBlocks = absSizeX * absSizeY * absSizeZ;

                if (totalBlocks == 0) {
                    continue;
                }

                int paletteSize = palette.size();
                if (paletteSize == 0) {
                    continue;
                }

                int bitsPerBlock = Math.max(2, (int) Math.ceil(Math.log(paletteSize) / Math.log(2)));

                long maxEntryValue = (1L << bitsPerBlock) - 1L;

                for (int i = 0; i < totalBlocks; i++) {
                    int bitIndex = i * bitsPerBlock;
                    int arrayIndex = bitIndex / 64;
                    int bitOffset = bitIndex % 64;

                    if (arrayIndex >= blockStates.length) {
                        break;
                    }

                    long value;
                    if (bitOffset + bitsPerBlock <= 64) {
                        value = (blockStates[arrayIndex] >> bitOffset) & maxEntryValue;
                    } else {
                        int bitsFromFirst = 64 - bitOffset;
                        int bitsFromSecond = bitsPerBlock - bitsFromFirst;
                        long firstPart = (blockStates[arrayIndex] >> bitOffset) & ((1L << bitsFromFirst) - 1L);
                        long secondPart = (arrayIndex + 1 < blockStates.length)
                            ? (blockStates[arrayIndex + 1] & ((1L << bitsFromSecond) - 1L))
                            : 0;
                        value = firstPart | (secondPart << bitsFromFirst);
                    }

                    int paletteIndex = (int) value;
                    if (paletteIndex < 0 || paletteIndex >= indexToBlockId.size()) {
                        continue;
                    }

                    String blockId = indexToBlockId.getOrDefault(paletteIndex, "minecraft:air");

                    if (!blockId.equals("minecraft:air") && !blockId.equals("minecraft:cave_air") && !blockId.equals("minecraft:void_air")) {
                        blockCounts.put(blockId, blockCounts.getOrDefault(blockId, 0) + 1);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }

        List<BlockCount> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : blockCounts.entrySet()) {
            result.add(new BlockCount(entry.getKey(), entry.getValue()));
        }

        result.sort((a, b) -> Integer.compare(b.count, a.count));

        return result;
    }

    public static String getSimpleBlockName(String blockId) {
        if (blockId.startsWith("minecraft:")) {
            blockId = blockId.substring("minecraft:".length());
        }
        return Arrays.stream(blockId.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .reduce((a, b) -> a + " " + b)
                .orElse(blockId);
    }
}
