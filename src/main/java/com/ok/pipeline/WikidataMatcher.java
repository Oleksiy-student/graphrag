package com.ok.pipeline;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;

public class WikidataMatcher {
    private static final String SPARQL_ENDPOINT = "https://query.wikidata.org/sparql";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    public static class Match {
        private final String id;        // Qxxx
        private final String label;     // Paris
        private final String type;      // City
        private final List<Relation> relations;

        public Match(String id, String label, String type, List<Relation> relations) {
            this.id = id; this.label = label; this.type = type; this.relations = relations;
        }
        public String getId() { return id; }
        public String getLabel() { return label; }
        public String getType() { return type; }
        public List<Relation> getRelations() { return relations; }
    }

    public static class Relation {
        public final String relationType;  
        public final String targetId;
        public final String targetLabel;
        public final String targetType;

        public Relation(String relationType, String targetId, String targetLabel, String targetType) {
            this.relationType = relationType;
            this.targetId = targetId;
            this.targetLabel = targetLabel;
            this.targetType = targetType;
        }
    }

    public Match match(String term) {
        try {
            String sparql = String.format("""
                SELECT ?item ?itemLabel ?typeLabel ?rel ?relLabel ?target ?targetLabel ?targetTypeLabel
                WHERE {
                  ?item rdfs:label "%s"@en.
                  OPTIONAL { ?item wdt:P31 ?type. ?type rdfs:label ?typeLabel FILTER(LANG(?typeLabel)="en") }
                  OPTIONAL { ?item ?rel ?target.
                             ?rel rdfs:label ?relLabel FILTER(LANG(?relLabel)="en").
                             ?target rdfs:label ?targetLabel FILTER(LANG(?targetLabel)="en").
                             OPTIONAL { ?target wdt:P31 ?targetType. ?targetType rdfs:label ?targetTypeLabel FILTER(LANG(?targetTypeLabel)="en") }
                           }
                  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
                }
                LIMIT 20
            """, term);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(SPARQL_ENDPOINT + "?query=" + java.net.URLEncoder.encode(sparql, "UTF-8")))
                    .header("Accept", "application/sparql-results+json")
                    .GET()
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                return new Match("LOCAL:" + term, term, "Thing", List.of());
            }

            JsonNode root = MAPPER.readTree(res.body()).path("results").path("bindings");
            if (!root.isArray() || root.isEmpty()) {
                return new Match("LOCAL:" + term, term, "Thing", List.of());
            }

            JsonNode first = root.get(0);
            String id = extractId(first.path("item").path("value").asText());
            String label = first.path("itemLabel").path("value").asText(term);
            String type = first.has("typeLabel") ? first.path("typeLabel").path("value").asText("Thing") : "Thing";

            List<Relation> rels = new ArrayList<>();
            for (JsonNode node : root) {
                if (node.has("relLabel") && node.has("target")) {
                    String relLabel = node.path("relLabel").path("value").asText();
                    String targetId = extractId(node.path("target").path("value").asText());
                    String targetLabel = node.path("targetLabel").path("value").asText();
                    String targetType = node.has("targetTypeLabel") ? node.path("targetTypeLabel").path("value").asText("Thing") : "Thing";
                    rels.add(new Relation(relLabel, targetId, targetLabel, targetType));
                }
            }

            return new Match(id, label, type, rels);

        } catch (Exception e) {
            return new Match("LOCAL:" + term, term, "Thing", List.of());
        }
    }

    private String extractId(String uri) {
        int idx = uri.lastIndexOf('/');
        return (idx >= 0) ? uri.substring(idx + 1) : uri;
    }
}
