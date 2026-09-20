package com.choculaterie.plugin;

import com.choculaterie.config.DownloadSettings;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class PluginRegistry {
	private static final String DIRECTORY = "litematic-downloader/plugins";
	private static final long MAX_MANIFEST_BYTES = 256 * 1024;

	private static final Map<String, PluginSource> SOURCES = new LinkedHashMap<>();
	private static final Map<String, String> ERRORS = new LinkedHashMap<>();
	private static boolean loaded = false;

	private PluginRegistry() {
	}

	public static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve(DIRECTORY);
	}

	public static synchronized void reload() {
		SOURCES.clear();
		ERRORS.clear();
		loaded = true;
		Path dir = directory();
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			System.err.println("[Plugin] cannot create " + dir + ": " + e.getMessage());
			return;
		}
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.filter(p -> p.toString().toLowerCase().endsWith(".json")).toList()) {
				load(file);
			}
		} catch (IOException e) {
			System.err.println("[Plugin] cannot list " + dir + ": " + e.getMessage());
		}
	}

	private static void load(Path file) {
		String label = file.getFileName().toString();
		try {
			if (Files.size(file) > MAX_MANIFEST_BYTES) {
				ERRORS.put(label, "file is larger than 256KB");
				return;
			}
			PluginManifest manifest;
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				manifest = PluginJson.GSON.fromJson(reader, PluginManifest.class);
			}
			if (manifest == null) {
				ERRORS.put(label, "file is empty");
				return;
			}
			manifest.sourceFile = label;
			String problem = manifest.validate();
			if (problem != null) {
				ERRORS.put(label, problem);
				return;
			}
			if (SOURCES.containsKey(manifest.id)) {
				ERRORS.put(label, "duplicate plugin id " + manifest.id);
				return;
			}
			SOURCES.put(manifest.id, new PluginSource(manifest));
		} catch (JsonSyntaxException e) {
			ERRORS.put(label, "invalid JSON: " + e.getMessage());
		} catch (Exception e) {
			ERRORS.put(label, e.getMessage() == null ? e.toString() : e.getMessage());
		}
	}

	private static synchronized void ensureLoaded() {
		if (!loaded) {
			reload();
		}
	}

	public static synchronized List<PluginSource> all() {
		ensureLoaded();
		return new ArrayList<>(SOURCES.values());
	}

	public static synchronized List<PluginSource> enabled() {
		ensureLoaded();
		List<PluginSource> out = new ArrayList<>();
		for (PluginSource source : SOURCES.values()) {
			if (isEnabled(source.id())) {
				out.add(source);
			}
		}
		return out;
	}

	public static synchronized PluginSource get(String id) {
		ensureLoaded();
		if (id == null) {
			return null;
		}
		PluginSource source = SOURCES.get(id);
		return source != null && isEnabled(id) ? source : null;
	}

	public static synchronized boolean isKnown(String id) {
		ensureLoaded();
		return id != null && SOURCES.containsKey(id);
	}

	public static synchronized String nameOf(String id) {
		ensureLoaded();
		PluginSource source = SOURCES.get(id);
		return source != null ? source.manifest().displayName() : id;
	}

	public static synchronized Map<String, String> errors() {
		ensureLoaded();
		return new LinkedHashMap<>(ERRORS);
	}

	public static boolean isEnabled(String id) {
		for (String entry : DownloadSettings.getInstance().getEnabledPlugins().split(",")) {
			if (entry.trim().equals(id)) {
				return true;
			}
		}
		return false;
	}

	public static void setEnabled(String id, boolean enabled) {
		List<String> ids = new ArrayList<>();
		for (String entry : DownloadSettings.getInstance().getEnabledPlugins().split(",")) {
			String trimmed = entry.trim();
			if (!trimmed.isEmpty() && !trimmed.equals(id)) {
				ids.add(trimmed);
			}
		}
		if (enabled) {
			ids.add(id);
		}
		DownloadSettings.getInstance().setEnabledPlugins(String.join(",", ids));
	}
}
