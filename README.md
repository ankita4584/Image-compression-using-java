# Quadtree-Based Image Compression

Adaptive image compression using a quadtree — recursively partitions an image into homogeneous regions, each represented by a single averaged color. Unlike many quadtree implementations that just redraw a simplified image (no real size reduction), this project includes actual binary serialization, quantitative quality evaluation, and a content-aware enhancement.

# Features
- Recursive quadtree partitioning based on color homogeneity
- RMS color averaging + depth-scaled adaptive threshold
- Real binary compression — bit-packed tree serialization (`.qtc` format)
- PSNR quality evaluation
- Edge-aware compression — Sobel edge detection preserves detail at visual boundaries
- Thin Spring Boot REST API (`/compress`, `/decompress`)

# How it works
```
Image → Build Quadtree (split if color error > threshold) → 
   ├─ Render (visual output)
   └─ Serialize (bit-packed binary, real compression)
         → Deserialize → Render → Evaluate (ratio, PSNR)
```

Why RMS averaging? `sqrt(mean(pixel²))` weights brighter pixels more than a plain mean, closer to perceived intensity.

Why threshold scales with depth? `error < threshold × (depth+1)` — smaller regions get more tolerance, preventing the tree from over-splitting to chase negligible noise.

Why is this real compression? Tree is serialized as 1 bit/node (leaf or internal) + 3 bytes/leaf color, in preorder. Positions are never stored — the decoder recomputes them using the same splitting rule, so no redundant data is saved.

# Binary format (`.qtc`)
| Field | Size |
|---|---|
| Width, Height | 2 bytes each |
| Threshold | 4 bytes (float) |
| Version | 1 byte |
| Bit count | 4 bytes |
| Structure bits | `ceil(bitCount/8)` bytes |
| Color stream | 3 bytes × leaf count |

# Results (256×256 test image)
| Metric | Value |
|---|---|
| Raw size | 196,608 bytes |
| Compressed | 103,111 bytes |
| Ratio | 52.44% |
| PSNR | 26.08 dB |

# Edge-aware compression
Adds a Sobel edge-detection pass; regions overlapping a real edge get a lower splitting threshold, forcing extra detail preservation there — similar in spirit to Region-of-Interest coding in codecs like JPEG 2000, but automatic and lightweight.

# API
```
POST /api/compress   (image, threshold) → {id, ratio, psnr, ...}
GET  /api/decompress/{id}   → reconstructed image
```

# Tech stack
Java, Java AWT, Spring Boot

# Known limitations
- Odd dimensions lose a 1-pixel row/column at split boundaries
- Compresses poorly on high-noise/random images
- Edge weight needs manual tuning

