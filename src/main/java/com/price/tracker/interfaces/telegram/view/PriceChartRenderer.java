package com.price.tracker.interfaces.telegram.view;

import com.price.tracker.domain.entity.PriceSnapshot;
import com.price.tracker.util.KyivTime;
import com.price.tracker.util.PriceFormat;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Renders the price history as a PNG with plain Java2D - the chart is a bot
 * nicety, not worth a charting dependency.
 */
@Component
public class PriceChartRenderer {

    private static final int WIDTH = 900;
    private static final int HEIGHT = 450;
    private static final int MARGIN_LEFT = 90;
    private static final int MARGIN_RIGHT = 30;
    private static final int MARGIN_TOP = 30;
    private static final int MARGIN_BOTTOM = 50;
    private static final int GRID_LINES = 4;
    private static final Color LINE_COLOR = new Color(36, 129, 204);
    private static final Color GRID_COLOR = new Color(225, 225, 225);
    private static final Color TEXT_COLOR = new Color(60, 60, 60);
    private static final Color OUT_OF_STOCK_COLOR = new Color(204, 68, 51);

    public byte[] render(List<PriceSnapshot> history) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            drawChart(graphics, history);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", output);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return output.toByteArray();
    }

    private void drawChart(Graphics2D graphics, List<PriceSnapshot> history) {
        double minPrice = history.stream().mapToDouble(s -> s.getPrice().doubleValue()).min().orElse(0);
        double maxPrice = history.stream().mapToDouble(s -> s.getPrice().doubleValue()).max().orElse(0);
        // Flat history still needs a visible band around the line
        double padding = Math.max((maxPrice - minPrice) * 0.15, Math.max(maxPrice * 0.02, 1));
        double low = minPrice - padding;
        double high = maxPrice + padding;
        int plotWidth = WIDTH - MARGIN_LEFT - MARGIN_RIGHT;
        int plotHeight = HEIGHT - MARGIN_TOP - MARGIN_BOTTOM;

        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        for (int line = 0; line <= GRID_LINES; line++) {
            int y = MARGIN_TOP + plotHeight * line / GRID_LINES;
            double price = high - (high - low) * line / GRID_LINES;
            graphics.setColor(GRID_COLOR);
            graphics.drawLine(MARGIN_LEFT, y, WIDTH - MARGIN_RIGHT, y);
            graphics.setColor(TEXT_COLOR);
            graphics.drawString(PriceFormat.format(BigDecimal.valueOf(Math.round(price))), 8, y + 5);
        }

        long firstTime = history.getFirst().getCheckedAt().toEpochMilli();
        long lastTime = history.getLast().getCheckedAt().toEpochMilli();
        long timeSpan = Math.max(lastTime - firstTime, 1);

        graphics.setColor(TEXT_COLOR);
        graphics.drawString(KyivTime.format(history.getFirst().getCheckedAt()), MARGIN_LEFT, HEIGHT - 15);
        String lastLabel = KyivTime.format(history.getLast().getCheckedAt());
        int lastLabelWidth = graphics.getFontMetrics().stringWidth(lastLabel);
        graphics.drawString(lastLabel, WIDTH - MARGIN_RIGHT - lastLabelWidth, HEIGHT - 15);

        graphics.setColor(LINE_COLOR);
        graphics.setStroke(new BasicStroke(2.5f));
        int previousX = -1;
        int previousY = -1;
        for (PriceSnapshot snapshot : history) {
            int x = MARGIN_LEFT + (int) (plotWidth * (snapshot.getCheckedAt().toEpochMilli() - firstTime) / (double) timeSpan);
            int y = MARGIN_TOP + (int) (plotHeight * (high - snapshot.getPrice().doubleValue()) / (high - low));
            if (previousX >= 0) {
                graphics.drawLine(previousX, previousY, x, y);
            }
            previousX = x;
            previousY = y;
        }
        for (PriceSnapshot snapshot : history) {
            int x = MARGIN_LEFT + (int) (plotWidth * (snapshot.getCheckedAt().toEpochMilli() - firstTime) / (double) timeSpan);
            int y = MARGIN_TOP + (int) (plotHeight * (high - snapshot.getPrice().doubleValue()) / (high - low));
            graphics.setColor(snapshot.isInStock() ? LINE_COLOR : OUT_OF_STOCK_COLOR);
            graphics.fillOval(x - 4, y - 4, 8, 8);
        }
    }
}
