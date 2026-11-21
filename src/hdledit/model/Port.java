// src/main/java/hdledit/model/Port.java
package hdledit.model;

import java.util.UUID;

/** Anschluss an einer Blockkante. position: 0.0 … 1.0 entlang der Seite. */
public class Port {

    private final String id;
    private Block.PortSide side;
    private double position; // 0 = Anfang der Seite, 1 = Ende

    public Port(Block.PortSide side, double position) {
        this(UUID.randomUUID().toString(), side, position);
    }

    public Port(String id, Block.PortSide side, double position) {
        this.id = id;
        this.side = side;
        this.position = clamp(position);
    }

    public String getId() { return id; }
    public Block.PortSide getSide() { return side; }
    public void setSide(Block.PortSide side) { this.side = side; }
    public double getPosition() { return position; }
    public void setPosition(double position) { this.position = clamp(position); }

    private static double clamp(double p) {
        if (p < 0.05) return 0.05;
        if (p > 0.95) return 0.95;
        return p;
    }
}
