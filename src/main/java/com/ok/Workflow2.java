package com.ok;

import com.ok.embeddings.*;
import com.ok.pipeline.*;
import com.ok.store.*;
import com.ok.util.*;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.logging.Logger;

public class Workflow2 {
    private static final Logger LOGGER = Logger.getLogger(Workflow2.class.getName());

    public static void run(String query) {
        try {
            // Load Supabase config
            SupabaseHelper.Config cfg = SupabaseHelper.loadConfig();
            EmbeddingModel model = new Qwen3EmbeddingModel();

            // Load existing graph and embeddings
            TinkerGraphStore graphStore = new TinkerGraphStore();
            try {
                graphStore.loadGraph("graph_document.graphml");
                graphStore.loadEmbeddings("graph_embeddings.json");
                LOGGER.info("Loaded existing graph and embeddings.");
            } catch (IOException e) {
                LOGGER.severe("Failed to load graph or embeddings: " + e.getMessage());
                return;
            }

            // Retrieve relevant chunks from Supabase
            List<SupabaseRetriever.Hit> hits = SupabaseHelper.retrieveHits(cfg, model, query, 5);
            if (hits.isEmpty()) {
                LOGGER.warning("No relevant hits retrieved from Supabase.");
            }

            // Compose answer using Deepseek-R1
            String ollamaUrl = Config.get("OLLAMA_URL");
            String ollamaModel = Config.get("OLLAMA_MODEL");

            AnswerComposer composer = new AnswerComposer(
                    HttpClient.newHttpClient(),
                    ollamaUrl,
                    ollamaModel
            );

            String answer = composer.compose(query, SupabaseHelper.toRetrieverHits(hits), 1200);
            LOGGER.info("Answer:\n" + answer);

        } catch (Exception e) {
            LOGGER.severe("Workflow2 failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
