package com.choculaterie.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PluginCatalog {
	private static final String BASE = "https://api.choculaterie.com/api/Plugins";
	private static final List<String> HOSTS = List.of("api.choculaterie.com");

	public record Entry(String id, String name, String version, String homepage, String kind, List<String> hosts) {
	}

	private PluginCatalog() {
	}

	public static List<Entry> fetch() throws IOException {
		String body = PluginHttp.getText(BASE, "GET", null, null, HOSTS);
		JsonElement root = PluginJson.GSON.fromJson(body, JsonElement.class);
		JsonArray array = null;
		if (root != null && root.isJsonObject() && root.getAsJsonObject().has("plugins")) {
			JsonElement plugins = root.getAsJsonObject().get("plugins");
			if (plugins.isJsonArray()) {
				array = plugins.getAsJsonArray();
			}
		} else if (root != null && root.isJsonArray()) {
			array = root.getAsJsonArray();
		}
		if (array == null) {
			throw new IOException("unexpected catalogue response");
		}

		List<Entry> entries = new ArrayList<>();
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject o = element.getAsJsonObject();
			String id = string(o, "id");
			if (id.isEmpty()) {
				continue;
			}
			List<String> hosts = new ArrayList<>();
			JsonElement hostArray = o.get("hosts");
			if (hostArray != null && hostArray.isJsonArray()) {
				for (JsonElement h : hostArray.getAsJsonArray()) {
					hosts.add(h.getAsString());
				}
			}
			entries.add(new Entry(id, string(o, "name"), string(o, "version"),
					string(o, "homepage"), string(o, "kind"), hosts));
		}
		return entries;
	}

	public static void install(Entry entry) throws IOException {
		byte[] bytes = PluginHttp.fetch(BASE + "/" + entry.id(), "GET", null, null, HOSTS,
				PluginHttp.MAX_RESPONSE_BYTES);
		PluginManifest manifest = PluginJson.GSON.fromJson(new String(bytes, StandardCharsets.UTF_8),
				PluginManifest.class);
		if (manifest == null) {
			throw new IOException("downloaded manifest is empty");
		}
		String problem = manifest.validate();
		if (problem != null) {
			throw new IOException("rejected manifest: " + problem);
		}
		if (!entry.id().equals(manifest.id)) {
			throw new IOException("manifest id does not match catalogue entry");
		}

		Path dir = PluginRegistry.directory();
		Files.createDirectories(dir);
		Files.write(dir.resolve(entry.id() + ".json"), bytes);
		PluginRegistry.reload();
	}

	public static String installedVersion(String id) {
		for (PluginSource source : PluginRegistry.all()) {
			if (source.id().equals(id)) {
				return source.manifest().versionOrEmpty();
			}
		}
		return null;
	}

	public static boolean updateAvailable(Entry entry) {
		String installed = installedVersion(entry.id());
		return installed != null && !installed.equals(entry.version()) && !entry.version().isEmpty();
	}

	private static String string(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}
}
