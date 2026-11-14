// src/main/java/hdledit/model/Flowchart.java
package hdledit.model;

public class Flowchart extends Diagram {
    public Flowchart(String name) {
        super(name, DiagramType.FLOWCHART);
    }

    public Flowchart(String id, String name) {
        super(id, name, DiagramType.FLOWCHART);
    }
}