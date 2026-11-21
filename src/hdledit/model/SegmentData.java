// src/main/java/hdledit/model/SegmentData.java
package hdledit.model;

/** Daten eines Verbindungsabschnitts zwischen zwei aufeinanderfolgenden Punkten. */
public class SegmentData {

    public enum Kind {
        LINE,
        BEZIER
    }

    private Kind kind = Kind.LINE;

    /** Kontrollpunkt 1 (absolut), nur bei BEZIER. null = automatisch. */
    private Integer c1x, c1y;
    /** Kontrollpunkt 2 (absolut), nur bei BEZIER. null = automatisch. */
    private Integer c2x, c2y;

    public SegmentData() {}

    public SegmentData(Kind kind) {
        this.kind = kind != null ? kind : Kind.LINE;
    }

    public SegmentData(Kind kind, Integer c1x, Integer c1y, Integer c2x, Integer c2y) {
        this.kind = kind != null ? kind : Kind.LINE;
        this.c1x = c1x;
        this.c1y = c1y;
        this.c2x = c2x;
        this.c2y = c2y;
    }

    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind != null ? kind : Kind.LINE; }

    public Integer getC1x() { return c1x; }
    public Integer getC1y() { return c1y; }
    public Integer getC2x() { return c2x; }
    public Integer getC2y() { return c2y; }

    public void setC1(int x, int y) { this.c1x = x; this.c1y = y; }
    public void setC2(int x, int y) { this.c2x = x; this.c2y = y; }

    public boolean hasControls() {
        return c1x != null && c1y != null && c2x != null && c2y != null;
    }

    public void clearControls() {
        c1x = c1y = c2x = c2y = null;
    }
}
