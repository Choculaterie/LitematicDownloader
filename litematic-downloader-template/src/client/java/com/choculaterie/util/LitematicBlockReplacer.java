package com.choculaterie.util;

import net.minecraft.nbt.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

public class LitematicBlockReplacer {

    public static boolean replaceBlock(File litematicFile, String oldBlockId, String newBlockId) {
        try (FileInputStream fis = new FileInputStream(litematicFile)) {
            CompoundTag root = NbtIo.readCompressed(fis, NbtAccounter.unlimitedHeap());

            if (!root.contains("Regions")) {
                return false;
            }

            CompoundTag regions = root.getCompound("Regions").orElse(new CompoundTag());
            boolean wasReplaced = false;

            for (String regionName : regions.keySet()) {
                CompoundTag region = regions.getCompound(regionName).orElse(new CompoundTag());

                if (!region.contains("BlockStatePalette")) {
                    continue;
                }

                ListTag palette = region.getList("BlockStatePalette").orElse(new ListTag());

                for (int i = 0; i < palette.size(); i++) {
                    CompoundTag blockState = palette.getCompound(i).orElse(new CompoundTag());
                    if (blockState.contains("Name")) {
                        String blockName = blockState.getString("Name").orElse("");
                        if (blockName.equals(oldBlockId)) {
                            CompoundTag newBlockState = new CompoundTag();
                            newBlockState.putString("Name", newBlockId);

                            if (blockState.contains("Properties")) {
                                CompoundTag properties = blockState.getCompound("Properties").orElse(new CompoundTag());
                                newBlockState.put("Properties", properties);
                            }

                            palette.set(i, newBlockState);
                            wasReplaced = true;
                        }
                    }
                }
            }

            if (wasReplaced) {
                File backupFile = new File(litematicFile.getParent(), litematicFile.getName() + ".backup");
                if (backupFile.exists()) {
                    backupFile.delete();
                }
                litematicFile.renameTo(backupFile);

                try (FileOutputStream fos = new FileOutputStream(litematicFile)) {
                    NbtIo.writeCompressed(root, fos);
                }

                backupFile.delete();
                return true;
            }

            return false;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
