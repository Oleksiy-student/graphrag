package com.ok.store;

import com.ok.embeddings.EmbeddingModel;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

// Database abstraction layer for Supabase vector operations
public class SupabaseRetriever {

  private static final Logger LOGGER = Logger.getLogger(SupabaseRetriever.class.getName());
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String url;
  private final String apiKey;
  private final String table;
  private final EmbeddingModel model;
  private final HttpClient client;

  // Value object for search results with immutable properties
  public static class Hit {
    public final String id;
    public final String chunkIndex;
    public final String chunkText;
    public final Map<String, Object> metadata;
    public final double score;

    public Hit(String id, String chunkIndex, String chunkText, Map<String, Object> metadata, double score) {
      this.id = id;
      this.chunkIndex = chunkIndex;
      this.chunkText = chunkText;
      this.metadata = metadata;
      this.score = score;
    }
  }

  public SupabaseRetriever(String url, String apiKey, String table, EmbeddingModel model) {
    this.url = url;
    this.apiKey = apiKey;
    this.table = table;
    this.model = model;
    this.client = HttpClient.newHttpClient();
  }

  // Vector similarity search with HTTP communication
  public List<Hit> retrieve(String query, int k) {
    try {
      // Convert query to vector representation
      float[] queryEmb = model.embed(query);
      List<Double> queryVector = new ArrayList<>(queryEmb.length);
      for (float f : queryEmb) queryVector.add((double) f);

      // Payload includes table name, query vector, and top_k
      Map<String, Object> payload = new HashMap<>();
      payload.put("table_name", table);
      payload.put("query_vector", queryVector);
      payload.put("top_k", k);

      String jsonBody = MAPPER.writeValueAsString(payload);

      // Call the generic vector_search remote procedure call
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url + "/rest/v1/rpc/vector_search"))
          .header("apikey", apiKey)
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
          .build();

      // Process response and transform data
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        List<Map<String, Object>> results = MAPPER.readValue(response.body(), new TypeReference<>() {});
        List<Hit> hits = new ArrayList<>();
        for (Map<String, Object> row : results) {
          String id = row.getOrDefault("id", "").toString();
          String chunkIndex = row.getOrDefault("chunk_index", "").toString();
          String chunkText = row.getOrDefault("chunk_text", "").toString();

          Object metaObj = row.get("metadata");
          Map<String, Object> metadata;
          if (metaObj instanceof Map<?, ?> map) {
            metadata = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
              if (entry.getKey() instanceof String key) metadata.put(key, entry.getValue());
            }
          } else {
            metadata = Map.of();
          }

          double score = row.containsKey("score") ? ((Number) row.get("score")).doubleValue() : 0.0;
          hits.add(new Hit(id, chunkIndex, chunkText, metadata, score));
        }
        return hits;
      } else {
        LOGGER.warning("Supabase retrieval failed: " + response.body());
      }
    } catch (Exception e) {
      LOGGER.severe("Error during Supabase retrieval: " + e.getMessage());
      e.printStackTrace();
    }
    return Collections.emptyList();
  }
}
