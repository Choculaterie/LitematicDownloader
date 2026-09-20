package com.choculaterie.plugin;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class PluginManifest {
	public static final int SUPPORTED_FORMAT = 1;
	private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

	public int format;
	public String id;
	public String name;
	public String homepage;
	public String version;
	public String kind;
	public String base;
	public String vendor;
	public List<String> hosts;
	public List<String> fileHosts;
	public Map<String, String> sortMap;
	public Map<String, Map<String, Field>> defs;
	public Block search;
	public Block details;
	public Block files;

	public transient String sourceFile;

	public boolean isApi() {
		return "api".equalsIgnoreCase(kind);
	}

	public List<String> downloadHosts() {
		return fileHosts != null && !fileHosts.isEmpty() ? fileHosts : hosts;
	}

	public boolean downloadsAnywhere() {
		return downloadHosts().stream().anyMatch(h -> "*".equals(h == null ? null : h.trim()));
	}

	public String versionOrEmpty() {
		return version == null ? "" : version.trim();
	}

	public String displayName() {
		return name != null && !name.isBlank() ? name : id;
	}

	public String validate() {
		if (format != SUPPORTED_FORMAT) {
			return "unsupported format " + format + " (this build understands " + SUPPORTED_FORMAT + ")";
		}
		if (id == null || !ID.matcher(id).matches()) {
			return "id must be lowercase letters, digits, dot, dash or underscore";
		}
		if (hosts == null || hosts.isEmpty()) {
			return "hosts must list every domain this plugin may contact";
		}
		for (String h : hosts) {
			if (h == null || h.isBlank() || h.contains("/") || h.equals("*")) {
				return "invalid host entry: " + h;
			}
		}
		if (fileHosts != null) {
			for (String h : fileHosts) {
				if (h == null || h.isBlank() || h.contains("/")) {
					return "invalid fileHosts entry: " + h;
				}
			}
		}
		if (isApi()) {
			if (base == null || base.isBlank()) {
				return "api plugins need a base url";
			}
			try {
				PluginHttp.validate(base, hosts);
			} catch (Exception e) {
				return "base url rejected: " + e.getMessage();
			}
			return null;
		}
		if (!"scrape".equalsIgnoreCase(kind)) {
			return "kind must be \"api\" or \"scrape\"";
		}
		if (search == null || search.resolved().isEmpty()) {
			return "scrape plugins need a search block";
		}
		return null;
	}

	public Map<String, Field> fieldsFor(Parse parse) {
		if (parse == null || parse.fields == null) {
			return Map.of();
		}
		if (parse.fields.isJsonPrimitive()) {
			String ref = parse.fields.getAsString();
			if (ref.startsWith("@") && defs != null) {
				Map<String, Field> def = defs.get(ref.substring(1));
				return def != null ? def : Map.of();
			}
			return Map.of();
		}
		return PluginJson.GSON.fromJson(parse.fields, PluginJson.FIELD_MAP);
	}

	public static class Block {
		public List<Variant> variants;
		public Request request;
		public Parse parse;
		@SerializedName("static")
		public List<Map<String, String>> staticEntries;

		public List<Variant> resolved() {
			if (variants != null && !variants.isEmpty()) {
				return variants;
			}
			if (request != null) {
				Variant v = new Variant();
				v.request = request;
				v.parse = parse;
				return List.of(v);
			}
			return List.of();
		}
	}

	public static class Variant {
		public Map<String, String> when;
		public Request request;
		public Parse parse;
	}

	public static class Request {
		public String method;
		public String url;
		public Map<String, String> headers;
		public Map<String, String> query;
		public Map<String, String> form;
	}

	public static class Parse {
		public String type;
		public String unwrap;
		public String list;
		public com.google.gson.JsonElement fields;
	}

	public static class Field {
		public String select;
		public String path;
		public com.google.gson.JsonElement attr;
		public Boolean text;
		public String regex;
		public String template;
		public String prefix;
		public String suffix;
		public String split;
		public Boolean all;
		public Boolean raw;
		@SerializedName("default")
		public String fallback;
	}
}
