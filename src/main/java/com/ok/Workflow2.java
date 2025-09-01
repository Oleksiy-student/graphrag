package com.ok;

import com.ok.embeddings.*;
import com.ok.pipeline.*;
import com.ok.store.*;
import com.ok.util.SupabaseHelper;

import java.util.List;
import java.util.logging.Logger;

public class Workflow2 {
  private static final Logger LOGGER = Logger.getLogger(Workflow2.class.getName());

  public static void run(String query) {
    try {
      SupabaseHelper.Config cfg = SupabaseHelper.loadConfig();
      EmbeddingModel model = new Qwen3EmbeddingModel();

      // Retrieve hits
      List<SupabaseRetriever.Hit> hits = SupabaseHelper.retrieveHits(cfg, model, query, 5);

      // Compose answer
      AnswerComposer composer = new AnswerComposer();
      String answer = composer.compose(query, SupabaseHelper.toRetrieverHits(hits), 1200);

      LOGGER.info(answer);

    } catch (Exception e) {
      LOGGER.severe("Workflow2 failed: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
