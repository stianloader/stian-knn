package org.stianloader.stianknn;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

public class Benchmarks {

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @Warmup(iterations = 1)
    public void benchmarkSQAL40nn(Blackhole bh) {
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
        SpatialQueryArrayLegacy<Map.Entry<Float, Float>> query = new SpatialQueryArrayLegacy<>(points);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        float width = generator.getMapWidth(starCount);
        float height = generator.getMapHeight(starCount);
        for (int i = 0; i < starCount; i++) {
            float x = random.nextFloat() * width;
            float y = random.nextFloat() * height;
            query.queryKnn(x, y, 40, bh::consume);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @Warmup(iterations = 1)
    public void benchmarkSQAL1nn50(Blackhole bh) {
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
        SpatialQueryArrayLegacy<Map.Entry<Float, Float>> query = new SpatialQueryArrayLegacy<>(points);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        float width = generator.getMapWidth(starCount);
        float height = generator.getMapHeight(starCount);
        for (int i = 0; i < starCount * 50; i++) {
            float x = random.nextFloat() * width;
            float y = random.nextFloat() * height;
            bh.consume(query.query1nn(x, y, 0, Float.MAX_VALUE));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @Warmup(iterations = 1)
    public void benchmarkSQAG40nn(Blackhole bh) {
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
        SpatialQueryArray<Map.Entry<Float, Float>> query = new SpatialQueryArray<>(points);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        float width = generator.getMapWidth(starCount);
        float height = generator.getMapHeight(starCount);
        for (int i = 0; i < starCount; i++) {
            float x = random.nextFloat() * width;
            float y = random.nextFloat() * height;
            query.queryKnn(x, y, 40, bh::consume);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @Warmup(iterations = 1)
    public void benchmarkSQAG1nn50(Blackhole bh) {
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
        SpatialQueryArray<Map.Entry<Float, Float>> query = new SpatialQueryArray<>(points);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        float width = generator.getMapWidth(starCount);
        float height = generator.getMapHeight(starCount);
        for (int i = 0; i < starCount * 50; i++) {
            float x = random.nextFloat() * width;
            float y = random.nextFloat() * height;
            query.queryKnn(x, y, 1, bh::consume);
        }
    }
}
