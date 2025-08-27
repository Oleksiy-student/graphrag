package com.ok;

import com.ok.pipeline.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.*;

public class AnswerComposerTest {
  /**
   * 
   */
  @Test
  public void testComposeGeneratesAnswer() {
    AnswerComposer composer = new AnswerComposer();

    // Sample list of hits to test answer composition
    List<Retriever.Hit> hits = Arrays.asList(
      new Retriever.Hit("chunk1", "GraphRAG augments retrieval with graphs", 0.9),
      new Retriever.Hit("chunk2", "Neo4j is a graph database", 0.8)
    );

    // Test answer composition with a sample query and retrieval hits
    String query = "What is GraphRAG?";
    String answer = composer.compose(query, hits, 500);

    assertNotNull(answer);
    assertTrue(answer.contains("Answer:"));
    assertTrue(answer.contains("GraphRAG"));
  }
}
