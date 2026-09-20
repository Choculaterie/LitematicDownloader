package com.choculaterie.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Extractor {
	private static final int MAX_REGEX_INPUT = 512 * 1024;
	private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

	private Extractor() {
	}

	static Map<String, List<String>> extract(Object node, Map<String, PluginManifest.Field> fields,
											 Map<String, String> context) {
		Map<String, List<String>> out = new LinkedHashMap<>();
		if (context != null) {
			context.forEach((k, v) -> out.put(k, List.of(v)));
		}
		if (fields == null) {
			return out;
		}
		fields.forEach((key, field) -> {
			if (field != null && field.template == null) {
				out.put(key, resolve(node, field));
			}
		});
		fields.forEach((key, field) -> {
			if (field != null && field.template != null) {
				String value = interpolate(field.template, out);
				out.put(key, value.isEmpty() ? List.of() : List.of(value));
			}
		});
		return out;
	}

	private static List<String> resolve(Object node, PluginManifest.Field field) {
		List<String> values = raw(node, field);
		if (field.regex != null && !field.regex.isBlank()) {
			List<String> matched = new ArrayList<>();
			Pattern p = pattern(field.regex);
			boolean everyMatch = Boolean.TRUE.equals(field.all);
			if (p != null) {
				for (String v : values) {
					String input = v.length() > MAX_REGEX_INPUT ? v.substring(0, MAX_REGEX_INPUT) : v;
					Matcher m = p.matcher(input);
					while (m.find()) {
						matched.add(m.groupCount() >= 1 && m.group(1) != null ? m.group(1) : m.group());
						if (!everyMatch) {
							break;
						}
					}
				}
			}
			values = matched;
		}
		if (field.split != null && !field.split.isEmpty()) {
			List<String> parts = new ArrayList<>();
			for (String v : values) {
				for (String part : v.split(Pattern.quote(field.split))) {
					String t = part.trim();
					if (!t.isEmpty()) {
						parts.add(t);
					}
				}
			}
			values = parts;
		}
		values = values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
		if (field.prefix != null || field.suffix != null) {
			String prefix = field.prefix == null ? "" : field.prefix;
			String suffix = field.suffix == null ? "" : field.suffix;
			values = values.stream().map(v -> prefix + v + suffix).toList();
		}
		if (values.isEmpty() && field.fallback != null) {
			return List.of(field.fallback);
		}
		return values;
	}

	private static List<String> raw(Object node, PluginManifest.Field field) {
		if (node instanceof Element element) {
			return fromHtml(element, field);
		}
		if (node instanceof JsonElement json) {
			return fromJson(json, field);
		}
		return List.of();
	}

	private static List<String> fromHtml(Element element, PluginManifest.Field field) {
		List<Element> targets = new ArrayList<>();
		if (field.select == null || field.select.isBlank()) {
			targets.add(element);
		} else if (Boolean.TRUE.equals(field.all)) {
			Elements found = element.select(field.select);
			targets.addAll(found);
		} else {
			Element first = element.selectFirst(field.select);
			if (first != null) {
				targets.add(first);
			}
		}
		List<String> values = new ArrayList<>();
		for (Element target : targets) {
			String value = valueOf(target, field);
			if (value != null && !value.isBlank()) {
				values.add(value);
			}
		}
		return values;
	}

	private static String valueOf(Element target, PluginManifest.Field field) {
		if (Boolean.TRUE.equals(field.raw)) {
			String data = target.data();
			return data.isEmpty() ? target.html() : data;
		}
		for (String attr : attrNames(field)) {
			String v = attr.equals("href") || attr.equals("src")
					? target.absUrl(attr)
					: target.attr(attr);
			if (v != null && !v.isBlank()) {
				return v;
			}
			v = target.attr(attr);
			if (v != null && !v.isBlank()) {
				return v;
			}
		}
		return target.text();
	}

	private static List<String> attrNames(PluginManifest.Field field) {
		if (field.attr == null || Boolean.TRUE.equals(field.text)) {
			return List.of();
		}
		if (field.attr.isJsonArray()) {
			List<String> names = new ArrayList<>();
			for (JsonElement e : field.attr.getAsJsonArray()) {
				if (e.isJsonPrimitive()) {
					names.add(e.getAsString());
				}
			}
			return names;
		}
		return field.attr.isJsonPrimitive() ? List.of(field.attr.getAsString()) : List.of();
	}

	private static List<String> fromJson(JsonElement json, PluginManifest.Field field) {
		JsonElement target = navigate(json, field.path);
		if (target == null || target.isJsonNull()) {
			return List.of();
		}
		if (target.isJsonArray()) {
			List<String> values = new ArrayList<>();
			for (JsonElement e : target.getAsJsonArray()) {
				String s = asString(e);
				if (s != null && !s.isBlank()) {
					values.add(s);
				}
				if (!Boolean.TRUE.equals(field.all)) {
					break;
				}
			}
			return values;
		}
		String s = asString(target);
		return s == null || s.isBlank() ? List.of() : List.of(s);
	}

	static JsonElement navigate(JsonElement root, String path) {
		if (path == null || path.isBlank()) {
			return root;
		}
		JsonElement current = root;
		for (String segment : path.split("\\.")) {
			if (current == null || current.isJsonNull()) {
				return null;
			}
			if (segment.isEmpty()) {
				continue;
			}
			if (current.isJsonArray()) {
				JsonArray array = current.getAsJsonArray();
				int index;
				try {
					index = Integer.parseInt(segment);
				} catch (NumberFormatException e) {
					return null;
				}
				if (index < 0 || index >= array.size()) {
					return null;
				}
				current = array.get(index);
			} else if (current.isJsonObject()) {
				JsonObject object = current.getAsJsonObject();
				if (!object.has(segment)) {
					return null;
				}
				current = object.get(segment);
			} else {
				return null;
			}
		}
		return current;
	}

	private static String asString(JsonElement e) {
		if (e == null || e.isJsonNull()) {
			return null;
		}
		return e.isJsonPrimitive() ? e.getAsString() : e.toString();
	}

	static String interpolate(String template, Map<String, List<String>> values) {
		StringBuilder out = new StringBuilder();
		int i = 0;
		while (i < template.length()) {
			char c = template.charAt(i);
			if (c == '{') {
				int end = template.indexOf('}', i);
				if (end > i) {
					String key = template.substring(i + 1, end);
					List<String> v = values.get(key);
					if (v == null || v.isEmpty()) {
						return "";
					}
					out.append(v.get(0));
					i = end + 1;
					continue;
				}
			}
			out.append(c);
			i++;
		}
		return out.toString();
	}

	private static Pattern pattern(String regex) {
		return PATTERNS.computeIfAbsent(regex, r -> {
			try {
				return Pattern.compile(r);
			} catch (Exception e) {
				System.err.println("[Plugin] invalid regex: " + r);
				return null;
			}
		});
	}
}
