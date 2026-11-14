package com.storyforge.orchestrator.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ReviewSheetTest {

    private static BufferedImage filled(Color colour) {
        BufferedImage image = new BufferedImage(384, 216, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(colour);
        g.fillRect(0, 0, 384, 216);
        g.dispose();
        return image;
    }

    @Test
    void aFlatCardOrABlackFrameShowsNothing() {
        assertThat(ReviewSheet.flat(filled(new Color(40, 60, 120)))).isTrue();
        assertThat(ReviewSheet.flat(filled(new Color(6, 6, 8)))).isTrue();
    }

    @Test
    void aPictureOrTextOnACardShowsSomething() {
        BufferedImage noise = new BufferedImage(384, 216, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(1);
        for (int y = 0; y < 216; y++) {
            for (int x = 0; x < 384; x++) {
                int v = 40 + random.nextInt(160);
                noise.setRGB(x, y, new Color(v, v, v).getRGB());
            }
        }
        assertThat(ReviewSheet.flat(noise)).isFalse();

        BufferedImage card = filled(new Color(30, 40, 80));
        Graphics2D g = card.createGraphics();
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 60));
        g.drawString("TYLER DURDEN", 10, 130);
        g.dispose();
        assertThat(ReviewSheet.flat(card)).isFalse();
    }

    @Test
    void longSpokenLinesWrapToTheTileWidth() {
        Graphics2D g = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        var lines = ReviewSheet.wrap("Every time Tyler Durden walks on screen, my testosterone climbs and my half-grown "
                + "beard finishes itself, and honestly that is the whole review.", g.getFontMetrics(), 200);
        assertThat(lines.size()).isGreaterThan(2);
        assertThat(lines).allSatisfy(line -> assertThat(g.getFontMetrics().stringWidth(line)).isLessThanOrEqualTo(200));
        assertThat(ReviewSheet.wrap(null, g.getFontMetrics(), 200)).isEmpty();
    }
}
