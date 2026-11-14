// src/main/java/hdledit/model/Diagram.java
package hdledit.model;

import java.util.UUID;

public abstract class Diagram {

    private final String id;
    private String name;
    private final DiagramType type;

    public Diagram(String name, DiagramType type) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.type = type;
    }

    public Diagram(String id, String name, DiagramType type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public DiagramType getType() { return type; }
}