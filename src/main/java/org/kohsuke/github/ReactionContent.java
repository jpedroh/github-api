package org.kohsuke.github;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ReactionContent {
  PLUS_ONE("+1"),
  MINUS_ONE("-1"),
  LAUGH("laugh"),
  CONFUSED("confused"),
  HEART("heart"),
  HOORAY("hooray"),
  ROCKET("rocket"),
  EYES("eyes")
  ;

  private final String content;

  ReactionContent(String content) {
    this.content = content;
  }

  @JsonValue public String getContent() {
    return content;
  }

  @JsonCreator public static ReactionContent forContent(String content) {
    for (ReactionContent c : ReactionContent.values()) {
      if (c.getContent().equals(content)) {
        return c;
      }
    }
    return null;
  }
}