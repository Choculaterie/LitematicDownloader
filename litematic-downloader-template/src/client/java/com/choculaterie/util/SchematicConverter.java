package com.choculaterie.util;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.datafix.fixes.BlockStateData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SchematicConverter {
	private static final int LITEMATIC_VERSION = 6;
	private static final String AIR = "minecraft:air";

	private SchematicConverter() {
	}

	public static boolean isConvertible(String fileName) {
		if (fileName == null) {
			return false;
		}
		String n = fileName.toLowerCase();
		return n.endsWith(".schematic") || n.endsWith(".schem");
	}

	public static byte[] toLitematic(byte[] data, String name) throws IOException {
		CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
		if (root.contains("Schematic")) {
			root = root.getCompound("Schematic").orElse(root);
		}

		int width = root.getShort("Width").orElse((short) 0) & 0xFFFF;
		int height = root.getShort("Height").orElse((short) 0) & 0xFFFF;
		int length = root.getShort("Length").orElse((short) 0) & 0xFFFF;
		if (width <= 0 || height <= 0 || length <= 0) {
			throw new IOException("schematic has no size");
		}
		int volume = width * height * length;

		Palette palette = new Palette();
		int[] indices;
		CompoundTag spongeV3 = root.getCompound("Blocks").filter(b -> b.contains("Data")).orElse(null);
		if (spongeV3 != null) {
			indices = readSponge(spongeV3.getCompound("Palette").orElse(new CompoundTag()),
					spongeV3.getByteArray("Data").orElse(new byte[0]), volume, palette);
		} else if (root.contains("BlockData")) {
			indices = readSponge(root.getCompound("Palette").orElse(new CompoundTag()),
					root.getByteArray("BlockData").orElse(new byte[0]), volume, palette);
		} else if (root.contains("Blocks")) {
			indices = readLegacy(root, volume, palette);
		} else {
			throw new IOException("unrecognised schematic: no Blocks or BlockData");
		}

		return write(palette, indices, width, height, length, name);
	}

	private static int[] readLegacy(CompoundTag root, int volume, Palette palette) throws IOException {
		byte[] blocks = root.getByteArray("Blocks").orElse(new byte[0]);
		byte[] meta = root.getByteArray("Data").orElse(new byte[0]);
		if (blocks.length < volume) {
			throw new IOException("Blocks array is shorter than the declared size");
		}
		byte[] add = root.getByteArray("AddBlocks").orElse(root.getByteArray("Add").orElse(new byte[0]));

		int[] indices = new int[volume];
		Map<Integer, Integer> cache = new HashMap<>();
		for (int i = 0; i < volume; i++) {
			int id = blocks[i] & 0xFF;
			if (add.length > 0 && (i >> 1) < add.length) {
				int nibble = (i & 1) == 0 ? (add[i >> 1] >> 4) & 0x0F : add[i >> 1] & 0x0F;
				id |= nibble << 8;
			}
			int value = meta.length > i ? meta[i] & 0x0F : 0;
			int key = (id << 4) | value;
			Integer index = cache.get(key);
			if (index == null) {
				index = palette.add(legacyState(key));
				cache.put(key, index);
			}
			indices[i] = index;
		}
		return indices;
	}

	private static CompoundTag legacyState(int key) {
		if (key >= 4096) {
			return named(AIR);
		}
		Dynamic<?> tag = BlockStateData.getTag(key);
		Object value = tag.getValue();
		return value instanceof CompoundTag compound ? compound : named(AIR);
	}

	private static int[] readSponge(CompoundTag spongePalette, byte[] blockData, int volume, Palette palette)
			throws IOException {
		Map<Integer, Integer> remap = new HashMap<>();
		for (String key : spongePalette.keySet()) {
			int id = spongePalette.getInt(key).orElse(0);
			remap.put(id, palette.add(parseState(key)));
		}

		int[] indices = new int[volume];
		int cursor = 0;
		for (int i = 0; i < volume; i++) {
			int value = 0;
			int shift = 0;
			while (true) {
				if (cursor >= blockData.length) {
					throw new IOException("BlockData ended early");
				}
				byte b = blockData[cursor++];
				value |= (b & 0x7F) << shift;
				if ((b & 0x80) == 0) {
					break;
				}
				shift += 7;
			}
			Integer mapped = remap.get(value);
			indices[i] = mapped == null ? palette.add(named(AIR)) : mapped;
		}
		return indices;
	}

	static CompoundTag parseState(String descriptor) {
		int bracket = descriptor.indexOf('[');
		if (bracket < 0) {
			return named(normalise(descriptor));
		}
		CompoundTag state = named(normalise(descriptor.substring(0, bracket)));
		String body = descriptor.substring(bracket + 1, descriptor.endsWith("]") ? descriptor.length() - 1 : descriptor.length());
		CompoundTag properties = new CompoundTag();
		for (String pair : body.split(",")) {
			int eq = pair.indexOf('=');
			if (eq > 0) {
				properties.putString(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
			}
		}
		if (!properties.isEmpty()) {
			state.put("Properties", properties);
		}
		return state;
	}

	private static String normalise(String name) {
		String trimmed = name.trim();
		return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
	}

	private static CompoundTag named(String name) {
		CompoundTag tag = new CompoundTag();
		tag.putString("Name", name);
		return tag;
	}

	private static byte[] write(Palette palette, int[] indices, int width, int height, int length, String name)
			throws IOException {
		int volume = indices.length;
		int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.size() - 1)));
		long max = (1L << bits) - 1L;
		long[] packed = new long[(int) ((volume * (long) bits + 63) / 64)];
		int nonAir = 0;
		for (int i = 0; i < volume; i++) {
			if (!palette.isAir(indices[i])) {
				nonAir++;
			}
			long value = indices[i] & max;
			long bitIndex = i * (long) bits;
			int slot = (int) (bitIndex >> 6);
			int offset = (int) (bitIndex & 63);
			packed[slot] |= value << offset;
			if (offset + bits > 64) {
				packed[slot + 1] |= value >>> (64 - offset);
			}
		}

		CompoundTag size = new CompoundTag();
		size.putInt("x", width);
		size.putInt("y", height);
		size.putInt("z", length);

		CompoundTag position = new CompoundTag();
		position.putInt("x", 0);
		position.putInt("y", 0);
		position.putInt("z", 0);

		ListTag paletteTag = new ListTag();
		paletteTag.addAll(palette.entries());

		CompoundTag region = new CompoundTag();
		region.put("Position", position);
		region.put("Size", size);
		region.put("BlockStatePalette", paletteTag);
		region.putLongArray("BlockStates", packed);
		region.put("TileEntities", new ListTag());
		region.put("Entities", new ListTag());
		region.put("PendingBlockTicks", new ListTag());
		region.put("PendingFluidTicks", new ListTag());

		String regionName = name == null || name.isBlank() ? "Converted" : name;
		CompoundTag regions = new CompoundTag();
		regions.put(regionName, region);

		long now = System.currentTimeMillis();
		CompoundTag metadata = new CompoundTag();
		metadata.put("EnclosingSize", size.copy());
		metadata.putString("Name", regionName);
		metadata.putString("Author", "");
		metadata.putString("Description", "Converted from schematic by Litematic Downloader");
		metadata.putLong("TimeCreated", now);
		metadata.putLong("TimeModified", now);
		metadata.putInt("RegionCount", 1);
		metadata.putInt("TotalBlocks", nonAir);
		metadata.putInt("TotalVolume", volume);

		CompoundTag root = new CompoundTag();
		root.putInt("Version", LITEMATIC_VERSION);
		root.putInt("MinecraftDataVersion", SharedConstants.WORLD_VERSION);
		root.put("Metadata", metadata);
		root.put("Regions", regions);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		NbtIo.writeCompressed(root, out);
		return out.toByteArray();
	}

	private static final class Palette {
		private final Map<String, Integer> byKey = new LinkedHashMap<>();
		private final List<CompoundTag> entries = new ArrayList<>();
		private final List<Boolean> air = new ArrayList<>();

		Palette() {
			add(named(AIR));
		}

		int add(CompoundTag state) {
			String key = key(state);
			Integer existing = byKey.get(key);
			if (existing != null) {
				return existing;
			}
			int index = entries.size();
			entries.add(state);
			air.add(AIR.equals(state.getString("Name").orElse("")));
			byKey.put(key, index);
			return index;
		}

		private static String key(CompoundTag state) {
			StringBuilder sb = new StringBuilder(state.getString("Name").orElse(""));
			CompoundTag properties = state.getCompound("Properties").orElse(null);
			if (properties != null) {
				List<String> names = new ArrayList<>(properties.keySet());
				names.sort(null);
				for (String property : names) {
					sb.append('|').append(property).append('=').append(properties.getString(property).orElse(""));
				}
			}
			return sb.toString();
		}

		boolean isAir(int index) {
			return index >= 0 && index < air.size() && air.get(index);
		}

		int size() {
			return entries.size();
		}

		List<CompoundTag> entries() {
			return entries;
		}
	}
}
