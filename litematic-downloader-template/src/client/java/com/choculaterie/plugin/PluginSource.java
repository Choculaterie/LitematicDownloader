package com.choculaterie.plugin;

import com.choculaterie.models.MinemevFileInfo;
import com.choculaterie.models.MinemevPostDetailInfo;
import com.choculaterie.models.MinemevPostInfo;
import com.choculaterie.models.MinemevSearchResponse;
import com.choculaterie.network.MinemevParsers;
import com.google.gson.JsonElement;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PluginSource {
	public record Result(List<MinemevPostInfo> posts, int totalPages, int totalItems) {
	}

	private final PluginManifest manifest;

	public PluginSource(PluginManifest manifest) {
		this.manifest = manifest;
	}

	public PluginManifest manifest() {
		return manifest;
	}

	public String id() {
		return manifest.id;
	}

	public Result search(String query, String sort, int page, int pageSize,
						 String tag, String versions) throws IOException {
		Map<String, String> vars = vars(query, sort, page, pageSize, tag, versions, null);
		if (manifest.isApi()) {
			String url = manifest.base + "/search?clean_uuid=0&page=" + page + "&pagesize=" + pageSize
					+ "&sort=" + enc(mapSort(sort))
					+ (manifest.vendor != null && !manifest.vendor.isBlank()
							? "&vendor=" + enc(manifest.vendor) : "")
					+ (query != null && !query.isEmpty() ? "&search=" + enc(query) : "")
					+ (tag != null && !tag.isEmpty() ? "&tag=" + enc(tag) : "")
					+ (versions != null && !versions.isEmpty() ? "&versions=" + enc(versions) : "");
			String body = PluginHttp.getText(url, "GET", null, null, manifest.hosts);
			MinemevSearchResponse response = MinemevParsers.parseSearchResponse(body);
			com.google.gson.JsonObject raw = PluginJson.GSON.fromJson(body, com.google.gson.JsonObject.class);
			int totalPages = raw != null && raw.has("total_pages") && raw.get("total_pages").isJsonPrimitive()
					? raw.get("total_pages").getAsInt() : response.totalPages();
			int totalItems = raw != null && raw.has("total_items") && raw.get("total_items").isJsonPrimitive()
					? raw.get("total_items").getAsInt() : response.totalItems();
			return new Result(rebrand(response.posts()), totalPages, totalItems);
		}
		PluginManifest.Variant variant = pick(manifest.search, vars);
		if (variant == null) {
			return new Result(List.of(), -1, -1);
		}
		List<Object> nodes = nodes(variant, vars, true);
		List<MinemevPostInfo> posts = new ArrayList<>();
		Map<String, PluginManifest.Field> fields = manifest.fieldsFor(variant.parse);
		for (Object node : nodes) {
			Map<String, List<String>> v = Extractor.extract(node, fields, null);
			String uuid = one(v, "uuid");
			if (uuid.isEmpty()) {
				continue;
			}
			posts.add(new MinemevPostInfo(
					manifest.id + "/" + uuid,
					one(v, "post_name"),
					one(v, "description"),
					one(v, "User"),
					intOf(v, "downloads"),
					one(v, "published_at"),
					many(v, "tags"),
					many(v, "versions"),
					manifest.id,
					many(v, "images"),
					nullable(v, "thumbnail_url"),
					nullable(v, "user_picture"),
					nullable(v, "yt_link"),
					nullable(v, "url_redirect")));
		}
		return new Result(posts, -1, -1);
	}

	public MinemevPostDetailInfo details(String uuid) throws IOException {
		if (manifest.isApi()) {
			String body = PluginHttp.getText(manifest.base + "/details/" + uuid, "GET", null, null, manifest.hosts);
			return MinemevParsers.parsePostDetail(body);
		}
		Map<String, String> vars = vars(null, null, 1, 0, null, null, uuid);
		PluginManifest.Variant variant = pick(manifest.details, vars);
		if (variant == null) {
			return new MinemevPostDetailInfo(uuid, uuid, "", "", null, 0, null,
					new String[0], new String[0], new String[0], null, false, null, null);
		}
		List<Object> nodes = nodes(variant, vars, false);
		Object node = nodes.isEmpty() ? null : nodes.get(0);
		Map<String, List<String>> v = Extractor.extract(node, manifest.fieldsFor(variant.parse),
				Map.of("uuid", uuid));
		String description = one(v, "description");
		return new MinemevPostDetailInfo(
				uuid,
				one(v, "post_name"),
				description,
				v.containsKey("description_md") ? one(v, "description_md") : description,
				one(v, "User"),
				intOf(v, "downloads"),
				one(v, "published_at"),
				many(v, "tags"),
				many(v, "versions"),
				many(v, "images"),
				nullable(v, "yt_link"),
				false,
				nullable(v, "creators"),
				nullable(v, "url_redirect"));
	}

	public MinemevFileInfo[] files(String uuid) throws IOException {
		if (manifest.isApi()) {
			String body = PluginHttp.getText(manifest.base + "/files/" + uuid, "GET", null, null, manifest.hosts);
			return MinemevParsers.parseFileList(body);
		}
		Map<String, String> vars = vars(null, null, 1, 0, null, null, uuid);
		if (manifest.files != null && manifest.files.staticEntries != null) {
			List<MinemevFileInfo> out = new ArrayList<>();
			for (Map<String, String> entry : manifest.files.staticEntries) {
				Map<String, List<String>> v = new LinkedHashMap<>();
				vars.forEach((k, val) -> v.put(k, List.of(val)));
				entry.forEach((k, val) -> {
					String resolved = Extractor.interpolate(val, v);
					v.put(k, resolved.isEmpty() ? List.of() : List.of(resolved));
				});
				out.add(toFile(v, uuid));
			}
			return out.toArray(new MinemevFileInfo[0]);
		}
		PluginManifest.Variant variant = pick(manifest.files, vars);
		if (variant == null) {
			return new MinemevFileInfo[0];
		}
		List<Object> nodes = nodes(variant, vars, true);
		List<MinemevFileInfo> out = new ArrayList<>();
		Map<String, PluginManifest.Field> fields = manifest.fieldsFor(variant.parse);
		for (Object node : nodes) {
			out.add(toFile(Extractor.extract(node, fields, Map.of("uuid", uuid)), uuid));
		}
		return out.toArray(new MinemevFileInfo[0]);
	}

	public byte[] download(String url) throws IOException {
		return PluginHttp.fetch(url, "GET", null, null, manifest.downloadHosts(), PluginHttp.MAX_DOWNLOAD_BYTES);
	}

	private MinemevFileInfo toFile(Map<String, List<String>> v, String uuid) {
		String id = one(v, "id");
		return new MinemevFileInfo(
				id.isEmpty() ? uuid : id,
				one(v, "default_file_name"),
				one(v, "file"),
				longOf(v, "file_size"),
				many(v, "versions"),
				intOf(v, "downloads"),
				v.containsKey("file_type") ? one(v, "file_type") : "litematic",
				false);
	}

	private List<MinemevPostInfo> rebrand(MinemevPostInfo[] posts) {
		List<MinemevPostInfo> out = new ArrayList<>(posts.length);
		for (MinemevPostInfo p : posts) {
			out.add(new MinemevPostInfo(manifest.id + "/" + p.uuid(), p.title(), p.description(), p.author(), p.downloads(),
					p.createdAt(), p.tags(), p.versions(), manifest.id, p.images(), p.thumbnailUrl(),
					p.userPicture(), p.ytLink(), p.urlRedirect()));
		}
		return out;
	}

	private List<Object> nodes(PluginManifest.Variant variant, Map<String, String> vars, boolean asList)
			throws IOException {
		PluginManifest.Request request = variant.request;
		String url = interpolateUrl(request.url, vars);
		Map<String, String> query = interpolateAll(request.query, vars, true);
		if (query != null && !query.isEmpty()) {
			StringBuilder sb = new StringBuilder(url);
			sb.append(url.contains("?") ? '&' : '?');
			boolean first = true;
			for (Map.Entry<String, String> e : query.entrySet()) {
				if (!first) {
					sb.append('&');
				}
				sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
				first = false;
			}
			url = sb.toString();
		}
		String body = PluginHttp.getText(url, request.method, interpolateAll(request.headers, vars, false),
				interpolateAll(request.form, vars, false), manifest.hosts);

		PluginManifest.Parse parse = variant.parse;
		boolean json = parse != null && "json".equalsIgnoreCase(parse.type);
		if (parse != null && parse.unwrap != null && !parse.unwrap.isBlank()) {
			JsonElement root = PluginJson.GSON.fromJson(body, JsonElement.class);
			JsonElement inner = Extractor.navigate(root, parse.unwrap);
			body = inner == null || inner.isJsonNull() ? ""
					: (inner.isJsonPrimitive() ? inner.getAsString() : inner.toString());
		}

		List<Object> out = new ArrayList<>();
		if (json) {
			JsonElement root = PluginJson.GSON.fromJson(body, JsonElement.class);
			if (root == null) {
				return out;
			}
			JsonElement target = asList && parse.list != null ? Extractor.navigate(root, parse.list) : root;
			if (target == null) {
				return out;
			}
			if (asList && target.isJsonArray()) {
				target.getAsJsonArray().forEach(out::add);
			} else {
				out.add(target);
			}
			return out;
		}
		Document doc = Jsoup.parse(body, url);
		if (asList && parse != null && parse.list != null && !parse.list.isBlank()) {
			for (Element e : doc.select(parse.list)) {
				out.add(e);
			}
		} else {
			out.add(doc);
		}
		return out;
	}

	private PluginManifest.Variant pick(PluginManifest.Block block, Map<String, String> vars) {
		if (block == null) {
			return null;
		}
		for (PluginManifest.Variant variant : block.resolved()) {
			if (variant.request == null || variant.request.url == null) {
				continue;
			}
			if (matches(variant.when, vars)) {
				return variant;
			}
		}
		return null;
	}

	private boolean matches(Map<String, String> when, Map<String, String> vars) {
		if (when == null || when.isEmpty()) {
			return true;
		}
		for (Map.Entry<String, String> e : when.entrySet()) {
			String actual = vars.getOrDefault(e.getKey(), "");
			String expected = e.getValue();
			if ("nonempty".equalsIgnoreCase(expected)) {
				if (actual.isEmpty()) {
					return false;
				}
			} else if ("empty".equalsIgnoreCase(expected)) {
				if (!actual.isEmpty()) {
					return false;
				}
			} else if (!actual.equalsIgnoreCase(expected)) {
				return false;
			}
		}
		return true;
	}

	private Map<String, String> vars(String query, String sort, int page, int pageSize,
									 String tag, String versions, String uuid) {
		Map<String, String> vars = new HashMap<>();
		vars.put("query", query == null ? "" : query);
		vars.put("sort", mapSort(sort));
		vars.put("rawSort", sort == null ? "" : sort);
		vars.put("page", String.valueOf(Math.max(1, page)));
		vars.put("pagesize", String.valueOf(pageSize));
		vars.put("offset", String.valueOf(Math.max(0, page - 1) * Math.max(0, pageSize)));
		vars.put("tag", tag == null ? "" : tag);
		vars.put("versions", versions == null ? "" : versions);
		vars.put("uuid", uuid == null ? "" : uuid);
		return vars;
	}

	private String mapSort(String sort) {
		if (sort == null) {
			return "";
		}
		if (manifest.sortMap != null) {
			String mapped = manifest.sortMap.get(sort);
			if (mapped != null) {
				return mapped;
			}
		}
		return sort;
	}

	private String interpolateUrl(String template, Map<String, String> vars) {
		return substitute(template, vars, true);
	}

	private Map<String, String> interpolateAll(Map<String, String> source, Map<String, String> vars, boolean encode) {
		if (source == null) {
			return null;
		}
		Map<String, String> out = new LinkedHashMap<>();
		source.forEach((k, v) -> out.put(k, substitute(v, vars, encode)));
		return out;
	}

	private String substitute(String template, Map<String, String> vars, boolean encode) {
		if (template == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		int i = 0;
		while (i < template.length()) {
			char c = template.charAt(i);
			if (c == '{') {
				int end = template.indexOf('}', i);
				if (end > i) {
					String key = template.substring(i + 1, end);
					String value = vars.getOrDefault(key, "");
					out.append(encode ? enc(value) : value);
					i = end + 1;
					continue;
				}
			}
			out.append(c);
			i++;
		}
		return out.toString();
	}

	private static String enc(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}

	private static String one(Map<String, List<String>> v, String key) {
		List<String> list = v.get(key);
		return list == null || list.isEmpty() ? "" : list.get(0);
	}

	private static String nullable(Map<String, List<String>> v, String key) {
		String s = one(v, key);
		return s.isEmpty() ? null : s;
	}

	private static String[] many(Map<String, List<String>> v, String key) {
		List<String> list = v.get(key);
		return list == null ? new String[0] : list.toArray(new String[0]);
	}

	private static int intOf(Map<String, List<String>> v, String key) {
		try {
			String s = one(v, key).replaceAll("[^0-9]", "");
			return s.isEmpty() ? 0 : Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static long longOf(Map<String, List<String>> v, String key) {
		try {
			String s = one(v, key).replaceAll("[^0-9]", "");
			return s.isEmpty() ? 0L : Long.parseLong(s);
		} catch (NumberFormatException e) {
			return 0L;
		}
	}
}
