package com.ok.pipeline;

import com.ok.embeddings.*;
import com.ok.store.*;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import java.util.*;

public class Retriever {
  private final GraphStore store;
  private final EmbeddingModel model;

  public Retriever(GraphStore store, EmbeddingModel model) {
    this.store = store;
    this.model = model;
  }

  public static class Hit {
        public final String chunkId;
        public final String text;
        public final double score;

        public Hit(String chunkId, String text, double score) {
            this.chunkId = chunkId;
            this.text = text;
            this.score = score;
        }
    }

  public List<Hit> retrieve(String query, int k, int expandPerEntity) {
    float[] q = model.embed(query);

    // Stage 1: vector similarity search
    List<Hit> base = new ArrayList<>();
    for (Vertex c : store.chunks()) {
        // Check embedding
        if (!c.property("embedding").isPresent()) {
          System.out.println("Skipping chunk " + c.id() + ": no embedding");
          continue;
        }
        float[] emb = (float[]) c.property("embedding").value();
        if (emb == null || emb.length == 0) {
          System.out.println("Skipping chunk " + c.id() + ": empty embedding");
          continue;
        }

        // Compute similarity
        double sim = VectorMath.cosine(q, emb);
        float s = (float) sim;

        // Safe access for id and text
        String cid = c.property("id").isPresent() ? c.property("id").value().toString() : c.id().toString();
        String text = c.property("text").isPresent() ? c.property("text").value().toString() : "";

        if (s > 0) {
          base.add(new Hit(cid, text, s));
          System.out.println("Chunk " + cid + " score: " + s);
        }
    }

    if (base.isEmpty()) {
      System.out.println("No hits found. Check embeddings and chunk content!");
      return Collections.emptyList();
    }

    // Sort by score descending and limit top-k
    base.sort((a, b) -> Double.compare(b.score, a.score));
    if (base.size() > k) base = base.subList(0, k);

    // Stage 2: expand via entities
    Map<String, Hit> merged = new LinkedHashMap<>();
    for (Hit h : base) merged.put(h.chunkId, h);

    for (Hit h : base) {
      Vertex chunkV = store.chunks().stream()
                            .filter(v -> {
                                String vid = v.property("id").isPresent() ? v.property("id").value().toString() : v.id().toString();
                                return vid.equals(h.chunkId);
                            })
                            .findFirst().orElse(null);
      if (chunkV == null) continue;

      List<Vertex> ents = store.entitiesMentionedIn(chunkV);
      for (Vertex e : ents) {
        int added = 0;
        for (Vertex other : store.chunksMentioning(e)) {
          if (other.equals(chunkV)) continue;
          String oid = other.property("id").isPresent() ? other.property("id").value().toString() : other.id().toString();
          if (merged.containsKey(oid)) continue;

          String otherText = other.property("text").isPresent() ? other.property("text").value().toString() : "";
          merged.put(oid, new Hit(oid, otherText, 0.15f));

          if (++added >= expandPerEntity) break;
        }
      }
    }

    // Rerank
    List<Hit> reranked = new ArrayList<>();
    for (Hit h : merged.values()) {
      double vecDouble = 0.0;
      Vertex chunkV = store.chunks().stream()
                            .filter(v -> {
                                String vid = v.property("id").isPresent() ? v.property("id").value().toString() : v.id().toString();
                                return vid.equals(h.chunkId);
                            })
                            .findFirst().orElse(null);

      if (chunkV != null && chunkV.property("embedding").isPresent()) {
        float[] emb2 = (float[]) chunkV.property("embedding").value();
        vecDouble = VectorMath.cosine(q, emb2);
      }

      float finalScore = (float) (0.85 * vecDouble + 0.15 * h.score);
      reranked.add(new Hit(h.chunkId, h.text, finalScore));
    }

    // Final sort
    reranked.sort((a, b) -> Double.compare(b.score, a.score));

    return reranked.size() > (k * 2) ? reranked.subList(0, k * 2) : reranked;
  }
}
