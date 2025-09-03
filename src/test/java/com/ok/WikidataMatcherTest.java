package com.ok;

import com.ok.pipeline.WikidataMatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WikidataMatcherTest {

  @Test
  public void testMatchRealEntity() {
    WikidataMatcher matcher = new WikidataMatcher();
    WikidataMatcher.Match match = matcher.match("Paris");

    assertNotNull(match, "Match should not be null");
    assertEquals("Paris", match.getLabel(), "Entity label should be Paris");
    assertNotNull(match.getId(), "Wikidata ID should not be null");
    assertFalse(match.getId().isBlank(), "Wikidata ID should not be blank");

    // Relations might vary, but should not throw
    assertNotNull(match.getRelations(), "Relations should not be null");
  }
}
