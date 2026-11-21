// src/main/java/hdledit/model/Connection.java
package hdledit.model;

import java.util.UUID;

public class Connection {

    public enum Style {
        DIRECT,
        ORTHOGONAL,
        BEZIER
    }

    private final String id;
    private Block from;
    private Block to;
    private Style style;

    public Connection(Block from, Block to, Style style) {
        this.id = UUID.randomUUID().toString();
        this.from = from;
        this.to = to;
        this.style = style != null ? style : Style.DIRECT;
    }

    public Connection(String id, Block from, Block to, Style style) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.style = style != null ? style : Style.DIRECT;
    }

    public String getId() { return id; }
    public Block getFrom() { return from; }
    public void setFrom(Block from) { this.from = from; }
    public Block getTo() { return to; }
    public void setTo(Block to) { this.to = to; }
    public Style getStyle() { return style; }
    public void setStyle(Style style) { this.style = style; }
}
