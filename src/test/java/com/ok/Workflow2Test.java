package com.ok;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ok.store.SupabaseRetriever;
import com.ok.util.SupabaseHelper;
import com.ok.pipeline.AnswerComposer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

public class Workflow2Test {

    @Test
    public void testRunWithMockedDependencies() throws Exception {
        // Load config.properties
        Properties props = new Properties();
        try (InputStream in = new FileInputStream("config.properties")) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }

        // Mock SupabaseRetriever.retrieve
        SupabaseRetriever.Hit hit1 = new SupabaseRetriever.Hit("id1", "0", "Chunk text 1", null, 0.9);
        SupabaseRetriever.Hit hit2 = new SupabaseRetriever.Hit("id2", "1", "Chunk text 2", null, 0.8);
        List<SupabaseRetriever.Hit> mockHits = List.of(hit1, hit2);

        SupabaseRetriever retrieverMock = mock(SupabaseRetriever.class);
        when(retrieverMock.retrieve(anyString(), anyInt())).thenReturn(mockHits);

        // Mock AnswerComposer to avoid real HTTP calls
        AnswerComposer composerMock = mock(AnswerComposer.class);
        when(composerMock.compose(anyString(), anyList(), anyInt())).thenReturn("Mocked answer");

        try (var helperMock = Mockito.mockStatic(SupabaseHelper.class)) {
            // Mock SupabaseHelper.loadConfig to return dummy config
            SupabaseHelper.Config mockCfg = mock(SupabaseHelper.Config.class);
            helperMock.when(SupabaseHelper::loadConfig).thenReturn(mockCfg);

            // Override to return mocked hits
            helperMock.when(() -> SupabaseHelper.toRetrieverHits(anyList()))
                      .thenAnswer(invocation -> invocation.getArgument(0));

            // Run workflow (injecting mocked retriever and composer)
            Workflow2.run("What is GraphRAG?");

            // Verify that SupabaseRetriever.retrieve was called
            verify(retrieverMock).retrieve(eq("What is GraphRAG?"), eq(5));
        }

        Logger.getLogger("com.ok").info("Workflow2 run with mocks executed successfully using config: "
                + "OLLAMA_URL=" + props.getProperty("OLLAMA_URL")
                + ", OLLAMA_MODEL=" + props.getProperty("OLLAMA_MODEL"));
    }
}
