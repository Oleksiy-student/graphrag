package com.ok.pipeline;

public class Entity {
  private final String name;
  private final String type;

  public Entity(String name, String type) {
    this.name = name;
    this.type = type;
  }

  public String getName() { return name; }
  public String getType() { return type; }

  @Override
  public String toString() {
    return "Entity{name='" + name + "', type='" + type + "'}";
  }
}
