package com.ok.embeddings;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * EmbeddingModel implementation using Qwen-3 embeddings via Ollama local API
 */
public class Qwen3EmbeddingModel implements EmbeddingModel {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static final String OLLAMA_URL;
  private static final String MODEL;

  // Static initialization block for configuration loading
  static {
        Properties props = new Properties();
        String url = "http://localhost:11434/api/generate"; // fallback default
        String model = "qwen3:4b";                          // fallback default
        try (InputStream in = new FileInputStream("config.properties")) {
            props.load(in);
            url = props.getProperty("OLLAMA_URL", url);
            model = props.getProperty("QWEN3_MODEL", model);
        } catch (IOException e) {
            System.err.println("Failed to load config.properties, using defaults: " + e.getMessage());
        }
        OLLAMA_URL = url;
        MODEL = model;
  }

  @Override
  public float[] embed(String text) {
    try {
      // Build JSON request for Ollama embedding API
      ObjectNode requestJson = MAPPER.createObjectNode();
      requestJson.put("model", MODEL);
      requestJson.put("input", text);

      HttpRequest request = HttpRequest.newBuilder()
              .uri(URI.create(OLLAMA_URL + "/api/embed"))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString(), StandardCharsets.UTF_8))
              .build();

      HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      Map<String,Object> respMap = MAPPER.readValue(
        response.body(),
        new TypeReference<Map<String,Object>>() {}
      );

      // Extract embedding vector from nested response structure
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

      // Convert Number list to float array
      float[] vec = new float[firstList.size()];
      for (int i = 0; i < firstList.size(); i++) {
        Number n = (Number) firstList.get(i);
        vec[i] = n.floatValue();
      }
      return vec;

    } catch (IOException e) {
      e.printStackTrace();
      return new float[0];
    } catch (InterruptedException e) {
      e.printStackTrace();
      return new float[0];
    }
  }
}
