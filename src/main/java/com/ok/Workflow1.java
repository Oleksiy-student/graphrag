package com.ok;

import com.ok.embeddings.*;
import com.ok.pipeline.*;
import com.ok.store.*;
import com.ok.util.SupabaseHelper;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Template Method Pattern implementation for full document processing workflow.
 * Defines the skeleton of the algorithm: extract -> chunk -> embed -> store -> retrieve -> compose.
 */
public class Workflow1 {
  private static final Logger LOGGER = Logger.getLogger(Workflow1.class.getName());
  private static final ObjectMapper MAPPER = new ObjectMapper();

  // Load configuration properties
  public static void run(String pdfFile, String query) {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("config.properties")) {
      props.load(in);
    } catch (IOException e) {
      LOGGER.severe("Failed to load config.properties: " + e.getMessage());
      return;
    }

    String SUPABASE_URL = props.getProperty("SUPABASE_URL");
    String SUPABASE_API_KEY = props.getProperty("SUPABASE_API_KEY");
    String SUPABASE_TABLE = props.getProperty("SUPABASE_TABLE");

    try {
      SupabaseHelper.Config cfg = SupabaseHelper.loadConfig();

      // Extract text from PDF document
      PdfExtractor pdfExtractor = new PdfExtractor();
      String docText = pdfExtractor.extract(pdfFile);

      // Split document into manageable chunks
      DocumentChunker chunker = new DocumentChunker(250);
      List<String> chunks = chunker.chunk(docText);
      LOGGER.fine(() -> "Total chunks: " + chunks.size());

      // Initialize embedding model for vector representations
      EmbeddingModel model = new Qwen3EmbeddingModel();

      // Store chunks and embeddings in Supabase vector database
      HttpClient client = HttpClient.newHttpClient();
      for (int i = 0; i < chunks.size(); i++) {
      final int idx = i;
      String text = chunks.get(idx);
      float[] embedding = model.embed(text);

      // Build metadata for each chunk
      Map<String, Object> metadata = new HashMap<>();
      metadata.put("doc", pdfFile);
      metadata.put("length", text.length());
      metadata.put("author", "Unknown");
      metadata.put("page_number", -1);
      metadata.put("created_at", new Date().toString());

      // Create database row with embedding vector
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("chunk_index", idx);
      row.put("chunk_text", text);
      row.put("metadata", metadata);
      row.put("embedding", embeddingToList(embedding));

      String jsonBody = MAPPER.writeValueAsString(row);

      // Insert into Supabase via REST API
      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(SUPABASE_URL + "/rest/v1/" + SUPABASE_TABLE))
        .header("apikey", SUPABASE_API_KEY)
        .header("Authorization", "Bearer " + SUPABASE_API_KEY)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("[" + jsonBody + "]"))
        .build();

      client.send(request, HttpResponse.BodyHandlers.ofString());
      }

      // Retrieve semantically similar chunks for the query
      List<SupabaseRetriever.Hit> hits = SupabaseHelper.retrieveHits(cfg, model, query, 8);

      // Build knowledge graph with entity extraction and Wikidata matching
      GraphStore graphStore = new TinkerGraphStore();
      WikidataMatcher wikidata = new WikidataMatcher();
      GraphBuilder builder = new GraphBuilder(graphStore, model, wikidata);

      builder.ingest(pdfFile, chunks, new EntityExtractor());

      // Persist graph to disk for future use
      try {
      ((TinkerGraphStore) graphStore).saveGraph("graph_document.graphml");
      LOGGER.fine("Graph saved successfully.");
      graphStore.exportCytoscapeJson("graph.json");
      LOGGER.fine("Graph exported to graph.json (Cytoscape.js format)");
      } catch (IOException e) {
      LOGGER.severe("Failed to save graph: " + e.getMessage());
      }

      // Generate final answer using retrieved context
      AnswerComposer composer = new AnswerComposer(
        HttpClient.newHttpClient(),
        props.getProperty("OLLAMA_URL"),
        props.getProperty("DEEPSEEK_MODEL")
      );

      String answer = composer.compose(query, SupabaseHelper.toRetrieverHits(hits), 1200);
      LOGGER.info(answer);

    } catch (Exception e) {
      LOGGER.severe("Error during workflow execution: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
     * Convert float array to List<Double> for JSON serialization.
     */
  private static List<Double> embeddingToList(float[] emb) {
  List<Double> list = new ArrayList<>(emb.length);
  for (float f : emb) list.add((double) f);
  return list;
  }
}
