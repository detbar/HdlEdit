// src/main/java/hdledit/model/BlockDiagram.java
package hdledit.model;

import java.util.ArrayList;
import java.util.List;

public class BlockDiagram extends Diagram {

    private final List<Block> blocks = new ArrayList<>();
    private final List<Connection> connections = new ArrayList<>();

    public BlockDiagram(String name) {
        super(name, DiagramType.BLOCK_DIAGRAM);
    }

    public BlockDiagram(String id, String name) {
        super(id, name, DiagramType.BLOCK_DIAGRAM);
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    public void addBlock(Block block) {
        blocks.add(block);
    }

    public void removeBlock(Block block) {
        blocks.remove(block);
        // Zugehörige Verbindungen auch entfernen
        connections.removeIf(c -> c.getFrom() == block || c.getTo() == block);
    }

    public List<Connection> getConnections() {
        return connections;
    }

    public void addConnection(Connection connection) {
        connections.add(connection);
    }

    public void removeConnection(Connection connection) {
        connections.remove(connection);
    }
}