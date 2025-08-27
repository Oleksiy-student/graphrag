package com.ok.pipeline;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


/**
* Minimal heuristic NER: extracts capitalized multiword phrases as entities
* and labels them as "Thing". Replace with spaCy/Stanford/LLM later.
*/
public class EntityExtractor {
  private static final Pattern PHRASE = Pattern.compile("(?:[A-Z][a-z]+(?:\\s+[A-Z][a-z]+){0,3})");
  private static final Set<String> STOP = Set.of("The","A","An","And","Of","In","On","At","For");


  public List<String> extract(String text) {
    List<String> ents = new ArrayList<>();
    Matcher m = PHRASE.matcher(text);
    while (m.find()) {
      String cand = m.group().trim();
      if (STOP.contains(cand)) continue;
        // Quick filter: require at least one lowercase to avoid ALLCAPS noise
        if (!cand.chars().anyMatch(Character::isLowerCase)) continue;
          ents.add(cand);
    }
    return ents;
  }
}
