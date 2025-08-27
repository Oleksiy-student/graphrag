package com.ok.pipeline;

import java.util.*;

public class AnswerComposer {
  public String compose(String query, List<Retriever.Hit> hits, int maxChars) {
    StringBuilder sb = new StringBuilder();
    sb.append("Question: ").append(query).append("\n\n");
    sb.append("Evidence (most relevant first):\n");

    for (int i = 0; i < hits.size(); i++) {
      Retriever.Hit h = hits.get(i);
      sb.append(String.format("%d) %s (score=%.3f)\n", i + 1, h.text, h.score));
    }

    sb.append("Draft Answer:\n");
    sb.append("Based on the retrieved evidence, GraphRAG can now generate a concise synthesis.\n");
    return sb.toString();
    }
}