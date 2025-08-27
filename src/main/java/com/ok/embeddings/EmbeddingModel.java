package com.ok.embeddings;

public interface EmbeddingModel {
  float[] embed(String text);
}