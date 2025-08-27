package com.ok.pipeline;

import com.ok.embeddings.*;
import com.ok.store.*;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import java.util.*;


public class GraphBuilder {
  private final GraphStore store;
  private final EmbeddingModel model;

  public GraphBuilder(GraphStore store, EmbeddingModel model) {
    this.store = store;
    this.model = model;
  }

  public static class IngestResult {
    private final List<Vertex> chunkVertices;
    private final List<Vertex> entityVertices;

    public IngestResult(List<Vertex> chunkVertices, List<Vertex> entityVertices) {
      this.chunkVertices = chunkVertices;
      this.entityVertices = entityVertices;
    }

    public List<Vertex> getChunkVertices() {  return chunkVertices; }

    public List<Vertex> getEntityVertices() {  return entityVertices; }
  }

  public IngestResult ingest(String docId, List<String> chunks, EntityExtractor ner) {
    Map<String, Vertex> entityMap = new HashMap<>();
    List<Vertex> chunkVs = new ArrayList<>();

    for (int i = 0; i < chunks.size(); i++) {
      String cid = docId + ":" + i;
      String text = chunks.get(i);

      float[] emb = model.embed(text);
      if (emb.length == 0) {
        System.out.println("Chunk " + cid + " embedding is empty, skipping...");
        continue;
      }

      Vertex c = store.addChunk(cid, text, emb);
      // Ensure embedding is stored on vertex property
      c.property("embedding", emb);
      chunkVs.add(c);

      // Extract entities
      for (String ent : ner.extract(text)) {
        Vertex e = entityMap.computeIfAbsent(ent, k -> store.addEntity(k, "Thing"));
        store.addEdge(c, e, "MENTIONS", Map.of("weight", 1.0));
      }
    }

    return new IngestResult(chunkVs, new ArrayList<>(entityMap.values()));
  }
}