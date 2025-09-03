package com.ok;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ok.embeddings.*;
import com.ok.pipeline.*;
import com.ok.store.*;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GraphBuilderTest {

    @Test
    public void testIngestBuildsGraphWithWikidata() throws Exception {
        // Prepare graph store
        GraphStore store = new TinkerGraphStore();

        // Prepare embedding model and fit some text
        TfIdfEmbeddingModel model = new TfIdfEmbeddingModel();
        model.fit(Arrays.asList("OpenAI builds AI", "Neo4j stores graphs"));

        // Entity extractor
        EntityExtractor ner = new EntityExtractor();

        // Mock WikidataMatcher
        WikidataMatcher wikidata = mock(WikidataMatcher.class);

        // Prepare mock matches and relations
        WikidataMatcher.Match matchAI = new WikidataMatcher.Match(
                "Q1", "AI", "Technology", List.of(
                        new WikidataMatcher.Relation("RELATED_TO", "Q2", "Neo4j", "Software")
                )
        );
        WikidataMatcher.Match matchNeo4j = new WikidataMatcher.Match(
                "Q2", "Neo4j", "Software", List.of()
        );

        // Mock behavior
        when(wikidata.match("AI")).thenReturn(matchAI);
        when(wikidata.match("Neo4j")).thenReturn(matchNeo4j);

        // Build graph
        GraphBuilder builder = new GraphBuilder(store, model, wikidata);
        GraphBuilder.IngestResult result = builder.ingest(
                "doc1",
                Arrays.asList("OpenAI builds AI", "Neo4j stores graphs"),
                ner,
                wikidata
        );

        TinkerGraphStore tstore = (TinkerGraphStore) store;

        // Check chunk vertices
        assertTrue(tstore.getGraph().traversal().V().hasLabel("chunk").hasNext(),
                "Should contain chunk vertices");

        // Check entity vertices
        assertTrue(tstore.getGraph().traversal().V().hasLabel("entity").hasNext(),
                "Should contain entity vertices");

        // Check 'chunks' property on each entity
        ObjectMapper mapper = new ObjectMapper();
        for (Vertex e : tstore.getGraph().traversal().V().hasLabel("entity").toList()) {
            assertTrue(e.property("chunks").isPresent(), "Entity should have 'chunks' property");
            String json = (String) e.property("chunks").value();
            List<String> chunkIds = mapper.readValue(json, List.class);
            assertFalse(chunkIds.isEmpty(), "Entity should reference at least one chunk ID");
        }

        // Check that the AI -> Neo4j relation edge exists
        boolean edgeExists = tstore.getGraph().traversal().E()
                .hasLabel("RELATED_TO")
                .toList()
                .stream()
                .anyMatch(edge ->
                        edge.outVertex().property("name").value().equals("AI") &&
                        edge.inVertex().property("name").value().equals("Neo4j")
                );
        assertTrue(edgeExists, "Graph should contain Wikidata relation edge AI -> Neo4j");
    }
}
