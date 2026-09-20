import com.choculaterie.models.MinemevPostInfo;
import com.choculaterie.models.MinemevSearchResponse;
import com.choculaterie.network.MinemevParsers;
import com.choculaterie.plugin.PluginManifest;
import com.choculaterie.plugin.PluginSource;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MixConformance {

    private static final String API = "https://www.minemev.com/api";
    private static final int PAGE_SIZE = 8;
    private static final int PAGES = 3;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    record Outcome(String id, String mode, List<Integer> counts, List<Integer> repeats,
                   List<Integer> pages, String note) {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: MixConformance <manifest-dir> [--query <term>] [--only <id>]");
            System.exit(2);
        }
        String query = "house";
        String only = null;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--query") && i + 1 < args.length) query = args[++i];
            if (args[i].equals("--only") && i + 1 < args.length) only = args[++i];
        }

        Path root = Path.of(args[0]);
        List<Path> manifests = Files.isDirectory(root)
                ? Files.list(root).filter(p -> p.toString().endsWith(".json")).sorted().toList()
                : List.of(root);

        Set<String> apiVendors = new HashSet<>();
        try {
            for (String v : MinemevParsers.parseVendorList(get(API + "/vendors"))) {
                apiVendors.add(v.toLowerCase(Locale.ROOT));
            }
        } catch (Exception e) {
            System.out.println("could not read the vendor list: " + e.getMessage());
        }

        System.out.println("query \"" + query + "\", " + PAGE_SIZE + " per page, pages 1.." + PAGES);
        System.out.println();

        Outcome apiOnly = walkApiOnly(query);
        List<Outcome> rows = new ArrayList<>();
        rows.add(apiOnly);

        Gson gson = new Gson();
        for (Path manifest : manifests) {
            String id = manifest.getFileName().toString().replace(".json", "");
            if (only != null && !only.equals(id)) continue;
            PluginManifest parsed;
            try {
                parsed = gson.fromJson(Files.readString(manifest), PluginManifest.class);
                if (parsed == null || parsed.validate() != null) {
                    rows.add(new Outcome(id, "-", List.of(), List.of(), List.of(), "manifest rejected"));
                    continue;
                }
            } catch (Exception e) {
                rows.add(new Outcome(id, "-", List.of(), List.of(), List.of(), "manifest unreadable"));
                continue;
            }
            PluginSource source = new PluginSource(parsed);
            rows.add(walk(id, source, parsed, query, apiVendors, false));
            rows.add(walk(id, source, parsed, query, apiVendors, true));
        }

        report(rows);
    }

    private static Outcome walkApiOnly(String query) {
        List<Integer> counts = new ArrayList<>();
        List<Integer> repeats = new ArrayList<>();
        List<Integer> pages = new ArrayList<>();
        Set<String> across = new HashSet<>();
        String note = "";
        for (int page = 1; page <= PAGES; page++) {
            try {
                MinemevSearchResponse api = fetchApi(query, page, PAGE_SIZE, "");
                int repeat = 0;
                int fresh = 0;
                for (MinemevPostInfo post : api.posts()) {
                    if (across.add(post.uuid())) fresh++; else repeat++;
                }
                counts.add(fresh);
                repeats.add(repeat);
                pages.add(api.totalPages());
            } catch (Exception e) {
                counts.add(-1);
                repeats.add(0);
                pages.add(0);
                note = shorten(e.getMessage());
                break;
            }
        }
        return new Outcome("(api alone)", "api", counts, repeats, pages, note);
    }

    private static Outcome walk(String id, PluginSource source, PluginManifest manifest, String query,
                                Set<String> apiVendors, boolean mixed) {
        int pluginCount = 1;
        String vendor = manifest.vendor != null && !manifest.vendor.isBlank() ? manifest.vendor : manifest.id;
        String exclude = apiVendors.contains(vendor.toLowerCase(Locale.ROOT)) ? vendor : "";
        int activeApiVendors = mixed ? Math.max(0, apiVendors.size() - (exclude.isEmpty() ? 0 : 1)) : 0;
        int apiStreams = activeApiVendors == 0 ? 0 : 1;
        int slice = Math.max(1, PAGE_SIZE / Math.max(1, apiStreams + pluginCount));
        int apiSize = activeApiVendors == 0 ? 0 : Math.max(1, PAGE_SIZE - slice * pluginCount);

        List<Integer> counts = new ArrayList<>();
        List<Integer> repeats = new ArrayList<>();
        List<Integer> pages = new ArrayList<>();
        Set<String> across = new HashSet<>();
        String note = "";

        for (int page = 1; page <= PAGES; page++) {
            List<MinemevPostInfo> merged = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            int totalPages = 1;
            boolean filled = false;

            if (apiSize > 0) {
                try {
                    MinemevSearchResponse api = fetchApi(query, page, apiSize, exclude);
                    totalPages = Math.max(totalPages, api.totalPages());
                    if (api.totalPages() <= 0 || page <= api.totalPages()) {
                        addNew(merged, seen, Arrays.asList(api.posts()));
                    }
                } catch (Exception e) {
                    note = "api leg: " + shorten(e.getMessage());
                }
            }

            try {
                PluginSource.Result result = source.search(query, "newest", page, slice, null, null);
                boolean spent = result.totalPages() > 0 && page > result.totalPages();
                int added = addNew(merged, seen, spent ? List.of() : result.posts());
                if (result.totalPages() > 0) {
                    totalPages = Math.max(totalPages, result.totalPages());
                } else if (added >= slice) {
                    filled = true;
                }
            } catch (Exception e) {
                note = shorten(e.getMessage());
                counts.add(-1);
                repeats.add(0);
                pages.add(0);
                break;
            }

            if (filled) totalPages = Math.max(totalPages, page + 1);

            int repeat = 0;
            int fresh = 0;
            for (MinemevPostInfo post : merged) {
                if (across.add(post.uuid())) fresh++; else repeat++;
            }
            counts.add(fresh);
            repeats.add(repeat);
            pages.add(totalPages);
        }
        return new Outcome(id, mixed ? "mixed" : "alone", counts, repeats, pages, note);
    }

    private static int addNew(List<MinemevPostInfo> merged, Set<String> seen, List<MinemevPostInfo> posts) {
        int added = 0;
        for (MinemevPostInfo post : posts) {
            String key = post.uuid();
            if (key == null || key.isEmpty() || seen.add(key)) {
                merged.add(post);
                added++;
            }
        }
        return added;
    }

    private static MinemevSearchResponse fetchApi(String query, int page, int pageSize, String exclude)
            throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(API + "/search?clean_uuid=1&sort=newest")
                .append("&search=").append(URLEncoder.encode(query, StandardCharsets.UTF_8))
                .append("&page=").append(page)
                .append("&pagesize=").append(pageSize);
        if (!exclude.isEmpty()) {
            url.append("&exclude_vendor=").append(URLEncoder.encode(exclude, StandardCharsets.UTF_8));
        }
        return MinemevParsers.parseSearchResponse(get(url.toString()));
    }

    private static String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "LitematicDownloader-conformance")
                .timeout(Duration.ofSeconds(20))
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static String shorten(String s) {
        if (s == null) return "failed";
        return s.length() > 48 ? s.substring(0, 48) + "..." : s;
    }

    private static void report(List<Outcome> rows) {
        System.out.printf("%-22s %-6s %-18s %-14s %-16s %s%n",
                "PLUGIN", "MODE", "NEW PER PAGE", "REPEATS", "PAGES REPORTED", "NOTE");
        for (Outcome o : rows) {
            System.out.printf("%-22s %-6s %-18s %-14s %-16s %s%n",
                    o.id(), o.mode(), o.counts(), o.repeats(), o.pages(), o.note());
        }
        System.out.println();

        List<String> repeating = new ArrayList<>();
        List<String> dry = new ArrayList<>();
        for (Outcome o : rows) {
            if (o.repeats().stream().anyMatch(n -> n > 0)) repeating.add(o.id() + " " + o.mode());
            if (o.counts().size() > 1 && o.counts().subList(1, o.counts().size()).stream().allMatch(n -> n == 0)) {
                dry.add(o.id() + " " + o.mode());
            }
        }
        System.out.println("repeats an earlier page: " + (repeating.isEmpty() ? "none" : String.join(", ", repeating)));
        System.out.println("nothing new after page 1: " + (dry.isEmpty() ? "none" : String.join(", ", dry)));
    }
}
