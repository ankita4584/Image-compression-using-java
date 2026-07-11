import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class CompressImage {
    class QuadTree {//buildtree class with only buildtree function only build if error is less 
        // so we find maximum depth 
        class QuadTreeNode {
            int x, y, w, h;
            int[] averageCbCr = new int[2];
            double error;
            Color averageColor;
            int atDepth;
            QuadTreeNode tl, tr, bl, br;
            boolean isLeaf = false;

            public QuadTreeNode(BufferedImage image, int x, int y, int w, int h, int atDepth) {
                // FIX: fields must be set BEFORE any early return, or degenerate
                // (1-pixel-wide) regions end up with averageColor=null, w=0, h=0 --
                // this was silently harmless for render() (fillRect of size 0 draws
                // nothing) but breaks serialize(), which reads every leaf's color.
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
                this.atDepth = atDepth;

                if (w <= 1 || h <= 1) {
                    this.averageColor = new Color(image.getRGB(x, y));
                    this.error = 0;
                    return;
                }

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

            // NEW: a second constructor used only during DESERIALIZATION.
            // It does NOT scan any image (there is none at decode time) -- it just
            // directly sets the fields from what was stored in the compressed file.
            public QuadTreeNode(int x, int y, int w, int h, int atDepth, Color color, boolean isLeaf) {
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
                this.atDepth = atDepth;
                this.averageColor = color;
                this.isLeaf = isLeaf;
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

        // NEW: private no-arg-build constructor used when reconstructing from a file.
        // Skips buildTree entirely since the tree shape is being restored, not computed.
        private QuadTree() {
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

        // =========================================================
        // NEW: SERIALIZATION
        // Walks the tree in preorder (root, tl, tr, bl, br).
        // Writes 1 bit per node: 1 = leaf, 0 = internal (has 4 children).
        // For leaves only, writes the average color as 3 bytes (R,G,B).
        // Positions/sizes are NEVER stored -- the decoder recomputes them
        // using splitDimension, exactly like split() does when building.
        // =========================================================

        void serialize(BufferedImage img, String outputPath) throws IOException {
            List<Boolean> structureBits = new ArrayList<>();
            List<Color> leafColors = new ArrayList<>();
            collectPreorder(root, structureBits, leafColors);

            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(outputPath)))) {

                // Header
                out.writeShort(img.getWidth());
                out.writeShort(img.getHeight());
                out.writeFloat(this.threshold);
                out.writeByte(1); // format version

                // Structure stream (bit-packed)
                byte[] packed = packBits(structureBits);
                out.writeInt(structureBits.size());
                out.write(packed);

                // Color stream (3 bytes per leaf, same order as structure bits)
                for (Color c : leafColors) {
                    out.writeByte(c.getRed());
                    out.writeByte(c.getGreen());
                    out.writeByte(c.getBlue());
                }
            }
        }

        private void collectPreorder(QuadTreeNode node, List<Boolean> bits, List<Color> colors) {
            if (node.isLeaf) {
                bits.add(true);
                colors.add(node.averageColor);
            } else {
                bits.add(false);
                collectPreorder(node.tl, bits, colors);
                collectPreorder(node.tr, bits, colors);
                collectPreorder(node.bl, bits, colors);
                collectPreorder(node.br, bits, colors);
            }
        }

        private byte[] packBits(List<Boolean> bits) {
            int numBytes = (bits.size() + 7) / 8;
            byte[] packed = new byte[numBytes];
            for (int i = 0; i < bits.size(); i++) {
                if (bits.get(i)) {
                    packed[i / 8] |= (1 << (7 - (i % 8)));
                }
            }
            return packed;
        }

        // =========================================================
        // NEW: DESERIALIZATION
        // Reads the bits/colors back and rebuilds the SAME tree shape,
        // using the second QuadTreeNode constructor (no image scanning).
        // Returns a fully usable QuadTree -- render()/renderEdges() work
        // on it exactly the same as on a freshly-built one.
        // =========================================================

        // NOTE: this is an INSTANCE method, not static -- QuadTree is a non-static
        // inner class, so it needs an enclosing CompressImage instance to exist at all.
        // Call it on a QuadTree created via `ci.new QuadTree()` (see CompressImage.loadBinary).
        void deserializeInto(String inputPath, int[] outWidth, int[] outHeight) throws IOException {
            try (DataInputStream in = new DataInputStream(
                    new BufferedInputStream(new FileInputStream(inputPath)))) {

                int width = in.readUnsignedShort();
                int height = in.readUnsignedShort();
                float threshold = in.readFloat();
                byte version = in.readByte();

                int bitCount = in.readInt();
                int byteCount = (bitCount + 7) / 8;
                byte[] bitData = new byte[byteCount];
                in.readFully(bitData);

                byte[] remainingColorBytes = in.readAllBytes();
                DataInputStream colorStream =
                        new DataInputStream(new ByteArrayInputStream(remainingColorBytes));

                this.threshold = threshold;
                int[] bitPos = {0};
                this.root = decodeNode(bitData, bitPos, colorStream, 0, 0, width, height, 0);
                this.maxDepth = findDepth(this.root);

                outWidth[0] = width;
                outHeight[0] = height;
            }
        }

        private QuadTreeNode decodeNode(byte[] bitData, int[] bitPos, DataInputStream colorStream,
                                         int x, int y, int w, int h, int atDepth) throws IOException {
            boolean isLeafNode = readBit(bitData, bitPos);

            if (isLeafNode) {
                int r = colorStream.readUnsignedByte();
                int g = colorStream.readUnsignedByte();
                int b = colorStream.readUnsignedByte();
                Color color = new Color(r, g, b);
                QuadTreeNode node = new QuadTreeNode(x, y, w, h, atDepth, color, true);
                leafNodes++;
                return node;
            } else {
                QuadTreeNode node = new QuadTreeNode(x, y, w, h, atDepth, null, false);
                // Must use the SAME splitDimension rounding as split(), or reconstruction
                // will misalign on odd width/height images.
                int hw = node.splitDimension(w);
                int hh = node.splitDimension(h);
                node.tl = decodeNode(bitData, bitPos, colorStream, x, y, hw, hh, atDepth + 1);
                node.tr = decodeNode(bitData, bitPos, colorStream, x + hw, y, hw, hh, atDepth + 1);
                node.bl = decodeNode(bitData, bitPos, colorStream, x, y + hh, hw, hh, atDepth + 1);
                node.br = decodeNode(bitData, bitPos, colorStream, x + hw, y + hh, hw, hh, atDepth + 1);
                return node;
            }
        }

        private boolean readBit(byte[] bitData, int[] bitPos) {
            boolean bit = (bitData[bitPos[0] / 8] & (1 << (7 - (bitPos[0] % 8)))) != 0;
            bitPos[0]++;
            return bit;
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

    // NEW: private constructor used only when loading from a compressed .qtc file
    // (no original image path/format needed in that case)
    private CompressImage() {
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

    // =========================================================
    // NEW: real compression entry points
    // =========================================================

    /** Serializes the built tree to a compact binary .qtc file (real compression, not a redrawn image). */
    public void saveBinary(String outputPath) throws IOException {
        qt.serialize(img, outputPath);
    }

    /** Loads a .qtc file back and rebuilds a fully working CompressImage + QuadTree from it. */
    public static CompressImage loadBinary(String inputPath, String outputFormat) throws IOException {
        CompressImage ci = new CompressImage();
        ci.format = outputFormat;

        ci.qt = ci.new QuadTree(); // empty QuadTree, owned by this CompressImage instance
        int[] w = new int[1], h = new int[1];
        ci.qt.deserializeInto(inputPath, w, h);

        ci.img = new BufferedImage(w[0], h[0], BufferedImage.TYPE_INT_RGB);
        return ci;
    }
}