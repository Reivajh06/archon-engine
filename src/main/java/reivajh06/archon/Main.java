package reivajh06.archon;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import reivajh06.plugins.PluginConfiguration;
import reivajh06.plugins.PluginResponse;

/** Archchess line protocol, plus an allocation-free recursive perft debugger. */
public final class Main {
    private static final String PREFIX = "$archchess:";
    private Main() {}
    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        ArchonPlugin plugin = new ArchonPlugin(); plugin.start();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            line = line.strip(); if (line.equals("exit")) break;
            try {
                if (line.equals("name")) out.println(PREFIX + plugin.name());
                else if (line.startsWith("move ")) out.println(PREFIX + withDetails(plugin.requestMove(line.substring(5).strip())));
                else if (line.startsWith("config ")) {
                    String[] p = line.split("\\s+");
                    if (p.length < 2 || p.length > 3) throw new IllegalArgumentException("invalid config");
                    var difficulty = PluginConfiguration.Difficulty.valueOf(p[1].toUpperCase(Locale.ROOT));
                    long ms = p.length == 3 ? Long.parseLong(p[2]) : 0;
                    out.println(PREFIX + toJson(Map.of("ok", String.valueOf(plugin.sendParameters(new PluginConfiguration(difficulty, ms))))));
                } else if (line.startsWith("perft ")) {
                    String[] p = line.split("\\s+", 3); int depth = Integer.parseInt(p[1]);
                    Board b = new Board(p.length == 3 ? p[2] : Board.START_FEN);
                    long start = System.nanoTime(), nodes = b.perft(depth), nanos = Math.max(1, System.nanoTime() - start);
                    Map<String, String> result = new LinkedHashMap<>();
                    result.put("depth", Integer.toString(depth)); result.put("nodes", Long.toString(nodes));
                    result.put("time", Long.toString(nanos / 1_000_000)); result.put("nps", Long.toString((long) (nodes * 1e9 / nanos)));
                    out.println(PREFIX + toJson(result));
                } else if (!line.isEmpty()) out.println(PREFIX + toJson(Map.of("error", "unknown command")));
            } catch (RuntimeException e) { out.println(PREFIX + toJson(Map.of("error", String.valueOf(e.getMessage())))); }
        }
        plugin.stop();
    }
    private static String toJson(Map<String, String> map) {
        return map.entrySet().stream().map(e -> "\"" + escape(e.getKey()) + "\":\"" + escape(e.getValue()) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }
    private static String withDetails(PluginResponse response) {
        String json = toJson(response.message());
        if (response.details() == null || response.details().isEmpty()) return json;
        return json.substring(0, json.length() - 1) + ",\"details\":" + toJson(response.details()) + "}";
    }
    private static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) switch (c) {
            case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\");
            case '\n' -> b.append("\\n"); case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t");
            default -> { if (c < 32) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
        }
        return b.toString();
    }
}
