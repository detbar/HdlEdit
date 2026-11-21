// src/main/java/hdledit/model/Block.java
package hdledit.model;

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

    public Block(String name, int x, int y) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.x = x;
        this.y = y;
        this.width = 120;
        this.height = 60;
    }

    public Block(String id, String name, int x, int y, int width, int height) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
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

    public int centerX() {
        return x + width / 2;
    }

    public int centerY() {
        return y + height / 2;
    }

    /** Ankerpunkt in der Mitte der angegebenen Seite. */
    public int[] getPortPoint(PortSide side) {
        return switch (side) {
            case NORTH -> new int[]{x + width / 2, y};
            case EAST  -> new int[]{x + width, y + height / 2};
            case SOUTH -> new int[]{x + width / 2, y + height};
            case WEST  -> new int[]{x, y + height / 2};
        };
    }

    /**
     * Wählt die beste Austritts-/Eintrittsseite relativ zu einem Zielpunkt.
     */
    public PortSide bestSideToward(int targetX, int targetY) {
        int cx = centerX();
        int cy = centerY();
        int dx = targetX - cx;
        int dy = targetY - cy;

        if (Math.abs(dx) > Math.abs(dy)) {
            return dx >= 0 ? PortSide.EAST : PortSide.WEST;
        } else {
            return dy >= 0 ? PortSide.SOUTH : PortSide.NORTH;
        }
    }
}
