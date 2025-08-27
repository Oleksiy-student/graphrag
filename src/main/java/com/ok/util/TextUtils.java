package com.ok.util;

import java.util.*;

public final class TextUtils {
  private TextUtils() {}

  public static List<String> chunkByTokens(String text, int targetTokens) {
    // Remove excessive newlines and normalize spaces
    text = text.replaceAll("\\r\\n", "\n")        // unify CRLF
               .replaceAll("\\n{2,}", "\n")      // multiple newlines → single
               .replaceAll("[ \t]+", " ");       // multiple spaces → single

    // Split into sentence-ish units
    String[] sentences = text.split("(?<=[.!?])\\s+");

    List<String> chunks = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    int tokens = 0;

    for (String s : sentences) {
      s = s.trim();
      if (s.isEmpty()) continue;

      int t = s.split("\\s+").length;
      if (tokens + t > targetTokens && cur.length() > 0) {
          chunks.add(cur.toString().trim());
          cur.setLength(0);
          tokens = 0;
      }
      cur.append(s).append(" ");
      tokens += t;
    }
    if (cur.length() > 0) chunks.add(cur.toString().trim());

    return chunks;
  }
}
