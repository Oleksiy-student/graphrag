package com.ok.pipeline;

import com.ok.embeddings.EmbeddingModel;
import com.ok.store.GraphStore;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * GraphBuilder handles ingesting document chunks into a graph,
 * extracting entities, linking them to chunks, and adding inter-entity relations from Wikidata.
 */
public class GraphBuilder {

    private final GraphStore store;
    private final EmbeddingModel model;
    private final WikidataMatcher wikidata;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public GraphBuilder(GraphStore store, EmbeddingModel model, WikidataMatcher wikidata) {
        this.store = store;
        this.model = model;
        this.wikidata = wikidata;
    }

    public static class IngestResult {
        private final List<Vertex> chunkVertices;
        private final List<Vertex> entityVertices;

        public IngestResult(List<Vertex> chunkVertices, List<Vertex> entityVertices) {
            this.chunkVertices = chunkVertices;
            this.entityVertices = entityVertices;
        }

        public List<Vertex> getChunkVertices() { return chunkVertices; }
        public List<Vertex> getEntityVertices() { return entityVertices; }
    }

    /**
     * Ingest document chunks, extract entities, link to chunks, and add Wikidata relations.
     *
     * @param docId Document ID
     * @param chunks List of chunk texts
     * @param ner Entity extractor
     * @param wikidata Wikidata matcher
     * @return IngestResult with chunk and entity vertices
     */
    public IngestResult ingest(String docId, List<String> chunks, EntityExtractor ner, WikidataMatcher wikidata) {
        Map<String, Vertex> entityMap = new HashMap<>();
        Map<String, Set<String>> entityToChunkIds = new HashMap<>();
        List<Vertex> chunkVertices = new ArrayList<>();

        // 1. Process chunks and extract entities
        for (int i = 0; i < chunks.size(); i++) {
            String chunkId = docId + ":" + i;
            String text = chunks.get(i);

            float[] emb = model.embed(text);
            if (emb.length == 0) {
                System.out.println("Skipping empty embedding for chunk " + chunkId);
                continue;
            }

            Vertex chunkV = store.addChunk(chunkId, text, emb);
            chunkV.property("embedding", emb);
            chunkVertices.add(chunkV);

            for (String ent : ner.extract(text)) {
                Vertex entityV = entityMap.computeIfAbsent(ent, name -> store.addEntity(name, "Thing"));
                entityToChunkIds.computeIfAbsent(ent, k -> new HashSet<>()).add(chunkId);

                store.addEdge(chunkV, entityV, "MENTIONS", Map.of("weight", 1.0));
            }
        }

        // 2. Set 'chunks' property on entity vertices
        entityToChunkIds.forEach((entityName, chunkSet) -> {
            Vertex e = entityMap.get(entityName);
            try {
                e.property("chunks", MAPPER.writeValueAsString(new ArrayList<>(chunkSet)));
            } catch (Exception ex) {
                System.err.println("Failed to set chunks property for entity " + entityName + ": " + ex.getMessage());
            }
        });

        // 3. Add inter-entity edges from Wikidata
        try {
            Map<String, WikidataMatcher.Match> matches = new HashMap<>();
            for (String entityName : entityMap.keySet()) {
                matches.put(entityName, wikidata.match(entityName));
            }

            for (WikidataMatcher.Match match : matches.values()) {
                for (WikidataMatcher.Relation rel : match.getRelations()) {
                    // Only add edge if both entities exist in our graph
                    Vertex from = entityMap.get(match.getLabel());
                    Vertex to = entityMap.get(rel.targetLabel);
                    if (from != null && to != null) {
                        store.addEdge(from, to, rel.relationType, Map.of("wikidata", true));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to add Wikidata relations: " + e.getMessage());
        }

        return new IngestResult(chunkVertices, new ArrayList<>(entityMap.values()));
    }
}
