package com.ok.embeddings;

/**
 * Strategy interface for different embedding implementations.
 * Allows pluggable text vectorization approaches.
 */
public interface EmbeddingModel {
  // Convert text to vector representation
  float[] embed(String text);
}