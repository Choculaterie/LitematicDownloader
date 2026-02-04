package com.choculaterie.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;

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

    public static List<BlockCount> parseBlockCounts(File litematicFile) {
        Map<String, Integer> blockCounts = new HashMap<>();

        try (FileInputStream fis = new FileInputStream(litematicFile)) {

            NbtCompound root = NbtIo.readCompressed(fis, NbtSizeTracker.ofUnlimitedBytes());

            if (!root.contains("Regions")) {
                return Collections.emptyList();
            }

            NbtCompound regions = root.getCompound("Regions").orElse(new NbtCompound());

            for (String regionName : regions.getKeys()) {
                NbtCompound region = regions.getCompound(regionName).orElse(new NbtCompound());

                if (!region.contains("BlockStatePalette")) {
                    continue;
                }

                NbtList palette = region.getList("BlockStatePalette").orElse(new NbtList());
                Map<Integer, String> indexToBlockId = new HashMap<>();

                for (int i = 0; i < palette.size(); i++) {
                    NbtCompound blockState = palette.getCompound(i).orElse(new NbtCompound());
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

                NbtCompound sizeCompound = region.getCompound("Size").orElse(new NbtCompound());
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
