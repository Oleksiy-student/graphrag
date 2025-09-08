package com.ok.store;

import java.net.http.*;
import java.net.URI;
import java.util.*;
import com.fasterxml.jackson.databind.*;

/**
 * HTTP client for Supabase REST API operations.
 * Encapsulates REST API communication with proper error handling.
 */
public class SupabaseClient {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  private final String url;
  private final String apiKey;
  private final String table;

  public SupabaseClient(String url, String apiKey, String table) {
    this.url = url;
    this.apiKey = apiKey;
    this.table = table;
  }

  public static class SupabaseVectorRecord {
    private final String text;
    private final String metadata;
    private final double score;

    public SupabaseVectorRecord(String text, String metadata, double score) {
      this.text = text;
      this.metadata = metadata;
      this.score = score;
    }

    public String getText() { return text; }
    public String getMetadata() { return metadata; }
    public double getScore() { return score; }
  }

  /**
     * Perform vector similarity search via Supabase RPC function.
     */
  public List<SupabaseVectorRecord> similaritySearch(float[] embedding, int k) throws Exception {
    String body = MAPPER.writeValueAsString(Map.of(
      "query_embedding", embedding,
      "top_k", k
    ));

    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(url + "/rest/v1/rpc/vector_search"))
      .header("apikey", apiKey)
      .header("Authorization", "Bearer " + apiKey)
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build();

    HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    JsonNode arr = MAPPER.readTree(response.body());

    List<SupabaseVectorRecord> records = new ArrayList<>();
    for (JsonNode node : arr) {
      String text = node.has("text") ? node.get("text").asText() : "";
      String metadata = node.has("metadata") ? node.get("metadata").toString() : "{}";
      double score = node.has("score") ? node.get("score").asDouble() : 0.0;
      records.add(new SupabaseVectorRecord(text, metadata, score));
    }
    return records;
  }

  /**
   * Insert a single chunk into Supabase vector table
   * @param text Chunk text
   * @param metadata JSON string containing metadata
   * @param embedding float[] vector from Qwen3
   */
  public void insertRow(String text, String metadata, float[] embedding) throws Exception {
    // Convert float[] to List<Double> for JSON
    List<Double> embList = new ArrayList<>();
    for (float f : embedding) embList.add((double) f);

    Map<String, Object> payload = new HashMap<>();
    payload.put("text", text);
    payload.put("metadata", MAPPER.readTree(metadata)); // store JSON as JSONB
    payload.put("embedding", embList);

    String body = MAPPER.writeValueAsString(payload);

    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(url + "/rest/v1/" + table))
      .header("apikey", apiKey)
      .header("Authorization", "Bearer " + apiKey)
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build();

    HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 400) {
      System.err.println("Failed to insert chunk: " + response.body());
    }
  }
}