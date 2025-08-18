package org.steam5.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.steam5.job.blurhash.BlurHash;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Base64;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class BlurhashService {

    static final Map<Type, ImageSize> IMAGE_SIZES = Map.of(
            Type.THUMBNAIL, new ImageSize(32, 20),
            Type.FULL, new ImageSize(64, 36)
    );

    /**
     * Converts a given BufferedImage to a PNG data URL with specified dimensions.
     *
     * <p>This method takes an image represented by a {@link BufferedImage} and scales it
     * to the provided width and height. The scaled image is then encoded as a PNG format
     * byte array, which is subsequently converted into a Base64 string. Finally, this Base64
     * string is prefixed with "data:image/png;base64," to form the complete data URL suitable for
     * embedding in HTML or CSS contexts.</p>
     *
     * @param src The source BufferedImage to be scaled and encoded.
     * @param w   The desired width of the output image.
     * @param h   The desired height of the output image.
     * @return A string representing the PNG data URL, or null if an error occurs during processing.
     */
    public static String toPngDataUrl(BufferedImage src, int w, int h) {
        try {
            final BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            final Graphics2D g2 = scaled.createGraphics();
            g2.drawImage(src, 0, 0, w, h, null);
            g2.dispose();
            final ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            ImageIO.write(scaled, "png", outputStream);
            final String b64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            return "data:image/png;base64," + b64;
        } catch (Exception e) {
            log.error("Error creating png data url for image", e);
            return null;
        }
    }

    /**
     * Reads an image from the specified URL and encodes it using BlurHash.
     *
     * <p>This method takes a URL pointing to an image resource and a type specifying the desired size of the output.
     * It checks if the provided type is valid by looking up predefined sizes in {@link #IMAGE_SIZES}. If the type
     * is invalid, an {@code IllegalArgumentException} is thrown.</p>
     *
     * <p>The image is read from the URL into a {@link BufferedImage}. If reading fails or returns null, the method
     * returns null. A BlurHash string representing a low-resolution preview of the image is generated using the
     * {@link BlurHash#encode(BufferedImage, int, int)} method with preset parameters.</p>
     *
     * <p>Next, the image is scaled to the dimensions specified by the type and converted into a PNG data URL via
     * {@link #toPngDataUrl(BufferedImage, int, int)}. The resulting encoded representation of the image consists
     * of both the BlurHash string and the PNG data URL.</p>
     *
     * <p>If any error occurs during these operations (e.g., issues with opening the stream, reading the image, or
     * encoding), an error message is logged, and the method returns null.</p>
     *
     * @param url  The URL pointing to the image resource to be read and encoded.
     * @param type The desired size type of the output, defined by {@link Type}. Determines the dimensions used for scaling.
     * @return An instance of {@link Encoded} containing a BlurHash string and a PNG data URL if successful;
     * otherwise, null if an error occurs or the image is invalid.
     * @throws IllegalArgumentException if the provided type does not have associated predefined dimensions in {@link #IMAGE_SIZES}.
     */
    public Encoded readAndEncode(String url, Type type) {
        if (IMAGE_SIZES.get(type) == null) {
            throw new IllegalArgumentException("Invalid type=" + type);
        }

        try (InputStream in = URI.create(url).toURL().openStream()) {
            final BufferedImage img = ImageIO.read(in);
            if (img == null) return null;
            final String hash = BlurHash.encode(img, 4, 4);
            final String data = toPngDataUrl(img, IMAGE_SIZES.get(type).width, IMAGE_SIZES.get(type).height);
            return new Encoded(hash, data);
        } catch (Exception e) {
            log.error("Error encoding image from url={}", url, e);
            return null;
        }
    }


    public enum Type {
        THUMBNAIL,
        FULL
    }

    record ImageSize(int width, int height) {
    }

    public record Encoded(String hash, String dataUrl) {
    }
}
