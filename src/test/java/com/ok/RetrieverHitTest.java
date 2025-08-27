// package com.ok;

// import org.junit.jupiter.api.Test;
// import org.apache.tinkerpop.gremlin.structure.Vertex;
// import java.util.List;
// import com.ok.embeddings.*;
// import com.ok.pipeline.*;
// import com.ok.store.*;
// import static org.junit.jupiter.api.Assertions.assertFalse;

// public class RetrieverHitTest {

//   @Test
//   void testRetrieverReturnsHits() throws Exception {
//     // --- Setup store ---
//     TinkerGraphStore store = new TinkerGraphStore();
//     store.loadGraph("graph.xml");
//     store.loadEmbeddings("embeddings.json");

//     // --- Setup model ---
//     TfIdfEmbeddingModel model = new TfIdfEmbeddingModel();

//     // Fit model on all chunk texts
//     List<Vertex> chunks = store.chunks();
//     List<String> texts = chunks.stream()
//             .map(v -> v.property("text").value().toString())
//             .toList();
//     model.fit(texts);

//     // --- Setup retriever ---
//     Retriever retriever = new Retriever(store, model);

//     String query = "number of dogs in an occupancy unit";

//     // Retrieve top 5 hits, expand 2 per entity
//     List<Retriever.Hit> hits = retriever.retrieve(query, 5, 2);

//     // --- Debug output ---
//     System.out.println("Hits returned: " + hits.size());
//     for (Retriever.Hit h : hits) {
//       System.out.println(h.chunkId + " -> score=" + h.score);
//       System.out.println(h.text.substring(0, Math.min(100, h.text.length())) + "...");
//     }

//     // --- Assertion: at least one hit must be returned ---
//     assertFalse(hits.isEmpty(), "Retriever returned no hits — check embeddings or TF-IDF model fit!");
//   }
// }
