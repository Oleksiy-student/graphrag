package com.ok.embeddings;

public final class VectorMath {
  private VectorMath() {}
  
  public static float dot(float[] a, float[] b) {
    int n = Math.min(a.length, b.length);
    float s = 0f; for (int i=0;i<n;i++) s += a[i]*b[i];
    return s;
  }
  public static float norm(float[] a) {
    float s=0f; for (float v: a) s += v*v; return (float)Math.sqrt(s);
  }
  public static float cosine(float[] a, float[] b) {
    if (a.length != b.length) throw new IllegalArgumentException("Vector sizes differ");
    double dot = 0, normA = 0, normB = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    if (normA == 0 || normB == 0) return 0f;
    return (float)(dot / (Math.sqrt(normA) * Math.sqrt(normB)));
  }
}