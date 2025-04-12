package org.stianloader.stianknn;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class TestTest {

    public static void main(String[] args) {
        TestStarGenerator tsg;
        try {
            tsg = new TestStarGenerator();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        tsg.generateStars(100000);

        {
            final int starCount = 50_000;
            TestStarGenerator generator;
            try {
                generator = new TestStarGenerator();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            List<Map.Entry<Float, Float>> stars = generator.generateStars(starCount);
            List<PointObjectPair<Map.Entry<Float, Float>>> points = new ArrayList<>(stars.size());
            for (Map.Entry<Float, Float> star : stars) {
                points.add(new PointObjectPair<>(star, star.getKey(), star.getValue()));
            }
            SpatialIndexKNN<Map.Entry<Float, Float>> query = new SpatialBufferedQueryArray<>(points);

            ThreadLocalRandom random = ThreadLocalRandom.current();
            float width = generator.getMapWidth(starCount);
            float height = generator.getMapHeight(starCount);
            for (int i = 0; i < starCount; i++) {
                float x = random.nextFloat() * width;
                float y = random.nextFloat() * height;
                query.queryKnn(x, y, 40, (star) -> {
                    // NOP
                });
            }
        }
    }

}
