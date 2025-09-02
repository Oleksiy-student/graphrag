package com.ok;

import com.ok.embeddings.*;
import com.ok.pipeline.*;
import com.ok.store.*;
import com.ok.util.SupabaseHelper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Workflow1 {
    private static final Logger LOGGER = Logger.getLogger(Workflow1.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void run(String pdfFile, String query) {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream("config.properties")) {
            props.load(in);
        } catch (IOException e) {
            LOGGER.severe("Failed to load config.properties: " + e.getMessage());
            return;
        }

        String SUPABASE_URL = props.getProperty("SUPABASE_URL");
        String SUPABASE_API_KEY = props.getProperty("SUPABASE_API_KEY");
        String SUPABASE_TABLE = props.getProperty("SUPABASE_TABLE");

        try {
            SupabaseHelper.Config cfg = SupabaseHelper.loadConfig();

            // Extract PDF text
            PdfExtractor pdfExtractor = new PdfExtractor();
            String docText = pdfExtractor.extract(pdfFile);

            // Chunk document
            DocumentChunker chunker = new DocumentChunker(500); // 500 tokens per chunk
            List<String> chunks = chunker.chunk(docText);
            LOGGER.fine(() -> "Total chunks: " + chunks.size());

            // Initialize embedding model
            EmbeddingModel model = new Qwen3EmbeddingModel();

            // Prepare and insert into Supabase
            HttpClient client = HttpClient.newHttpClient();
            for (int i = 0; i < chunks.size(); i++) {
                final int idx = i;
                String text = chunks.get(idx);
                float[] embedding = model.embed(text);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("doc_id", pdfFile);
                metadata.put("length", text.length());
                metadata.put("author", "Unknown");
                metadata.put("page_number", -1);
                metadata.put("created_at", new Date().toString());

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("chunk_index", idx);
                row.put("chunk_text", text);
                row.put("metadata", metadata);
                row.put("embedding", embeddingToList(embedding));

                String jsonBody = MAPPER.writeValueAsString(row);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SUPABASE_URL + "/rest/v1/" + SUPABASE_TABLE))
                        .header("apikey", SUPABASE_API_KEY)
                        .header("Authorization", "Bearer " + SUPABASE_API_KEY)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("[" + jsonBody + "]"))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    LOGGER.fine(() -> String.format("Inserted chunk %d successfully.", idx));
                } else {
                    LOGGER.warning(() -> String.format("Failed to insert chunk %d: %s", idx, response.body()));
                }
            }

            // Retrieve similar chunks from Supabase
            List<SupabaseRetriever.Hit> hits = SupabaseHelper.retrieveHits(cfg, model, query, 5);

            // Compose answer using deepseek-r1:1.5b via Ollama
            AnswerComposer composer = new AnswerComposer(HttpClient.newHttpClient(),
                    props.getProperty("OLLAMA_URL"),
                    props.getProperty("OLLAMA_MODEL"));

            String answer = composer.compose(query, SupabaseHelper.toRetrieverHits(hits), 1200);
            LOGGER.info(answer);

        } catch (IOException e) {
            LOGGER.severe("IO Error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.severe("Error during workflow execution: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<Double> embeddingToList(float[] emb) {
        List<Double> list = new ArrayList<>(emb.length);
        for (float f : emb) list.add((double) f);
        return list;
    }
}
