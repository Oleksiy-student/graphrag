package com.ok.pipeline;

import com.ok.embeddings.EmbeddingModel;
import com.ok.store.GraphStore;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * GraphBuilder: adds entities and relations only (no chunk nodes),
 * each entity keeps chunk_ids of chunks mentioning it.
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
        private final List<Vertex> entityVertices;
        private final List<Vertex> relationEdges; // edges stored as vertices for debug
        public IngestResult(List<Vertex> entityVertices, List<Vertex> relationEdges) {
            this.entityVertices = entityVertices; this.relationEdges = relationEdges;
        }
        public List<Vertex> getEntityVertices() { return entityVertices; }
        public List<Vertex> getRelationEdges() { return relationEdges; }
    }

    public GraphBuilder.IngestResult ingest(String docId, List<String> chunks, EntityExtractor ner) {
        Map<String, Vertex> entityMap = new HashMap<>();
        Map<String, EntityExtractor.Entity> globalEntities = new HashMap<>();
        List<EntityExtractor.Relation> globalRelations = new ArrayList<>();

        int batchSize = 5;

        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(start + batchSize, chunks.size());
            List<String> batch = chunks.subList(start, end);

            // Extract entities + relations
            EntityExtractor.ExtractionResult batchResult = ner.extractBatch(docId, batch);

            // Merge batch entities
            for (EntityExtractor.Entity e : batchResult.getEntities()) {
                EntityExtractor.Entity global = globalEntities.computeIfAbsent(
                        e.getName(),
                        k -> new EntityExtractor.Entity(e.getName(), e.getType())
                );
                global.getChunkIds().addAll(e.getChunkIds());
            }

            globalRelations.addAll(batchResult.getRelations());
        }

        // Create vertices
        List<Vertex> entityVertices = new ArrayList<>();
        for (EntityExtractor.Entity e : globalEntities.values()) {
            Vertex v = store.addEntity(e.getName(), e.getType());
            try {
                v.property("chunk_ids", new ArrayList<>(e.getChunkIds()));
            } catch (Exception ex) {
                System.err.println("Failed to set chunk_ids for entity " + e.getName() + ": " + ex.getMessage());
            }
            entityMap.put(e.getName(), v);
            entityVertices.add(v);
            System.out.println("Added entity: " + e);
        }

        // Add edges, auto-create missing vertices
        for (EntityExtractor.Relation r : globalRelations) {
            Vertex from = entityMap.computeIfAbsent(r.source, 
                k -> store.addEntity(r.source, "Thing"));
            Vertex to = entityMap.computeIfAbsent(r.target, 
                k -> store.addEntity(r.target, "Thing"));

            store.addEdge(from, to, r.relationType, Map.of("extracted", true));
            System.out.println("Added relation: " + r);
        }

        return new IngestResult(entityVertices, Collections.emptyList());
    }


}
