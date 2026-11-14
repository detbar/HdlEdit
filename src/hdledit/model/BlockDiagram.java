// src/main/java/hdledit/model/BlockDiagram.java
package hdledit.model;

public class BlockDiagram extends Diagram {
    public BlockDiagram(String name) {
        super(name, DiagramType.BLOCK_DIAGRAM);
    }

    public BlockDiagram(String id, String name) {
        super(id, name, DiagramType.BLOCK_DIAGRAM);
    }
}