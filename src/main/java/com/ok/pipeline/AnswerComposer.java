package com.ok.pipeline;

import java.util.*;

public class AnswerComposer {
  public String compose(String query, List<Retriever.Hit> hits, int maxChars) {
    StringBuilder sb = new StringBuilder();
    sb.append("Question: ").append(query).append("\n\n");
    sb.append("Evidence (most relevant first):\n");

    for (Retriever.Hit hit : hits) {
      String preview = hit.text.length() > 100 ? hit.text.substring(0, 100) + "..." : hit.text;
      sb.append("- [").append(hit.chunkId).append("] ").append(preview)
        .append(String.format(" (score=%.4f)", hit.score))
        .append("\n");
    }

    sb.append("Draft Answer:\n");
    sb.append("Based on the retrieved evidence, GraphRAG can now generate a concise synthesis.\n");
    return sb.toString();
    }
}