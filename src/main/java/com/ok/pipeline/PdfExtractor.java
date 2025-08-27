package com.ok.pipeline;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;

public class PdfExtractor {
   public String extract(File pdfFile) throws IOException {
      try (PDDocument document = PDDocument.load(pdfFile)) {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setLineSeparator("\n\n"); // preserve paragraph breaks
        String text = stripper.getText(document);
        return text.trim();
      }
    }
}
