
import java.util.*;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class Main {
    public static void main(String[] args) {
        String inputImagePath = "spice_factory.jpg"; // make sure this file exists
        String outputImagePath = "compress.png"; // will be overwritten
        float threshold = 1.0f;

        try {
            CompressImage compressor = new CompressImage(inputImagePath, threshold);
            compressor.build();
            compressor.saveCompressed(outputImagePath);
            
            // compressor.saveEdge("edges_only.png", 2);
            compressor.saveBinary("compressed.qtc");

            // CompressImage loaded = CompressImage.loadBinary("compressed.qtc", "png");
            // BufferedImage reconstructed = loaded.img;
            // Graphics2D g = reconstructed.createGraphics();
            // g.setColor(Color.BLACK);
            // g.fillRect(0, 0, reconstructed.getWidth(), reconstructed.getHeight());
            // g.dispose();
            // loaded.qt.render(reconstructed);
            // ImageIO.write(reconstructed, "png", new
            // File("test_reconstructed_from_binary.png"));

            // System.out.println("Binary file saved to: " + new
            // File("compressed.qtc").getAbsolutePath());
            System.out.println("Compression complete. Output saved to " + outputImagePath);

            BufferedImage original = ImageIO.read(new File(inputImagePath));
            BufferedImage reconstructed = ImageIO.read(new File(outputImagePath));

            CompressionMetrics cm = new CompressionMetrics();
            double psnr = cm.computePSNR(original, reconstructed);

            long rawSize = (long) original.getWidth() * original.getHeight() * 3;
            long compressedSize = new File("compressed.qtc").length();
            double ratio = cm.computeCompressionRatio(rawSize, compressedSize);

            System.out.println("--- Compression + Quality Report ---");
            System.out.println("Raw size:        " + rawSize + " bytes");
            System.out.println("Compressed size: " + compressedSize + " bytes");
            System.out.println("Ratio:           " + String.format("%.2f%%", ratio * 100) + " of original");
            System.out.println("PSNR:   " + String.format("%.2f dB", psnr));

        } catch (IOException e) {
            System.err.println("Error during compression: " + e.getMessage());
        }

    }
}
