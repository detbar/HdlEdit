// src/main/java/hdledit/model/BlockDiagram.java
package hdledit.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlockDiagram extends Diagram {

    private final List<Block> blocks = new ArrayList<>();
    private final List<Connection> connections = new ArrayList<>();

    public BlockDiagram(String name) {
        super(name, DiagramType.BLOCK_DIAGRAM);
    }

    public BlockDiagram(String id, String name) {
        super(id, name, DiagramType.BLOCK_DIAGRAM);
    }

    public List<Block> getBlocks() { return blocks; }

    public void addBlock(Block block) { blocks.add(block); }

    public void removeBlock(Block block) {
        blocks.remove(block);
        connections.removeIf(c -> c.getFrom() == block || c.getTo() == block);
    }

    public List<Connection> getConnections() { return connections; }

    public void addConnection(Connection connection) { connections.add(connection); }

    public void removeConnection(Connection connection) { connections.remove(connection); }

    public Set<String> occupiedPortIds() {
        Set<String> ids = new HashSet<>();
        for (Connection c : connections) {
            if (c.getFromPort() != null) ids.add(c.getFromPort().getId());
            if (c.getToPort() != null) ids.add(c.getToPort().getId());
        }
        return ids;
    }

    public boolean isPortOccupied(Port port) {
        if (port == null) return false;
        String id = port.getId();
        for (Connection c : connections) {
            if (c.getFromPort() != null && id.equals(c.getFromPort().getId())) return true;
            if (c.getToPort() != null && id.equals(c.getToPort().getId())) return true;
        }
        return false;
    }

    public void removeConnectionsUsingPort(Port port) {
        if (port == null) return;
        String id = port.getId();
        connections.removeIf(c ->
                (c.getFromPort() != null && id.equals(c.getFromPort().getId()))
                        || (c.getToPort() != null && id.equals(c.getToPort().getId())));
    }
}
