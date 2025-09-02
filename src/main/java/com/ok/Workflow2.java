package com.ok;

import com.ok.embeddings.*;
import com.ok.pipeline.*;
import com.ok.store.*;
import com.ok.util.SupabaseHelper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

public class Workflow2 {
    private static final Logger LOGGER = Logger.getLogger(Workflow2.class.getName());

    public static void run(String query) {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream("config.properties")) {
            props.load(in);
        } catch (IOException e) {
            LOGGER.severe("Failed to load config.properties: " + e.getMessage());
            return;
        }

        try {
            SupabaseHelper.Config cfg = SupabaseHelper.loadConfig();
            EmbeddingModel model = new Qwen3EmbeddingModel();

            // Retrieve hits
            List<SupabaseRetriever.Hit> hits = SupabaseHelper.retrieveHits(cfg, model, query, 5);

            // Compose answer using DeepSeek-R1 via Ollama
            AnswerComposer composer = new AnswerComposer(
                    HttpClient.newHttpClient(),
                    props.getProperty("OLLAMA_URL"),
                    props.getProperty("OLLAMA_MODEL")
            );

            String answer = composer.compose(query, SupabaseHelper.toRetrieverHits(hits), 1200);
            LOGGER.info(answer);

        } catch (IOException e) {
            LOGGER.severe("IO Error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.severe("Workflow2 failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
