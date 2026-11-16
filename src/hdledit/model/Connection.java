// src/main/java/hdledit/model/Connection.java
package hdledit.model;

import java.util.UUID;

public class Connection {

    private final String id;
    private final Block from;
    private final Block to;

    public Connection(Block from, Block to) {
        this.id = UUID.randomUUID().toString();
        this.from = from;
        this.to = to;
    }

    public Connection(String id, Block from, Block to) {
        this.id = id;
        this.from = from;
        this.to = to;
    }

    public String getId() { return id; }
    public Block getFrom() { return from; }
    public Block getTo() { return to; }
}