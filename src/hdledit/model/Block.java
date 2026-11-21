// src/main/java/hdledit/model/Block.java
package hdledit.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Block {

    public enum PortSide {
        NORTH, EAST, SOUTH, WEST
    }

    private final String id;
    private String name;
    private int x;
    private int y;
    private int width;
    private int height;
    private final List<Port> ports = new ArrayList<>();

    public Block(String name, int x, int y) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.x = x;
        this.y = y;
        this.width = 120;
        this.height = 60;
        addDefaultPorts();
    }

    public Block(String id, String name, int x, int y, int width, int height) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        // Ports werden separat geladen / addDefaultPorts falls leer
    }

    private void addDefaultPorts() {
        ports.add(new Port(PortSide.NORTH, 0.5));
        ports.add(new Port(PortSide.EAST, 0.5));
        ports.add(new Port(PortSide.SOUTH, 0.5));
        ports.add(new Port(PortSide.WEST, 0.5));
    }

    public void ensureDefaultPorts() {
        if (ports.isEmpty()) addDefaultPorts();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public boolean contains(int px, int py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    public int centerX() { return x + width / 2; }
    public int centerY() { return y + height / 2; }

    public List<Port> getPorts() { return ports; }

    public void addPort(Port port) { ports.add(port); }

    public void removePort(Port port) { ports.remove(port); }

    public Port findPort(String portId) {
        for (Port p : ports) {
            if (p.getId().equals(portId)) return p;
        }
        return null;
    }

    /** Weltkoordinaten eines Ports. */
    public int[] getPortPoint(Port port) {
        double t = port.getPosition();
        return switch (port.getSide()) {
            case NORTH -> new int[]{x + (int) Math.round(width * t), y};
            case EAST  -> new int[]{x + width, y + (int) Math.round(height * t)};
            case SOUTH -> new int[]{x + (int) Math.round(width * t), y + height};
            case WEST  -> new int[]{x, y + (int) Math.round(height * t)};
        };
    }

    /** Legacy: Mitte einer Seite (für Fallback). */
    public int[] getPortPoint(PortSide side) {
        return getPortPoint(new Port(side, 0.5));
    }

    public PortSide bestSideToward(int targetX, int targetY) {
        int cx = centerX();
        int cy = centerY();
        int dx = targetX - cx;
        int dy = targetY - cy;
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx >= 0 ? PortSide.EAST : PortSide.WEST;
        }
        return dy >= 0 ? PortSide.SOUTH : PortSide.NORTH;
    }

    /** Nächster freier Port in Richtung target (oder null). */
    public Port nearestPortToward(int targetX, int targetY, java.util.Set<String> occupiedPortIds) {
        PortSide preferred = bestSideToward(targetX, targetY);
        Port best = null;
        double bestDist = Double.MAX_VALUE;
        for (Port p : ports) {
            if (occupiedPortIds != null && occupiedPortIds.contains(p.getId())) continue;
            int[] pt = getPortPoint(p);
            double d = Math.hypot(pt[0] - targetX, pt[1] - targetY);
            // leichte Bevorzugung der bevorzugten Seite
            if (p.getSide() == preferred) d *= 0.7;
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /**
     * Bestimmt Seite und Position (0..1) für einen Punkt nahe am Blockrand.
     * @return null wenn zu weit weg
     */
    public Object[] edgeHit(int px, int py, int tolerance) {
        // Abstand zu jeder Kante prüfen
        int left = x, right = x + width, top = y, bottom = y + height;

        // NORTH
        if (Math.abs(py - top) <= tolerance && px >= left - tolerance && px <= right + tolerance) {
            double t = (px - left) / (double) width;
            return new Object[]{PortSide.NORTH, clamp01(t)};
        }
        // SOUTH
        if (Math.abs(py - bottom) <= tolerance && px >= left - tolerance && px <= right + tolerance) {
            double t = (px - left) / (double) width;
            return new Object[]{PortSide.SOUTH, clamp01(t)};
        }
        // WEST
        if (Math.abs(px - left) <= tolerance && py >= top - tolerance && py <= bottom + tolerance) {
            double t = (py - top) / (double) height;
            return new Object[]{PortSide.WEST, clamp01(t)};
        }
        // EAST
        if (Math.abs(px - right) <= tolerance && py >= top - tolerance && py <= bottom + tolerance) {
            double t = (py - top) / (double) height;
            return new Object[]{PortSide.EAST, clamp01(t)};
        }
        return null;
    }

    private static double clamp01(double t) {
        if (t < 0.05) return 0.05;
        if (t > 0.95) return 0.95;
        return t;
    }
}
