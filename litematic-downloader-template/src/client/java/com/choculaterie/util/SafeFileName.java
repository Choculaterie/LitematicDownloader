package com.choculaterie.util;

import java.util.Locale;
import java.util.Set;

public final class SafeFileName {
	private static final int MAX_LENGTH = 120;
	private static final Set<String> RESERVED = Set.of(
			"con", "prn", "aux", "nul",
			"com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
			"lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

	private SafeFileName() {
	}

	public static String sanitize(String name, String fallback) {
		if (name == null) {
			return fallback;
		}
		String cleaned = name;
		int slash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
		if (slash >= 0) {
			cleaned = cleaned.substring(slash + 1);
		}
		StringBuilder sb = new StringBuilder(cleaned.length());
		for (int i = 0; i < cleaned.length(); i++) {
			char c = cleaned.charAt(i);
			if (c < 0x20 || c == 0x7F) {
				continue;
			}
			sb.append(c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|' ? '_' : c);
		}
		cleaned = sb.toString().trim();
		while (cleaned.startsWith(".")) {
			cleaned = cleaned.substring(1).trim();
		}
		while (cleaned.endsWith(".") || cleaned.endsWith(" ")) {
			cleaned = cleaned.substring(0, cleaned.length() - 1);
		}
		if (cleaned.length() > MAX_LENGTH) {
			cleaned = cleaned.substring(0, MAX_LENGTH);
		}
		if (cleaned.isEmpty()) {
			return fallback;
		}
		int dot = cleaned.lastIndexOf('.');
		String stem = dot > 0 ? cleaned.substring(0, dot) : cleaned;
		if (RESERVED.contains(stem.toLowerCase(Locale.ROOT))) {
			return "_" + cleaned;
		}
		return cleaned;
	}
}
