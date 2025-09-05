package com.ok;

import com.ok.embeddings.EmbeddingModel;
import com.ok.pipeline.EntityExtractor;
import com.ok.pipeline.GraphBuilder;
import com.ok.store.GraphStore;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphBuilderTest {

    @Test
    void testIngestWithEntitiesAndRelations() {
        GraphStore store = mock(GraphStore.class);
        EmbeddingModel model = mock(EmbeddingModel.class);
        EntityExtractor ner = mock(EntityExtractor.class);

        // Mock embeddings
        when(model.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        // Mock entity vertices
        Vertex aliceV = mock(Vertex.class);
        Vertex acmeV = mock(Vertex.class);
        when(store.addEntity(eq("Alice"), eq("Person"))).thenReturn(aliceV);
        when(store.addEntity(eq("Acme"), eq("Organization"))).thenReturn(acmeV);

        // Mock extraction: Alice works_for Acme
        EntityExtractor.Entity alice = new EntityExtractor.Entity("Alice", "Person");
        alice.addChunkId(0);
        EntityExtractor.Entity acme = new EntityExtractor.Entity("Acme", "Organization");
        acme.addChunkId(0);

        List<EntityExtractor.Relation> relations = List.of(
            new EntityExtractor.Relation("Alice", "Acme", "works_for")
        );

        EntityExtractor.ExtractionResult extractionResult =
            new EntityExtractor.ExtractionResult(List.of(alice, acme), relations);

        when(ner.extractBatch(eq("doc"), anyList())).thenReturn(extractionResult);

        // Build graph
        GraphBuilder gb = new GraphBuilder(store, model, null);
        GraphBuilder.IngestResult result = gb.ingest("doc", List.of("Alice works at Acme"), ner);

        // Verify entity vertices created
        verify(store).addEntity("Alice", "Person");
        verify(store).addEntity("Acme", "Organization");
        assertEquals(2, result.getEntityVertices().size());

        // Verify relation edge
        verify(store).addEdge(aliceV, acmeV, "works_for", Map.of("extracted", true));
    }
}
