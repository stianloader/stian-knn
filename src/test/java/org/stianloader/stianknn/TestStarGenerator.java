package org.stianloader.stianknn;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.ImageIO;

public class TestStarGenerator {

    private static final float GRANULARITY_FACTOR = 0.035F;
    private static final double GRANULARITY_FACTOR_4SQ = 16D * GRANULARITY_FACTOR * GRANULARITY_FACTOR;

    private final float[] floatmap;
    private final int floatmapWidth;
    private final int floatmapHeight;
    private final float density;

    public TestStarGenerator() throws IOException {
        try (InputStream is = TestStarGenerator.class.getResourceAsStream("/beloia.png")) {
            BufferedImage image = ImageIO.read(is);
            Raster valueRaster = image.getRaster();
            this.floatmapWidth = valueRaster.getWidth();
            this.floatmapHeight = valueRaster.getHeight();
            float[] fullMap = new float[this.floatmapHeight * this.floatmapWidth * 4];
            if (valueRaster.getPixels(0, 0, this.floatmapWidth, this.floatmapHeight, fullMap) != fullMap) {
                throw new AssertionError("#getPixels did not return preallocated array");
            }
            float density = 0F;
            this.floatmap = new float[this.floatmapWidth * this.floatmapHeight];
            Arrays.fill(this.floatmap, 1F);
            for (int i = 0; i < fullMap.length; i++) {
                this.floatmap[i >> 2] *= fullMap[i] / 255F; 
            }
            for (float floatmap : this.floatmap) {
                density += floatmap;
            }
            if (density == 0F) {
                throw new IllegalStateException("Total density of 0. Black texture?");
            }
            this.density = density;
        }
    }

    public List<Map.Entry<Float, Float>> generateStars(int count) {
        List<Map.Entry<Float, Float>> out = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float width = this.getMapWidth(count);
        float height = this.getMapHeight(count);
        while (count-- != 0) {
            while (true) {
                float rx = random.nextFloat();
                float ry = random.nextFloat();
                int pxX = (int) (rx * this.floatmapWidth);
                int pxY = (int) (ry * this.floatmapHeight);
                float density = this.floatmap[pxY * this.floatmapWidth + pxX];
                if (random.nextFloat() < density) {
                    continue;
                }
                out.add(new AbstractMap.SimpleImmutableEntry<>(rx * width, ry * height));
                break;
            }
        }

        return out;
    }

    public float getMapWidth(int count) {
        return this.getMapHeight(count) * (((float) this.floatmapHeight) / this.floatmapHeight);
    }

    public float getMapHeight(int count) {
        return (float) Math.sqrt(count * TestStarGenerator.GRANULARITY_FACTOR_4SQ / (this.getAverageDensity() * 1.77F));
    }

    public float getAverageDensity() {
        return this.density / (this.floatmapHeight * this.floatmapWidth);
    }
}
