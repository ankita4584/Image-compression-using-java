import java.awt.image.BufferedImage;

public class CompressionMetrics {

    /**
     * Computes PSNR (Peak Signal-to-Noise Ratio) between two images.
     * Higher = better quality retained. Infinity means pixel-perfect (identical).
     */
    public static double computePSNR(BufferedImage original, BufferedImage reconstructed) {
        if (original.getWidth() != reconstructed.getWidth()
                || original.getHeight() != reconstructed.getHeight()) {
            throw new IllegalArgumentException("Images must be the same dimensions to compare");
        }

        int width = original.getWidth();
        int height = original.getHeight();
        long sumSquaredError = 0;
        long totalValues = (long) width * height * 3; // R, G, B per pixel

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int origRGB = original.getRGB(x, y);
                int reconRGB = reconstructed.getRGB(x, y);

                int origR = (origRGB >> 16) & 0xFF, origG = (origRGB >> 8) & 0xFF, origB = origRGB & 0xFF;
                int reconR = (reconRGB >> 16) & 0xFF, reconG = (reconRGB >> 8) & 0xFF, reconB = reconRGB & 0xFF;

                sumSquaredError += (long) Math.pow(origR - reconR, 2);
                sumSquaredError += (long) Math.pow(origG - reconG, 2);
                sumSquaredError += (long) Math.pow(origB - reconB, 2);
            }
        }

        double mse = (double) sumSquaredError / totalValues;

        if (mse == 0) {
            return Double.POSITIVE_INFINITY; // pixel-perfect match
        }

        double maxPixelValue = 255.0;
        return 10 * Math.log10((maxPixelValue * maxPixelValue) / mse);
    }

    public static double computeCompressionRatio(long originalSizeBytes, long compressedSizeBytes) {
        return (double) compressedSizeBytes / originalSizeBytes;
    }

}