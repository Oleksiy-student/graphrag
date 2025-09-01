package com.ok.pipeline;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;

public class PdfExtractor {
  /**
   * Extracts text from a PDF while preserving paragraph breaks and headings.
   * Returns a single String where paragraphs are separated by double newlines.
   */
  public String extract(String fileName) throws IOException {
    String folder = "data";
    File pdfFile = new File(folder, fileName);
    try (PDDocument document = PDDocument.load(pdfFile)) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setLineSeparator("\n");
      String rawText = stripper.getText(document);

      // Normalize line breaks: collapse multiple blank lines into double newline
      String normalized = rawText.replaceAll("(?m)^[ \t]*\r?\n", "\n")
                                  .replaceAll("\n{2,}", "\n\n");

      // Trim spaces on each line
      StringBuilder sb = new StringBuilder();
      String[] lines = normalized.split("\n");
      for (String line : lines) {
        String trimmed = line.trim();
        if (!trimmed.isEmpty()) {
          sb.append(trimmed).append("\n\n"); // double newline between paragraphs
        }
      }
      return sb.toString().trim();
    }
  }
}
