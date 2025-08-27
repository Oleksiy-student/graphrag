package com.ok.embeddings;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


/**
* Tiny TF‑IDF model built over a fixed corpus you load during ingestion.
* Use as a local, dependency‑free vectorizer; swap with real embeddings later.
*/
public class TfIdfEmbeddingModel implements EmbeddingModel {
  private final Map<String,Integer> vocabIndex = new HashMap<>();
  private final Map<String,Integer> df = new HashMap<>();
  private int docCount = 0;
  private static final Pattern TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9_]+");


  public void fit(List<String> documents) {
    docCount = documents.size();
    Set<String> seen;
    for (String doc : documents) {
      seen = new HashSet<>();
      Matcher m = TOKEN.matcher(doc.toLowerCase());
      while (m.find()) {
        String t = m.group();
        if (!vocabIndex.containsKey(t)) {
          vocabIndex.put(t, vocabIndex.size());
        }
        if (seen.add(t)) df.merge(t, 1, Integer::sum);
      }
    }
  }


  @Override
  public float[] embed(String text) {
    float[] vec = new float[vocabIndex.size()];
    Map<String,Integer> tf = new HashMap<>();
    Matcher m = TOKEN.matcher(text.toLowerCase());
    int max = 0;
    while (m.find()) {
      String tok = m.group();
      tf.merge(tok, 1, Integer::sum);
      max = Math.max(max, tf.get(tok));
    }
    for (Map.Entry<String,Integer> e : tf.entrySet()) {
      Integer idx = vocabIndex.get(e.getKey());
      if (idx == null) continue;
      int f = e.getValue();
      int dfi = df.getOrDefault(e.getKey(), 1);
      double idf = Math.log((1.0 + docCount) / (1.0 + dfi)) + 1.0;
      double wt = (0.5 + 0.5 * (f / (double) Math.max(1, max))) * idf; // normalized tf * idf
      vec[idx] = (float) wt;
    }
    return normalize(vec);
  }

  private float[] normalize(float[] vec) {
    double sum = 0;
    for (float v : vec) sum += v*v;
    double norm = Math.sqrt(sum);
    if (norm == 0) return vec;
    float[] out = new float[vec.length];
    for (int i = 0; i < vec.length; i++) out[i] = (float)(vec[i]/norm);
    return out;
  }
}
