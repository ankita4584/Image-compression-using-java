import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class CompressImage {
    class QuadTree {
        class QuadTreeNode {
            int x, y, w, h;
            int[] averageCbCr = new int[2];
            double error;
            Color averageColor;
            int atDepth;
            QuadTreeNode tl, tr, bl, br;
            boolean isLeaf = false;

            public QuadTreeNode(BufferedImage image, int x, int y, int w, int h, int atDepth) {
                if (w <= 1 || h <= 1) {
                    return;
                }
                this.w = w;
                this.h = h;
                this.x = x;
                this.y = y;
                this.atDepth = atDepth;

                long avgRed, avgBlue, avgGreen;
                double redDeviation = 0, greenDeviation = 0, blueDeviation = 0;

                {
                    long totalRed = 0;
                    long totalGreen = 0;
                    long totalBlue = 0;

                    for (int row = y; row < y + h; row++) {
                        for (int col = x; col < x + w; col++) {
                            Color pixelColor = new Color(image.getRGB(col, row));
                            totalRed += Math.pow(pixelColor.getRed(), 2);
                            totalGreen += Math.pow(pixelColor.getGreen(), 2);
                            totalBlue += Math.pow(pixelColor.getBlue(), 2);
                        }
                    }

                    int numPixels = w * h;

                    avgRed = (long) Math.sqrt(totalRed / numPixels);
                    avgGreen = (long) Math.sqrt(totalGreen / numPixels);
                    avgBlue = (long) Math.sqrt(totalBlue / numPixels);

                    for (int row = y; row < y + h; row++) {
                        for (int col = x; col < x + w; col++) {
                            Color pixelColor = new Color(image.getRGB(col, row));
                            redDeviation += Math.pow(avgRed - pixelColor.getRed(), 2);
                            greenDeviation += Math.pow(avgGreen - pixelColor.getGreen(), 2);
                            blueDeviation += Math.pow(avgBlue - pixelColor.getBlue(), 2);
                        }
                    }

                    redDeviation /= numPixels;
                    greenDeviation /= numPixels;
                    blueDeviation /= numPixels;

                    this.averageColor = new Color((int) (avgRed), (int) (avgGreen),
                            (int) (avgBlue));
                    this.error = (Math.sqrt(redDeviation) + Math.sqrt(greenDeviation)
                            + Math.sqrt(blueDeviation)) / 3;
                }
            }

            private int splitDimension(int dimension) {
                return dimension % 2 == 0 ? dimension / 2 : (dimension - 1) / 2;
            }

            void split(BufferedImage image) {
                this.tl = new QuadTreeNode(image, x, y, splitDimension(w), splitDimension(h), atDepth + 1);
                this.tr = new QuadTreeNode(image, x + splitDimension(w), y, splitDimension(w), splitDimension(h),
                        atDepth + 1);
                this.bl = new QuadTreeNode(image, x, y + splitDimension(h), splitDimension(w), splitDimension(h),
                        atDepth + 1);
                this.br = new QuadTreeNode(image, x + splitDimension(w), y + splitDimension(h), splitDimension(w),
                        splitDimension(h), atDepth + 1);
            }
        }

        QuadTreeNode root;
        float threshold;
        int maxDepth;
        double maxError = Integer.MIN_VALUE;
        int leafNodes = 0;

        public QuadTree(BufferedImage img, float threshold) {
            this.root = new QuadTreeNode(img, 0, 0, img.getWidth(), img.getHeight(), 0);
            this.threshold = threshold;
            buildTree(img, this.root);
            this.maxDepth = this.findDepth(root);
        }

        private int findDepth(QuadTreeNode node) {
            if (node == null) {
                return -1;
            } else if (node.tl == null && node.tr == null && node.bl == null && node.br == null) {
                return 0;
            } else {
                int maxChildDepth = -1;
                maxChildDepth = Math.max(maxChildDepth, findDepth(node.tl));
                maxChildDepth = Math.max(maxChildDepth, findDepth(node.tr));
                maxChildDepth = Math.max(maxChildDepth, findDepth(node.bl));
                maxChildDepth = Math.max(maxChildDepth, findDepth(node.br));
                return maxChildDepth + 1;
            }
        }

        private void buildTree(BufferedImage img, QuadTreeNode node) {
            if (node.error > maxError) {
                maxError = node.error;
            }
            if (node.error < (this.threshold * (node.atDepth + 1))) {
                node.isLeaf = true;
                leafNodes++;
                return;
            }
            node.split(img);
            buildTree(img, node.tl);
            buildTree(img, node.tr);
            buildTree(img, node.bl);
            buildTree(img, node.br);
        }

        private void renderAtDepth(QuadTreeNode node, BufferedImage image, int depth) {
            Graphics2D graphics = image.createGraphics();
            renderAtDepth0(node, image, depth, graphics);
            graphics.dispose();
        }

        private void renderAtDepth0(QuadTreeNode node, BufferedImage image, int depth, Graphics2D graphics) {
            if (node == null) {
                return;
            }
            if (node.atDepth == depth) {
                int x = node.x;
                int y = node.y;
                int width = node.w;
                int height = node.h;
                graphics.setColor(node.averageColor);
                graphics.fillRect(x, y, width, height);
                return;
            }
            renderAtDepth0(node.tl, image, depth, graphics);
            renderAtDepth0(node.tr, image, depth, graphics);
            renderAtDepth0(node.bl, image, depth, graphics);
            renderAtDepth0(node.br, image, depth, graphics);
        }
        
        private void render0(QuadTreeNode node, BufferedImage image, int maxDepth) {
            if (node == null) {
                return;
            }

            for (int i = 0; i < maxDepth; i++) {
                renderAtDepth(node, image, i);
            }
        }

        private void renderEdges0(QuadTreeNode node, BufferedImage image, int edges, int maxDepth) {
            if (node == null) {
                return;

            }

            for (int i = maxDepth - 1; i > maxDepth - edges; i--) {
                renderAtDepth(node, image, i);
            }
        }

        void renderEdges(BufferedImage image, int edges) {
            renderEdges0(this.root, image, edges, this.findDepth(this.root));
        }

        void render(BufferedImage img) {
            render0(this.root, img, this.maxDepth);
        }
    }

    BufferedImage img;
    float threshold;
    QuadTree qt;
    String format;

    public CompressImage(String input_path, float threshold) throws IOException {
        this.img = ImageIO.read(new File(input_path));
        this.threshold = threshold;
        String[] temp = input_path.split("\\.");
        format = temp[temp.length - 1];
    }

    public void build() {
        qt = new QuadTree(img, this.threshold);
    }

    public void saveCompressed(String outputPath) throws IOException {
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, img.getWidth(), img.getHeight());
        qt.render(img);
        ImageIO.write(img, format, new File(outputPath));

    }

    public void saveEdge(String outputPath, int edges) throws IOException {
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, img.getWidth() % 2 == 0 ? img.getWidth() : img.getWidth() - 1,
                img.getHeight() % 2 == 0 ? img.getHeight() : img.getHeight() - 1);
        qt.renderEdges(img, edges);
        ImageIO.write(img, format, new File(outputPath));
    }
    
}