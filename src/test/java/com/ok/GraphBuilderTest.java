package com.ok;

import com.ok.embeddings.*;
import com.ok.pipeline.*;
import com.ok.store.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;


public class GraphBuilderTest {

  @Test
  public void testIngestBuildsGraph() {
    GraphStore store = new TinkerGraphStore();
    TfIdfEmbeddingModel model = new TfIdfEmbeddingModel();
    model.fit(Arrays.asList("OpenAI builds AI", "Neo4j stores graphs"));
    EntityExtractor ner = new EntityExtractor();


    GraphBuilder builder = new GraphBuilder(store, model);
    builder.ingest("doc1", Arrays.asList("OpenAI builds AI", "Neo4j stores graphs"), ner);

    TinkerGraphStore tstore = (TinkerGraphStore) store;
    assertTrue(tstore.getGraph().traversal().V().hasLabel("chunk").hasNext(), "Should contain chunk vertices");
    assertTrue(tstore.getGraph().traversal().V().hasLabel("entity").hasNext(), "Should contain entity vertices");
  }
}