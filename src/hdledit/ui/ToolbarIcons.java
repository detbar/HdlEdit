// src/main/java/hdledit/ui/ToolbarIcons.java
package hdledit.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class ToolbarIcons {

    private ToolbarIcons() {}

    public static Icon createIcon(Color color, String symbol) {
        int size = 18;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(color);
        g.fillRoundRect(1, 1, size - 2, size - 2, 5, 5);

        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        int x = (size - fm.stringWidth(symbol)) / 2;
        int y = (size + fm.getAscent()) / 2 - 2;
        g.drawString(symbol, x, y);

        g.dispose();
        return new ImageIcon(image);
    }

    public static final Icon NEW_PROJECT   = createIcon(new Color(60, 140, 60),  "+");
    public static final Icon OPEN_PROJECT  = createIcon(new Color(50, 100, 180), "O");
    public static final Icon SAVE_PROJECT  = createIcon(new Color(180, 120, 40), "S");
    public static final Icon CLOSE_PROJECT = createIcon(new Color(160, 60, 60),  "X");
    public static final Icon ADD_BLOCK     = createIcon(new Color(80, 80, 160),  "B");
    public static final Icon CONNECT       = createIcon(new Color(40, 120, 160), "→");
}