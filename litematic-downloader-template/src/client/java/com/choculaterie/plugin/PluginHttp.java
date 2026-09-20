package com.choculaterie.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class PluginHttp {
	public static final int TIMEOUT_MS = 8000;
	public static final int MAX_REDIRECTS = 3;
	public static final long MAX_RESPONSE_BYTES = 5L * 1024 * 1024;
	public static final long MAX_DOWNLOAD_BYTES = 64L * 1024 * 1024;

	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.connectTimeout(Duration.ofMillis(TIMEOUT_MS))
			.build();

	private PluginHttp() {
	}

	public static String getText(String url, String method, Map<String, String> headers,
								 Map<String, String> form, List<String> allowedHosts) throws IOException {
		return new String(fetch(url, method, headers, form, allowedHosts, MAX_RESPONSE_BYTES), StandardCharsets.UTF_8);
	}

	public static byte[] fetch(String url, String method, Map<String, String> headers,
							   Map<String, String> form, List<String> allowedHosts, long maxBytes) throws IOException {
		String current = url;
		for (int hop = 0; ; hop++) {
			URI uri = validate(current, allowedHosts);
			HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(TIMEOUT_MS));
			if (headers != null) {
				for (Map.Entry<String, String> h : headers.entrySet()) {
					if (isForbiddenHeader(h.getKey())) {
						continue;
					}
					b.header(h.getKey(), h.getValue());
				}
			}
			if ("POST".equalsIgnoreCase(method)) {
				b.header("Content-Type", "application/x-www-form-urlencoded");
				b.POST(HttpRequest.BodyPublishers.ofString(encodeForm(form), StandardCharsets.UTF_8));
			} else {
				b.GET();
			}
			b.header("User-Agent", "LitematicDownloader-Plugin/1.0");

			HttpResponse<InputStream> res;
			try {
				res = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("request interrupted", e);
			}

			int code = res.statusCode();
			if (code >= 300 && code < 400) {
				String location = res.headers().firstValue("location").orElse(null);
				try (InputStream ignored = res.body()) {
				}
				if (location == null) {
					throw new IOException("redirect without Location");
				}
				if (hop >= MAX_REDIRECTS) {
					throw new IOException("too many redirects");
				}
				current = uri.resolve(location).toString();
				continue;
			}
			try (InputStream in = res.body()) {
				if (code >= 400) {
					throw new IOException("HTTP " + code + " from " + uri.getHost());
				}
				return readCapped(in, maxBytes);
			}
		}
	}

	private static byte[] readCapped(InputStream in, long maxBytes) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		long total = 0;
		int n;
		while ((n = in.read(buf)) != -1) {
			total += n;
			if (total > maxBytes) {
				throw new IOException("response exceeds " + maxBytes + " bytes");
			}
			out.write(buf, 0, n);
		}
		return out.toByteArray();
	}

	static String encodeIllegal(String url) {
		StringBuilder out = new StringBuilder(url.length() + 16);
		byte[] bytes = url.getBytes(StandardCharsets.UTF_8);
		for (int i = 0; i < bytes.length; i++) {
			int c = bytes[i] & 0xFF;
			boolean alreadyEscaped = c == '%' && i + 2 < bytes.length
					&& isHex(bytes[i + 1]) && isHex(bytes[i + 2]);
			if (alreadyEscaped || (c > 0x20 && c < 0x7F && c != '"' && c != '<' && c != '>'
					&& c != '\\' && c != '^' && c != '`' && c != '{' && c != '|' && c != '}')) {
				out.append((char) c);
			} else {
				out.append('%').append(String.format("%02X", c));
			}
		}
		return out.toString();
	}

	private static boolean isHex(byte b) {
		char c = (char) (b & 0xFF);
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

	static URI validate(String url, List<String> allowedHosts) throws IOException {
		URI uri;
		try {
			uri = URI.create(encodeIllegal(url));
		} catch (IllegalArgumentException e) {
			throw new IOException("malformed url: " + url);
		}
		String scheme = uri.getScheme();
		if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
			throw new IOException("only http/https is allowed: " + url);
		}
		String host = uri.getHost();
		if (host == null || host.isEmpty()) {
			throw new IOException("url has no host: " + url);
		}
		if (!hostAllowed(host, allowedHosts)) {
			throw new IOException(host + " is not in the plugin's declared hosts");
		}
		InetAddress[] addresses;
		try {
			addresses = InetAddress.getAllByName(host);
		} catch (IOException e) {
			throw new IOException("cannot resolve " + host, e);
		}
		for (InetAddress address : addresses) {
			if (isPrivate(address)) {
				throw new IOException(host + " resolves to a non-public address");
			}
		}
		return uri;
	}

	static boolean hostAllowed(String host, List<String> allowedHosts) {
		if (allowedHosts == null || allowedHosts.isEmpty()) {
			return false;
		}
		String h = host.toLowerCase();
		for (String entry : allowedHosts) {
			if (entry == null || entry.isBlank()) {
				continue;
			}
			String e = entry.trim().toLowerCase();
			if (e.equals("*")) {
				return true;
			}
			if (e.startsWith("*.")) {
				String suffix = e.substring(1);
				if (h.endsWith(suffix) && h.length() > suffix.length()) {
					return true;
				}
			} else if (e.equals(h)) {
				return true;
			}
		}
		return false;
	}

	static boolean isPrivate(InetAddress a) {
		if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress()
				|| a.isSiteLocalAddress() || a.isMulticastAddress()) {
			return true;
		}
		byte[] b = a.getAddress();
		if (b.length == 16) {
			return (b[0] & 0xFE) == 0xFC;
		}
		if (b.length == 4) {
			int first = b[0] & 0xFF;
			int second = b[1] & 0xFF;
			return first == 0 || (first == 100 && second >= 64 && second <= 127);
		}
		return false;
	}

	private static boolean isForbiddenHeader(String name) {
		if (name == null || name.isBlank()) {
			return true;
		}
		String n = name.toLowerCase();
		return n.equals("host") || n.equals("content-length") || n.equals("connection")
				|| n.equals("upgrade") || n.equals("cookie") || n.startsWith("proxy-")
				|| n.startsWith("sec-");
	}

	private static String encodeForm(Map<String, String> form) {
		if (form == null || form.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : form.entrySet()) {
			if (sb.length() > 0) {
				sb.append('&');
			}
			sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
					.append('=')
					.append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8));
		}
		return sb.toString();
	}
}
