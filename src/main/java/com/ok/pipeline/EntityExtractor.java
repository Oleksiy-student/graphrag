package com.ok.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Extracts entities and relations from chunks using DeepSeek-R1.
 * Each entity tracks chunk_ids where it appears.
 */
public class EntityExtractor {

    private static final String OLLAMA_URL;
    private static final String MODEL;
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS)
        .enable(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES)
        .enable(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);

    private final HttpClient client = HttpClient.newHttpClient();

    static {
        Properties props = new Properties();
        String url = "http://localhost:11434";
        String model = "deepseek-r1:1.5b";
        try (InputStream in = new FileInputStream("config.properties")) {
            props.load(in);
            url = props.getProperty("OLLAMA_URL", url);
            model = props.getProperty("DEEPSEEK_MODEL", model);
        } catch (IOException e) {
            System.err.println("Failed to load config.properties, using defaults: " + e.getMessage());
        }
        OLLAMA_URL = url;
        MODEL = model;
    }

    public static class Entity {
        private final String name;
        private final String type;
        private final Set<Integer> chunkIds = new HashSet<>(); // integers only

        public Entity(String name, String type) { this.name = name; this.type = type; }
        public String getName() { return name; }
        public String getType() { return type; }
        public Set<Integer> getChunkIds() { return chunkIds; }
        public void addChunkId(int chunkId) { chunkIds.add(chunkId); } // integer only

        @Override
        public String toString() { return name + " (" + type + ") [" + chunkIds + "]"; }
    }



    public static class Relation {
        public final String source;
        public final String target;
        public final String relationType;
        public Relation(String source, String target, String relationType) {
            this.source = source; this.target = target; this.relationType = relationType;
        }
        @Override
        public String toString() {
            return source + " -> " + target + " [" + relationType + "]";
        }
    }

    public static class ExtractionResult {
        private final List<Entity> entities;
        private final List<Relation> relations;
        public ExtractionResult(List<Entity> entities, List<Relation> relations) {
            this.entities = entities; this.relations = relations;
        }
        public List<Entity> getEntities() { return entities; }
        public List<Relation> getRelations() { return relations; }
    }

    public ExtractionResult extractBatch(String docId, List<String> chunks) {
        try {
            Map<String, Entity> entityMap = new HashMap<>();
            List<Relation> relations = new ArrayList<>();

            for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
                String chunkText = chunks.get(chunkIndex);

                String prompt = """
                    You are an information extraction system.
                    Extract all entities and relations from the following chunk.
                    Return JSON only, exactly in this format:

                    {
                      "entities": [
                        {"name": "Alice", "type": "Person", "chunk_ids": [0]},
                        {"name": "Acme Corp", "type": "Organization", "chunk_ids": [0]}
                      ],
                      "relations": [
                        {"source": "Alice", "target": "Acme Corp", "relationType": "works_for"}
                      ]
                    }

                    Rules for entity classification:
                    1. Entities must be one of: Person, Organization, Location, Event, Year, Quantity.
                    2. Flatten multi-line names into spaces.
                    3. Dates, years, or numeric expressions (e.g., 1918, 10, 759, hundreds of thousands) must be typed as "Year" if it's a date/year, or "Quantity" if it is a count/number.
                    4. Textual counts without explicit numbers (e.g., "streamed from the city") should be considered a Quantity.
                    5. Do not create entities of type 'Relation'; instead use the "relations" array.
                    6. Each entity should appear once per chunk; include all chunk_ids where mentioned.
                    7. Relations must reference entities by their exact name and should be typed according to the context (e.g., works_for, eventYear, affectedPopulation, effect, causes, etc.).

                    Examples:

                    Entities:
                    {"name": "Boccaccio’s Florence", "type": "Location", "chunk_ids": [0]}
                    {"name": "1918", "type": "Year", "chunk_ids": [0]}
                    {"name": "759 Philadelphians", "type": "Quantity", "chunk_ids": [0]}

                    Relations:
                    {"source": "Boccaccio’s Florence", "target": "1348", "relationType": "timePlace"}
                    {"source": "Influenza", "target": "1918", "relationType": "eventYear"}
                    {"source": "Influenza", "target": "1000000", "relationType": "affectedPopulation"}

                    Always classify ambiguous numeric phrases or textual counts as Quantity, and years/dates as Year.
                    """ + "\nChunk " + chunkIndex + ":\n<<<\n" + chunkText + "\n>>>";

                // Prepare request
                String body = String.format(
                        "{\"model\":\"%s\",\"prompt\":%s,\"stream\":false}",
                        MODEL, MAPPER.writeValueAsString(prompt)
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(OLLAMA_URL + "/api/generate"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String raw = response.body();

                if (response.statusCode() != 200) {
                    System.err.println("Ollama error: " + raw);
                    continue;
                }

                // Clean JSON
                String cleaned = cleanJsonOutput(raw);

                JsonNode root;
                try {
                    root = MAPPER.readTree(cleaned);
                    if (!root.has("entities")) {
                        ((ObjectNode) root).set("entities", MAPPER.createArrayNode());
                    }
                    if (!root.has("relations") || !root.get("relations").isArray()) {
                        ((ObjectNode) root).set("relations", MAPPER.createArrayNode());
                    }
                } catch (Exception ex) {
                    System.err.println("Failed to parse JSON for chunk " + chunkIndex + ": " + ex.getMessage());
                    continue;
                }

                // Entities
                for (JsonNode n : root.get("entities")) {
                    String rawName = n.path("name").asText(null);
                    String type = n.path("type").asText("Thing");
                    if (rawName == null || rawName.isBlank()) continue;

                    final String name = rawName.replaceAll("[\\n\\r]+", " ").trim();

                    Entity e = entityMap.computeIfAbsent(name, k -> new Entity(name, type));
                    e.addChunkId(chunkIndex);
                }

                // Relations
                for (JsonNode n : root.get("relations")) {
                    String src = n.path("source").asText(null);
                    String tgt = n.path("target").asText(null);
                    String relType = n.path("relationType").asText(null);

                    if (src != null && tgt != null && relType != null) {
                        relations.add(new Relation(src, tgt, relType));
                    }
                }
            }

            // Cross-chunk relations
            try {
                List<Entity> allEntities = new ArrayList<>(entityMap.values());
                String entityJson = MAPPER.writeValueAsString(allEntities);

                String crossPrompt = """
                    You are an information extraction system.
                    I will give you a list of entities extracted from multiple chunks.
                    Infer any relations that span across different chunks.

                    Rules:
                    - Only return JSON in this format:
                      {
                        "relations": [
                          {"source": "Entity1", "target": "Entity2", "relationType": "relation"}
                        ]
                      }
                    - Use exact entity names from the list.
                    - Only include relations that connect entities from different chunks.
                    - If none exist, return {"relations": []}.
                    """ + "\nEntities:\n" + entityJson;

                String body = String.format(
                        "{\"model\":\"%s\",\"prompt\":%s,\"stream\":false}",
                        MODEL, MAPPER.writeValueAsString(crossPrompt)
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(OLLAMA_URL + "/api/generate"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String cleaned = cleanJsonOutput(response.body());

                JsonNode root = MAPPER.readTree(cleaned);
                if (root.has("relations")) {
                    for (JsonNode n : root.get("relations")) {
                        String src = n.path("source").asText(null);
                        String tgt = n.path("target").asText(null);
                        String relType = n.path("relationType").asText(null);

                        if (src != null && tgt != null && relType != null) {
                            relations.add(new Relation(src, tgt, relType));
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("Cross-chunk relation extraction failed: " + ex.getMessage());
            }

            // Debug
            // System.out.println("=== Extracted Entities ===");
            // entityMap.values().forEach(System.out::println);
            // System.out.println("=== Extracted Relations ===");
            // relations.forEach(System.out::println);

            return new ExtractionResult(new ArrayList<>(entityMap.values()), relations);

        } catch (Exception e) {
            e.printStackTrace();
            return new ExtractionResult(Collections.emptyList(), Collections.emptyList());
        }
    }

    /** Cleans messy LLM output into valid JSON */
    private String cleanJsonOutput(String raw) {
        if (raw == null || raw.isBlank()) return "{}";
        try {
            // Try to unwrap {"response": "..."} but don't crash if not JSON yet
            try {
                JsonNode outer = MAPPER.readTree(raw);
                if (outer.has("response")) raw = outer.get("response").asText();
            } catch (Exception ignore) {}

            // Strip code fences, comments, tags, ellipses
            raw = raw.replaceAll("(?i)<think>", "")
                    .replaceAll("(?i)</think>", "")
                    .replaceAll("(?s)```json.*?\\n", "")
                    .replaceAll("(?s)```", "")
                    .replaceAll("(?m)^\\s*//.*$", "")
                    .replaceAll("//.*(?=[\\n\\r])", "")
                    .replaceAll("/\\*.*?\\*/", "")
                    .replaceAll("\\.\\.\\.", "");

            // Unwrap double-encoded JSON if present
            if (raw.startsWith("\"{") && raw.endsWith("}\"")) {
                try { raw = MAPPER.readValue(raw, String.class); } catch (Exception ignore) {}
            }

            // Normalize quotes
            raw = raw.replace("“", "\"").replace("”", "\"")
                    .replace("‘", "'").replace("’", "'");

            // Only add quotes if not already quoted
            raw = raw.replaceAll("(?<=\\{|,)(\\s*)(?!\")(\\w+)\\s*:", "$1\"$2\":");

            // Ensure values are quoted if they look like bare words
            raw = quoteUnquotedStringValues(raw);

            // Trim to the first complete top-level JSON object
            raw = trimToTopLevelJsonObject(raw);

            // Remove bare commentary after commas outside strings (e.g., ", Wait, ...")
            raw = stripBareTextAfterCommasOutsideStrings(raw);

            // Kill trailing commas
            raw = raw.replaceAll(",\\s*([}\\]])", "$1");

            // Final sanity: if it still doesn't parse, try a greedy backward cut
            try {
                MAPPER.readTree(raw);
            } catch (Exception firstFail) {
                int last = raw.length();
                while (last > 0) {
                    int i = raw.lastIndexOf('}', last - 1);
                    if (i <= 0) break;
                    String candidate = raw.substring(0, i + 1);
                    try {
                        MAPPER.readTree(candidate);
                        raw = candidate;
                        break;
                    } catch (Exception ignore) {
                        last = i;
                    }
                }
            }

            return raw.trim();
        } catch (Exception e) {
            System.err.println("Failed to clean LLM output: " + e.getMessage());
            return "{}";
        }
    }

    /** Returns substring containing ONLY the first complete top-level JSON object. */
    private String trimToTopLevelJsonObject(String s) {
        int start = s.indexOf('{');
        if (start < 0) return "{}";
        int depth = 0;
        boolean inStr = false, esc = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (esc) { esc = false; }
                else if (c == '\\') { esc = true; }
                else if (c == '"') { inStr = false; }
            } else {
                if (c == '"') { inStr = true; }
                else if (c == '{') { depth++; }
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return s.substring(start, i + 1);
                }
            }
        }
        // If we never closed, fallback to from { to last }
        int end = s.lastIndexOf('}');
        return (end > start) ? s.substring(start, end + 1) : "{}";
    }

    /** Strips free-text inserted after commas when outside strings (e.g., ", Wait, ..."). */
    private String stripBareTextAfterCommasOutsideStrings(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean inStr = false, esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                out.append(c);
                if (esc) { esc = false; }
                else if (c == '\\') { esc = true; }
                else if (c == '"') { inStr = false; }
                continue;
            }
            if (c == '"') {
                inStr = true;
                out.append(c);
                continue;
            }
            if (c == ',') {
                out.append(c);
                // copy whitespace
                int j = i + 1;
                while (j < s.length() && Character.isWhitespace(s.charAt(j))) {
                    out.append(s.charAt(j));
                    j++;
                }
                if (j < s.length()) {
                    char nxt = s.charAt(j);
                    // valid next tokens after a comma outside strings
                    boolean valid = (nxt == '{' || nxt == '[' || nxt == '"' ||
                                    nxt == '}' || nxt == ']' ||
                                    nxt == 't' || nxt == 'f' || nxt == 'n' || // true/false/null
                                    nxt == '-' || Character.isDigit(nxt));
                    if (!valid) {
                        // skip until next structural/token start
                        while (j < s.length()) {
                            char k = s.charAt(j);
                            if (k == '{' || k == '[' || k == '"' || k == '}' || k == ']'
                                    || k == 't' || k == 'f' || k == 'n' || k == '-' || Character.isDigit(k)) {
                                break;
                            }
                            j++;
                        }
                    }
                    i = j - 1; // continue from the token we stopped at (loop will ++)
                }
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /** Fixes unquoted string values like {"source": Steamship} → {"source": "Steamship"} */
    private String quoteUnquotedStringValues(String s) {
        return s.replaceAll("(:\\s*)([A-Za-z][^,\"}\\]]*)", "$1\"$2\"");
    }

}
