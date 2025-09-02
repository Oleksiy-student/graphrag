package com.ok;

import com.ok.pipeline.AnswerComposer;
import com.ok.pipeline.Retriever;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class AnswerComposerTest {

    @Test
    public void testComposeWithMockHttpClient() throws Exception {
        // Mock HttpClient
        HttpClient mockClient = Mockito.mock(HttpClient.class);
        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"response\":\"GraphRAG augments retrieval with graph-based reasoning.\"}");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // Load config.properties
        Properties props = new Properties();
        try (InputStream in = new FileInputStream("config.properties")) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }

        // Create AnswerComposer using mocked HttpClient and config values
        AnswerComposer composer = new AnswerComposer(
                mockClient,
                props.getProperty("OLLAMA_URL"),
                props.getProperty("OLLAMA_MODEL")
        );

        // Sample hits
        List<Retriever.Hit> hits = Arrays.asList(
                new Retriever.Hit("chunk1", "GraphRAG augments retrieval with graphs", 0.9),
                new Retriever.Hit("chunk2", "Neo4j is a graph database", 0.8)
        );

        // Compose answer
        String query = "What is GraphRAG?";
        String answer = composer.compose(query, hits, 500);

        // Assertions
        assertNotNull(answer);
        assertTrue(answer.contains("Draft Answer"));
        assertTrue(answer.toLowerCase().contains("graphrag"));
        assertTrue(answer.contains("GraphRAG augments retrieval"));
    }
}
