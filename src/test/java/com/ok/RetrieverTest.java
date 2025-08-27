// package com.ok;

// import static org.junit.jupiter.api.Assertions.*;
// import org.junit.jupiter.api.Test;
// import com.ok.embeddings.*;
// import com.ok.pipeline.*;
// import com.ok.store.*;
// import java.util.*;

// public class RetrieverTest {
//   @Test
//   public void testRetrieveReturnsRelevantHits() {
//     GraphStore store = new TinkerGraphStore();
//     TfIdfEmbeddingModel model = new TfIdfEmbeddingModel();
//     List<String> chunks = Arrays.asList(
//     "GraphRAG combines retrieval and graphs",
//     "Neo4j is a graph database",
//     "OpenAI builds models"
//     );
//     model.fit(chunks);


//     GraphBuilder builder = new GraphBuilder(store, model);
//     builder.ingest("doc1", chunks, new EntityExtractor());


//     Retriever retriever = new Retriever(store, model);
//     List<Retriever.Hit> hits = retriever.retrieve("What is Neo4j?", 2, 1);


//     assertFalse(hits.isEmpty(), "Should return hits for query");
//     assertTrue(hits.get(0).text.toLowerCase().contains("neo4j"));
//   }
// }
