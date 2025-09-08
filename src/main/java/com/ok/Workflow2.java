package com.ok;

import com.ok.embeddings.*;
import com.ok.pipeline.*;
import com.ok.store.*;
import com.ok.util.*;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.logging.Logger;

/**
 * Alternative workflow strategy for processing with cached data.
 * Demonstrates Strategy pattern variation with different processing approach.
 */
public class Workflow2 {
  private static final Logger LOGGER = Logger.getLogger(Workflow2.class.getName());

  public static void run(String query) {
    try {
      // Dependency injection: External configuration
      SupabaseHelper.Config cfg = SupabaseHelper.loadConfig();
      EmbeddingModel model = new Qwen3EmbeddingModel();

      // Template method: Load existing data instead of processing
      TinkerGraphStore graphStore = new TinkerGraphStore();
      try {
        graphStore.loadGraph("graph_document.graphml");
        LOGGER.info("Loaded existing graph.");
      } catch (IOException e) {
        LOGGER.severe("Failed to load graph: " + e.getMessage());
        return;
      }

      // Same retrieval strategy as Workflow1: Retrieve semantically similar chunks for the query
      List<SupabaseRetriever.Hit> hits = SupabaseHelper.retrieveHits(cfg, model, query, 5);
      if (hits.isEmpty()) {
        LOGGER.warning("No relevant hits retrieved from Supabase.");
      }

      // Factory pattern: Create answer composer
      String ollamaUrl = Config.get("OLLAMA_URL");
      String ollamaModel = Config.get("OLLAMA_MODEL");

      AnswerComposer composer = new AnswerComposer(
          HttpClient.newHttpClient(),
          ollamaUrl,
          ollamaModel
      );

      String answer = composer.compose(query, SupabaseHelper.toRetrieverHits(hits), 1200);
      LOGGER.info("Answer:\n" + answer);

    } catch (Exception e) {
      LOGGER.severe("Workflow2 failed: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
