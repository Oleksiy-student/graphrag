package com.ok.embeddings;

import java.io.IOException;
import java.net.http.*;
import java.net.URI;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * EmbeddingModel implementation using Qwen-3 embeddings via Ollama local API.
 */
public class Qwen3EmbeddingModel implements EmbeddingModel {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  @Override
  public float[] embed(String text) {
    try {
      // Build JSON request
      ObjectNode requestJson = MAPPER.createObjectNode();
      requestJson.put("model", "qwen3:4b");
      requestJson.put("input", text);

      HttpRequest request = HttpRequest.newBuilder()
              .uri(URI.create("http://localhost:11434/api/embed"))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
              .build();

      HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      Map<String,Object> respMap = MAPPER.readValue(
        response.body(),
        new TypeReference<Map<String,Object>>() {}
      );

      // Extract embeddings safely
      Object embeddingsObj = respMap.get("embeddings");
      if (!(embeddingsObj instanceof List<?> embsList) || embsList.isEmpty()) {
        System.out.println("Embedding not found in response: " + response.body());
        return new float[0];
      }

      Object firstEmbObj = ((List<?>) embsList.get(0));
      if (!(firstEmbObj instanceof List<?> firstList)) {
        System.out.println("Invalid embedding format: " + response.body());
        return new float[0];
      }

      float[] vec = new float[firstList.size()];
      for (int i = 0; i < firstList.size(); i++) {
        Number n = (Number) firstList.get(i);
        vec[i] = n.floatValue();
      }
      return vec;

    } catch (Exception e) {
      e.printStackTrace();
      return new float[0];
    }
  }
}
