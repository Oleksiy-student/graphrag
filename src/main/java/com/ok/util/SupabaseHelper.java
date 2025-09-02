package com.ok.util;

import com.ok.embeddings.*;
import com.ok.pipeline.*;
import com.ok.store.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Logger;

public class SupabaseHelper {
  private static final Logger LOGGER = Logger.getLogger(SupabaseHelper.class.getName());

  public static class Config {
    public final String url;
    public final String apiKey;
    public final String table;

    public Config(String url, String apiKey, String table) {
      this.url = url;
      this.apiKey = apiKey;
      this.table = table;
    }
  }

  // Load config.properties and return supabase settings
  public static Config loadConfig() throws IOException {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("config.properties")) {
      props.load(in);
    }
    return new Config(
      props.getProperty("SUPABASE_URL"),
      props.getProperty("SUPABASE_API_KEY"),
      props.getProperty("SUPABASE_TABLE")
    );
  }

  // Retrieve hits from Supabase for a given query
  public static List<SupabaseRetriever.Hit> retrieveHits(Config cfg, EmbeddingModel model, String query, int topK)
      throws Exception {
    SupabaseRetriever retriever = new SupabaseRetriever(cfg.url, cfg.apiKey, cfg.table, model);
    List<SupabaseRetriever.Hit> hits = retriever.retrieve(query, topK);

    LOGGER.fine("\n--- Retrieved hits (preview first 100 chars) ---");
    for (SupabaseRetriever.Hit h : hits) {
      String preview = h.chunkText.length() > 100 ? h.chunkText.substring(0, 100) + "..." : h.chunkText;
      LOGGER.fine(String.format("Score: %.4f\nPreview: %s\nMetadata: %s\n",
          h.score, preview, h.metadata));
    }
    return hits;
  }

  // Convert SupabaseRetriever.Hit → Retriever.Hit for AnswerComposer
  public static List<Retriever.Hit> toRetrieverHits(List<SupabaseRetriever.Hit> supabaseHits) {
    List<Retriever.Hit> dummyHits = new ArrayList<>();
    for (SupabaseRetriever.Hit h : supabaseHits) {
      dummyHits.add(new Retriever.Hit("chunk " + h.chunkIndex, h.chunkText, h.score));
    }
    return dummyHits;
  }
}
