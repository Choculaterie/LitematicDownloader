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
            NbtCompound root = NbtIo.readCompressed(fis, NbtSizeTracker.ofUnlimitedBytes());

            if (!root.contains("Regions")) {
                return false;
            }

            NbtCompound regions = root.getCompound("Regions").orElse(new NbtCompound());
            boolean wasReplaced = false;

            for (String regionName : regions.getKeys()) {
                NbtCompound region = regions.getCompound(regionName).orElse(new NbtCompound());

                if (!region.contains("BlockStatePalette")) {
                    continue;
                }

                NbtList palette = region.getList("BlockStatePalette").orElse(new NbtList());

                for (int i = 0; i < palette.size(); i++) {
                    NbtCompound blockState = palette.getCompound(i).orElse(new NbtCompound());
                    if (blockState.contains("Name")) {
                        String blockName = blockState.getString("Name").orElse("");
                        if (blockName.equals(oldBlockId)) {
                            NbtCompound newBlockState = new NbtCompound();
                            newBlockState.putString("Name", newBlockId);

                            if (blockState.contains("Properties")) {
                                NbtCompound properties = blockState.getCompound("Properties").orElse(new NbtCompound());
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
