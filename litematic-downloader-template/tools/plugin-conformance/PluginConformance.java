import com.choculaterie.models.MinemevFileInfo;
import com.choculaterie.models.MinemevPostDetailInfo;
import com.choculaterie.models.MinemevPostInfo;
import com.choculaterie.plugin.PluginHttp;
import com.choculaterie.plugin.PluginManifest;
import com.choculaterie.plugin.PluginSource;
import com.google.gson.Gson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public final class PluginConformance {

    private static final int PROBE_MIN_LENGTH = 4;

    enum Status { PASS, FAIL, WARN, SKIP }

    record Check(String name, Status status, String detail) {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: PluginConformance <manifest-dir-or-file> [--only <id>] [--no-download]");
            System.exit(2);
        }
        boolean download = true;
        String only = null;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--no-download")) download = false;
            if (args[i].equals("--only") && i + 1 < args.length) only = args[++i];
        }

        Path root = Path.of(args[0]);
        List<Path> manifests = Files.isDirectory(root)
                ? Files.list(root).filter(p -> p.toString().endsWith(".json")).sorted().toList()
                : List.of(root);

        Map<String, List<Check>> results = new LinkedHashMap<>();
        for (Path manifest : manifests) {
            String id = manifest.getFileName().toString().replace(".json", "");
            if (only != null && !only.equals(id)) continue;
            results.put(id, run(manifest, download));
        }

        report(results);
        boolean failed = results.values().stream().flatMap(List::stream)
                .anyMatch(c -> c.status() == Status.FAIL);
        System.exit(failed ? 1 : 0);
    }

    private static List<Check> run(Path manifestPath, boolean download) {
        List<Check> checks = new ArrayList<>();
        PluginManifest manifest;
        try {
            manifest = new Gson().fromJson(Files.readString(manifestPath), PluginManifest.class);
        } catch (Exception e) {
            checks.add(new Check("MANIFEST", Status.FAIL, "unreadable: " + e.getMessage()));
            return checks;
        }
        if (manifest == null) {
            checks.add(new Check("MANIFEST", Status.FAIL, "empty"));
            return checks;
        }
        String problem = manifest.validate();
        if (problem != null) {
            checks.add(new Check("MANIFEST", Status.FAIL, problem));
            return checks;
        }
        checks.add(new Check("MANIFEST", Status.PASS, manifest.kind + ", hosts=" + manifest.hosts.size()));

        PluginSource source = new PluginSource(manifest);

        List<MinemevPostInfo> page1 = List.of();
        PluginSource.Result r1;
        try {
            r1 = source.search("", "newest", 1, 8, null, null);
            page1 = r1.posts();
            long usable = page1.stream()
                    .filter(p -> notBlank(p.uuid()) && notBlank(p.title()))
                    .count();
            if (page1.isEmpty()) {
                checks.add(new Check("SEARCH", Status.FAIL, "no posts returned"));
            } else if (usable < page1.size()) {
                checks.add(new Check("SEARCH", Status.FAIL,
                        (page1.size() - usable) + "/" + page1.size() + " posts missing uuid or title"));
            } else {
                checks.add(new Check("SEARCH", Status.PASS, page1.size() + " posts"));
            }
        } catch (Exception e) {
            checks.add(new Check("SEARCH", Status.FAIL, msg(e)));
            return checks;
        }

        int totalPages = r1.totalPages();
        if (totalPages < 0) {
            checks.add(new Check("TOTALS", Status.WARN, "declared unknown"));
        } else if (totalPages == 0) {
            checks.add(new Check("TOTALS", Status.FAIL, "totalPages=" + totalPages));
        } else {
            checks.add(new Check("TOTALS", Status.PASS, "pages=" + totalPages + " items=" + r1.totalItems()));
        }

        String probe = probeTerm(page1);
        if (probe == null) {
            checks.add(new Check("QUERY", Status.WARN, "no usable probe word in page 1"));
        } else {
            try {
                List<MinemevPostInfo> hits = source.search(probe, "newest", 1, 8, null, null).posts();
                long matching = hits.stream().filter(p -> contains(p, probe)).count();
                Set<String> baseIds = new HashSet<>();
                for (MinemevPostInfo p : page1) baseIds.add(p.uuid());
                boolean identicalToUnfiltered = !hits.isEmpty() && hits.size() == page1.size()
                        && hits.stream().allMatch(p -> baseIds.contains(p.uuid()));

                if (hits.isEmpty()) {
                    checks.add(new Check("QUERY", Status.FAIL,
                            "\"" + probe + "\" came from a listed title but returned nothing"));
                } else if (identicalToUnfiltered) {
                    checks.add(new Check("QUERY", Status.FAIL,
                            "query ignored: \"" + probe + "\" returned the unfiltered page"));
                } else if (matching == 0) {
                    checks.add(new Check("QUERY", Status.FAIL,
                            "0/" + hits.size() + " results mention \"" + probe + "\""));
                } else {
                    checks.add(new Check("QUERY", Status.PASS,
                            matching + "/" + hits.size() + " match \"" + probe + "\""));
                }
            } catch (Exception e) {
                checks.add(new Check("QUERY", Status.FAIL, msg(e)));
            }
        }

        try {
            List<MinemevPostInfo> page2 = source.search("", "newest", 2, 8, null, null).posts();
            if (page2.isEmpty()) {
                checks.add(new Check("PAGING", Status.WARN, "page 2 empty"));
            } else {
                Set<String> first = new HashSet<>();
                for (MinemevPostInfo p : page1) first.add(p.uuid());
                long overlap = page2.stream().filter(p -> first.contains(p.uuid())).count();
                if (overlap == page2.size()) {
                    checks.add(new Check("PAGING", Status.FAIL, "page 2 identical to page 1"));
                } else {
                    checks.add(new Check("PAGING", Status.PASS,
                            page2.size() + " posts, " + overlap + " overlap"));
                }
            }
        } catch (Exception e) {
            checks.add(new Check("PAGING", Status.FAIL, msg(e)));
        }

        if (page1.isEmpty()) return checks;

        String uuid = stripVendor(page1.get(0).uuid());

        try {
            MinemevPostDetailInfo detail = source.details(uuid);
            if (detail == null || !notBlank(detail.getTitle())) {
                checks.add(new Check("DETAILS", Status.FAIL, "empty title for " + uuid));
            } else {
                checks.add(new Check("DETAILS", Status.PASS, truncate(detail.getTitle(), 28)));
            }
        } catch (Exception e) {
            checks.add(new Check("DETAILS", Status.FAIL, msg(e)));
        }

        MinemevFileInfo file = null;
        try {
            MinemevFileInfo[] files = source.files(uuid);
            if (files.length == 0) {
                checks.add(new Check("FILES", Status.FAIL, "no files for " + uuid));
            } else {
                file = files[0];
                if (!notBlank(file.getDownloadUrl())) {
                    checks.add(new Check("FILES", Status.FAIL, "file has no url"));
                    file = null;
                } else if (!notBlank(file.getDefaultFileName())) {
                    checks.add(new Check("FILES", Status.FAIL, "file has no name"));
                } else {
                    checks.add(new Check("FILES", Status.PASS,
                            files.length + " file(s), " + truncate(file.getDefaultFileName(), 24)));
                }
            }
        } catch (Exception e) {
            checks.add(new Check("FILES", Status.FAIL, msg(e)));
        }

        if (!download) {
            checks.add(new Check("DOWNLOAD", Status.SKIP, "--no-download"));
        } else if (file == null) {
            checks.add(new Check("DOWNLOAD", Status.SKIP, "no file url"));
        } else {
            try {
                byte[] bytes = source.download(file.getDownloadUrl());
                String kind = magicOf(bytes);
                if (kind == null) {
                    checks.add(new Check("DOWNLOAD", Status.FAIL,
                            "not a schematic (" + bytes.length + "B, starts " + preview(bytes) + ")"));
                } else {
                    checks.add(new Check("DOWNLOAD", Status.PASS, bytes.length + "B " + kind));
                }
            } catch (Exception e) {
                checks.add(new Check("DOWNLOAD", Status.FAIL, msg(e)));
            }
        }
        return checks;
    }

    private static String probeTerm(List<MinemevPostInfo> posts) {
        Set<String> skip = Set.of("the", "and", "for", "with", "minecraft", "schematic", "farm", "new");
        for (MinemevPostInfo post : posts) {
            String title = post.title();
            if (title == null) continue;
            for (String word : title.split("[^\\p{L}\\p{N}]+")) {
                String w = word.toLowerCase(Locale.ROOT);
                boolean cjk = word.codePoints().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF);
                if (cjk && word.length() >= 2) return word;
                if (w.length() >= PROBE_MIN_LENGTH && !skip.contains(w)) return word;
            }
        }
        return null;
    }

    private static String stripVendor(String uuid) {
        int slash = uuid.indexOf('/');
        return slash >= 0 ? uuid.substring(slash + 1) : uuid;
    }

    private static boolean contains(MinemevPostInfo post, String term) {
        String t = term.toLowerCase(Locale.ROOT);
        return (post.title() != null && post.title().toLowerCase(Locale.ROOT).contains(t))
                || (post.description() != null && post.description().toLowerCase(Locale.ROOT).contains(t));
    }

    private static String magicOf(byte[] b) {
        if (b == null || b.length < 4) return null;
        int b0 = b[0] & 0xFF, b1 = b[1] & 0xFF;
        if (b0 == 0x1F && b1 == 0x8B) return "gzip";
        if (b0 == 0x0A) return "nbt";
        if (b0 == 'P' && b1 == 'K') return "zip";
        return null;
    }

    private static String preview(byte[] b) {
        int n = Math.min(12, b.length);
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < n; i++) {
            char c = (char) (b[i] & 0xFF);
            sb.append(c >= 32 && c < 127 ? c : '.');
        }
        return sb.append('"').toString();
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String msg(Exception e) {
        String m = e.getMessage();
        return truncate(m == null ? e.getClass().getSimpleName() : m, 60);
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        s = s.replace('\n', ' ').trim();
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    private static void report(Map<String, List<Check>> results) {
        List<String> columns = List.of("MANIFEST", "SEARCH", "TOTALS", "QUERY", "PAGING", "DETAILS", "FILES", "DOWNLOAD");
        System.out.println();
        System.out.printf("%-22s", "PLUGIN");
        for (String c : columns) System.out.printf("%-10s", c);
        System.out.println();
        System.out.println("-".repeat(22 + columns.size() * 10));

        List<String> notes = new ArrayList<>();
        for (var entry : results.entrySet()) {
            Map<String, Check> byName = new LinkedHashMap<>();
            for (Check c : entry.getValue()) byName.put(c.name(), c);
            System.out.printf("%-22s", truncate(entry.getKey(), 21));
            for (String col : columns) {
                Check c = byName.get(col);
                System.out.printf("%-10s", c == null ? "-" : symbol(c.status()));
            }
            System.out.println();
            for (Check c : entry.getValue()) {
                if (c.status() == Status.FAIL || c.status() == Status.WARN) {
                    notes.add(String.format("  %-20s %-9s %s  %s",
                            entry.getKey(), c.name(), symbol(c.status()), c.detail()));
                }
            }
        }

        if (!notes.isEmpty()) {
            System.out.println("\nDETAIL");
            notes.forEach(System.out::println);
        }

        long pass = results.values().stream().flatMap(List::stream).filter(c -> c.status() == Status.PASS).count();
        long fail = results.values().stream().flatMap(List::stream).filter(c -> c.status() == Status.FAIL).count();
        long warn = results.values().stream().flatMap(List::stream).filter(c -> c.status() == Status.WARN).count();
        System.out.printf("%n%d plugins  |  %d pass  %d fail  %d warn%n", results.size(), pass, fail, warn);
    }

    private static String symbol(Status s) {
        return switch (s) {
            case PASS -> "PASS";
            case FAIL -> "FAIL";
            case WARN -> "warn";
            case SKIP -> "skip";
        };
    }
}
