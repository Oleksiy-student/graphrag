package com.ok.pipeline;

import com.ok.util.TextUtils;
import java.util.*;


public class DocumentChunker {
  private final int targetTokens;

  /**
   * @param targetTokens approximate number of tokens per chunk
   */
  public DocumentChunker(int targetTokens) {
    this.targetTokens = targetTokens;
  }

  /**
   * Chunk the text into smaller pieces (~targetTokens each) using TextUtils.
   */
  public List<String> chunk(String text) {
    return TextUtils.chunkByTokens(text, targetTokens);
  }
}
