package com.ok;

import org.junit.jupiter.api.Test;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.util.Arrays;
import java.util.List;

import com.ok.store.*;
import com.ok.embeddings.*;

public class RetrieverDebugTest {

  @Test
  void testChunkEmbeddingsAndCosine() {
    // --- Setup your store and model ---
    TinkerGraphStore store = new TinkerGraphStore();

    // Load saved graph and embeddings (optional)
    try {
      store.loadGraph("graph.xml");
      store.loadEmbeddings("embeddings.json");
    } catch (Exception e) {
      e.printStackTrace();
    }

    TfIdfEmbeddingModel model = new TfIdfEmbeddingModel();

    // Fit model on all chunks (ensure same TF-IDF vocabulary)
    List<Vertex> chunks = store.chunks();
    List<String> texts = chunks.stream()
                .map(v -> v.property("text").value().toString())
                .toList();
    model.fit(texts);

    // --- Debug query ---
    String query = "number of dogs in an occupancy unit";
    float[] qEmb = model.embed(query);

    System.out.println("Query embedding length: " + qEmb.length);
    System.out.println("Query embedding sample: " +
          Arrays.toString(Arrays.copyOf(qEmb, Math.min(10, qEmb.length))));

    // --- Check each chunk ---
    for (Vertex v : chunks) {
      String chunkId = v.id().toString();
      String text = v.property("text").value().toString();

      if (!v.property("embedding").isPresent()) {
        System.out.println(chunkId + " - no embedding!");
        continue;
      }

      float[] emb = (float[]) v.property("embedding").value();
      System.out.println(chunkId + " embedding length: " + emb.length);
      System.out.println("Chunk embedding sample: " +
              Arrays.toString(Arrays.copyOf(emb, Math.min(10, emb.length))));

      // Compute cosine similarity
      float score = VectorMath.cosine(qEmb, emb);
      System.out.println(chunkId + " -> cosine with query: " + score);

      if (score > 0) {
        System.out.println("Text snippet: " + (text.length() > 100 ? text.substring(0, 100) + "..." : text));
      }
      System.out.println("---------------------------------------------------");
    }
  }
}
