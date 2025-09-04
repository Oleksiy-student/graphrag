package com.ok.pipeline;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ok.util.Config;

public class AnswerComposer {

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final HttpClient httpClient;
  private final String ollamaUrl;
  private final String ollamaModel;
  private static final int MAX_HITS = 10; // limit evidence length

  public AnswerComposer() {
    this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
        Config.get("OLLAMA_URL"),
        Config.get("OLLAMA_MODEL"));
  }

  // Default constructor uses real client + loads from config
  public AnswerComposer(HttpClient httpClient, String ollamaUrl, String ollamaModel) {
    this.httpClient = httpClient;
    this.ollamaUrl = ollamaUrl;
    this.ollamaModel = ollamaModel;
  }

  public String compose(String query, List<Retriever.Hit> hits, int maxChars) {
    try {
      StringBuilder evidence = new StringBuilder(hits.size() * 150); // pre-allocate
      int count = 0;
      for (Retriever.Hit hit : hits) {
        if (count++ >= MAX_HITS) break;
        String preview = hit.text.length() > 100 ? hit.text.substring(0, 100) + "..." : hit.text;
        evidence.append("- [").append(hit.chunkId).append("] ").append(preview)
            .append(String.format(" (score=%.4f)", hit.score))
            .append("\n");
      }

      String prompt = String.format(
          "Question: %s\n\nEvidence:\n%s\n\nDraft Answer:",
          query, evidence.toString()
      );

      // Include "stream": false to get full output in one response
      String payload = String.format(
          "{\"model\":\"%s\",\"prompt\":%s,\"stream\":false}",
          ollamaModel, MAPPER.writeValueAsString(prompt)
      );

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(ollamaUrl + "/api/generate"))
          .timeout(Duration.ofSeconds(30))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      // Handle multiple JSON objects if streamed
      StringBuilder finalOutput = new StringBuilder();
      String body = response.body().trim();

      // Check if body is a single JSON object or multiple JSONs (stream)
      if (body.startsWith("{") && body.endsWith("}")) {
        Map<String, Object> json = MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {});
        finalOutput.append(json.getOrDefault("response", "").toString());
      } else {
        // Split multiple JSON objects separated by newlines
        String[] lines = body.split("\\r?\\n");
        for (String line : lines) {
          if (line.isBlank()) continue;
          Map<String, Object> json = MAPPER.readValue(line, new TypeReference<Map<String, Object>>() {});
          Object resp = json.get("response");
          if (resp != null) finalOutput.append(resp.toString());
          if (Boolean.TRUE.equals(json.get("done"))) break;
        }
      }

      return "Draft Answer:\n" + finalOutput.toString();

    } catch (Exception e) {
      // Fallback: generate local answer
      StringBuilder fallback = new StringBuilder();
      fallback.append("Draft Answer (fallback):\n");
      fallback.append("Based on retrieved evidence, GraphRAG can synthesize an answer.\n\n");
      for (Retriever.Hit hit : hits) {
        String preview = hit.text.length() > 100 ? hit.text.substring(0, 100) + "..." : hit.text;
        fallback.append("- [").append(hit.chunkId).append("] ").append(preview)
            .append(String.format(" (score=%.4f)", hit.score))
            .append("\n");
      }
      return fallback.toString();
    }
  }
}
