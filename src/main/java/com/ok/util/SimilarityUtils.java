package com.ok.util;

public class SimilarityUtils {
  /** Compute cosine similarity between two float vectors */
    public static double cosine(float[] v1, float[] v2) {
      if (v1.length != v2.length) throw new IllegalArgumentException("Vector length mismatch");
      double dot = 0.0, norm1 = 0.0, norm2 = 0.0;
      for (int i = 0; i < v1.length; i++) {
          dot += v1[i] * v2[i];
          norm1 += v1[i] * v1[i];
          norm2 += v2[i] * v2[i];
      }
      if (norm1 == 0 || norm2 == 0) return 0.0;
      return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

}
