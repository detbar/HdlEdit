// src/main/java/hdledit/model/TableDiagram.java
package hdledit.model;

public class TableDiagram extends Diagram {
    public TableDiagram(String name) {
        super(name, DiagramType.TABLE);
    }

    public TableDiagram(String id, String name) {
        super(id, name, DiagramType.TABLE);
    }
}