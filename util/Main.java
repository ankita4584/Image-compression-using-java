
import java.util.*;
import java.io.IOException;
public class Main {
    public static void main(String[] args) {
        String inputImagePath = "spice_factory.jpg";       // make sure this file exists
        String outputImagePath = "compress.png";     // will be overwritten
        float threshold = 1.0f;

        try {
            CompressImage compressor = new CompressImage(inputImagePath, threshold);
            compressor.build();
            compressor.saveCompressed(outputImagePath);
            compressor.saveEdge("edges_only.png", 2);

            System.out.println("Compression complete. Output saved to " + outputImagePath);
        } catch (IOException e) {
            System.err.println("Error during compression: " + e.getMessage());
        }
    }
}
