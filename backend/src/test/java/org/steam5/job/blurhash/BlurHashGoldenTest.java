package org.steam5.job.blurhash;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the encoder output for a deterministic image: the vendored port keeps only the encode
 * path, and this is what proves the deletions did not change it.
 */
class BlurHashGoldenTest {

    private static BufferedImage gradient() {
        final BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                image.setRGB(x, y, (x * 31) << 16 | (y * 31) << 8 | ((x + y) * 15));
            }
        }
        return image;
    }

    @Test
    void pinsTheHashForAKnownImage() {
        assertEquals("K9FPHD4AWj_O9Pb0e*fAfS", BlurHash.encode(gradient(), 3, 3));
    }
}
