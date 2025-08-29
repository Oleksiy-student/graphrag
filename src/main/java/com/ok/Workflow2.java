package com.ok;

import com.ok.embeddings.*;
import com.ok.pipeline.*;
import com.ok.store.*;
import java.io.IOException;
import java.util.List;

public class Workflow2 {
    public static void run(String query) {
        try {
            GraphStore store = new TinkerGraphStore();
            store.loadGraph("graph.xml");
            store.loadEmbeddings("embeddings.json");

            EmbeddingModel model = new Qwen3EmbeddingModel();
            Retriever retriever = new Retriever(store, model);

            List<Retriever.Hit> hits = retriever.retrieve(query, 5, 2);

            AnswerComposer composer = new AnswerComposer();
            String answer = composer.compose(query, hits, 1200);

            System.out.println("\n=== DRAFT ANSWER ===\n" + answer);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
