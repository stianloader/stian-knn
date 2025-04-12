package org.stianloader.stianknn;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An improved variant of the original variant of KNN spatial queries implemented by this library.
 * In specific, this class makes use of buffering to avoid cases where object with equal distance
 * get absorbed. At a technical level, this variant retains the core design idea wherein the points
 * are stored in an array sorted by their horizontal position. This allows objects with similar coordinates to be
 * grouped together closer in memory. It also avoid the use of nests or otherwise multiple arrays,
 * which is a property that is commonly used across traditional spatial queries, but which can
 * be inefficient at times.
 *
 * @param <E> The type of elements this container may store. Note that the elements have to be
 * wrapped in a {@link PointObjectPair}, see the constructor's signature for more.
 */
public class SpatialBufferedQueryArray<E> implements SpatialRingIndex1NN<E>, SpatialIndexKNN<E>, SpatialIndexIterable<E> {

    static final class CachedObject<T> implements Comparable<CachedObject<T>> {
        private static final CachedObject<Object> NULL_OBJECT = new CachedObject<>(null, Float.POSITIVE_INFINITY);
        private final PointObjectPair<T> object;
        private final float distanceSq;

        private CachedObject(PointObjectPair<T> o, float distanceSq) {
            this.object = o;
            this.distanceSq = distanceSq;
        }

        @Override
        public int compareTo(CachedObject<T> o) {
            int ret = Float.compare(this.distanceSq, o.distanceSq);
            if (ret != 0) {
                return ret;
            }
            return this.object.compareTo(o.object);
        }
    }

    /**
     * The {@link SpatialBufferedQueryArray#queryKnn(float, float, int, Consumer)} method uses
     * simple array buffering when few elements are to be retrieved, however when more elements
     * need to be retrieved it will fall back to the iterator. This constant determines the threshold.
     *
     * <p>The iterator variant is deemed inefficient due to it's use of a {@link PriorityQueue},
     * however other than that the two algorithms are functionally similar.
     */
    private static final int KNN_ITERATOR_THRESHOLD = 64;

    private final @NotNull PointObjectPair<E>[] points;

    public SpatialBufferedQueryArray(@NotNull Collection<@NotNull PointObjectPair<E>> points) {
        @SuppressWarnings("unchecked")
        PointObjectPair<E>[] pointArray = points.toArray(new @NotNull PointObjectPair[0]);
        this.points = pointArray;
        Arrays.sort(this.points);
    }

    private int binarySearch(int leftAnchor, int rightAnchor, float x) {
        rightAnchor--;

        while (leftAnchor <= rightAnchor) {
            final int center = (leftAnchor + rightAnchor) / 2;
            final int compareResult = Float.compare(this.points[center].x, x);
            if (compareResult < 0) {
                leftAnchor = center + 1;
            } else if (compareResult == 0) {
                return center;
            } else {
                rightAnchor = center - 1;
            }
        }

        return leftAnchor;
    }

    public Iterator<E> createIterator(float x, float y) {

        return new Iterator<E>() {

            /**
             * Elements that already had their distances evaluated (i.e. are between the left
             * and right edge), but have yet to be returned by {@link Iterator#next()} (i.e. because
             * the vertical distance is too great despite of their seemingly low horizontal distance).
             */
            private PriorityQueue<CachedObject<E>> queue = new PriorityQueue<>();

            private int leftEdge;
            private int rightEdge;

            {
                int searchOrigin = SpatialBufferedQueryArray.this.binarySearch(0, SpatialBufferedQueryArray.this.points.length, x);
                this.leftEdge = searchOrigin - 1;
                this.rightEdge = searchOrigin;
            }

            @Override
            public boolean hasNext() {
                return !this.queue.isEmpty() || this.leftEdge >= 0 || this.rightEdge < SpatialBufferedQueryArray.this.points.length;
            }

            @Override
            public E next() {
                CachedObject<E> nearest = this.next0();
                if (nearest == null) {
                    throw new NoSuchElementException(SpatialBufferedQueryArray.this.points.length == 0 ? "No elements to iterate over" : "Iterator has been exhausted");
                }

                this.queue.poll();
                return nearest.object.object;
            }

            private CachedObject<E> next0() {
                CachedObject<E> nearest = this.queue.peek();
                float nearestDistSq = nearest == null ? Float.POSITIVE_INFINITY : nearest.distanceSq;
                PointObjectPair<E>[] points = SpatialBufferedQueryArray.this.points;
                boolean crawlLeft = this.leftEdge >= 0, crawlRight = this.rightEdge < points.length;

                while (crawlLeft || crawlRight) {
                    if (crawlLeft) {
                        PointObjectPair<E> pair = points[this.leftEdge--];
                        if (this.leftEdge < 0) {
                            crawlLeft = false;
                        }
                        float horizontalDistanceSq = (pair.x - x) * (pair.x - x);
                        float objectDistanceSq = horizontalDistanceSq + (pair.y - y) * (pair.y - y);
                        CachedObject<E> object = new CachedObject<E>(pair, objectDistanceSq);
                        this.queue.add(object);
                        if (horizontalDistanceSq >= nearestDistSq) {
                            crawlLeft = false;
                        } else if (objectDistanceSq < nearestDistSq) {
                            nearest = object;
                            nearestDistSq = objectDistanceSq;
                        }
                    }
                    if (crawlRight) {
                        PointObjectPair<E> pair = points[this.rightEdge++];
                        if (this.rightEdge == points.length) {
                            crawlRight = false;
                        }
                        float horizontalDistanceSq = (pair.x - x) * (pair.x - x);
                        float objectDistanceSq = horizontalDistanceSq + (pair.y - y) * (pair.y - y);
                        CachedObject<E> object = new CachedObject<E>(pair, objectDistanceSq);
                        this.queue.add(object);
                        if (horizontalDistanceSq >= nearestDistSq) {
                            crawlRight = false;
                        } else if (objectDistanceSq < nearestDistSq) {
                            nearest = object;
                            nearestDistSq = objectDistanceSq;
                        }
                    }
                }
                return nearest;
            }
        };
    }

    @Nullable
    @Override
    public E query1nn(float x, float y, float minDistSq, float maxDistSq) {
        PointObjectPair<E> pair = this.query1nn0(x, y, minDistSq, maxDistSq);
        return pair == null ? null : pair.object;
    }

    @Nullable
    private PointObjectPair<E> query1nn0(float x, float y, float minDistSq, float maxDistSq) {
        int maxPoints = this.points.length;
        int searchOrigin = this.binarySearch(0, maxPoints, x);

        int leftEdge = searchOrigin - 1;
        int rightEdge = searchOrigin;

        PointObjectPair<E> currentNearest = null;
        while (true) {
            if (leftEdge >= 0) {
                PointObjectPair<E> pair = this.points[leftEdge--];
                float dx = pair.x - x;
                dx *= dx;
                if (dx > maxDistSq) {
                    if (rightEdge >= maxPoints) {
                        break;
                    }
                    leftEdge = -1;
                } else {
                    float dy = pair.y - y;
                    float distSq = dx + dy * dy;
                    if (distSq >= minDistSq && distSq < maxDistSq) {
                        currentNearest = pair;
                        maxDistSq = distSq;
                    }
                }
            }
            if (rightEdge < maxPoints) {
                PointObjectPair<E> pair = this.points[rightEdge++];
                float dx = pair.x - x;
                dx *= dx;
                if (dx > maxDistSq) {
                    if (leftEdge < 0) {
                        break;
                    }
                    rightEdge = maxPoints;
                } else {
                    float dy = pair.y - y;
                    float distSq = dx + dy * dy;
                    if (distSq >= minDistSq && distSq < maxDistSq) {
                        currentNearest = pair;
                        maxDistSq = distSq;
                    }
                }
            } else if (leftEdge < 0) {
                break; // Emergency break
            }
        }

        return currentNearest;
    }

    @Override
    public void queryKnn(float x, float y, int neighbourCount, @NotNull Consumer<@NotNull E> out) {
        neighbourCount = Math.min(neighbourCount, this.points.length);

        if (neighbourCount > SpatialBufferedQueryArray.KNN_ITERATOR_THRESHOLD) {
            Iterator<E> it = this.createIterator(x, y);
            while (neighbourCount-- != 0) {
                out.accept(it.next());
            }
            return;
        }

        @SuppressWarnings("unchecked")
        final CachedObject<E>[] collector = new CachedObject[neighbourCount];
        Arrays.fill(collector, CachedObject.NULL_OBJECT);

        final int searchOrigin = SpatialBufferedQueryArray.this.binarySearch(0, SpatialBufferedQueryArray.this.points.length, x);
        int leftEdge = searchOrigin - 1;
        int rightEdge = searchOrigin;
        final int lastElement = neighbourCount - 1;

        boolean crawlLeft = leftEdge >= 0, crawlRight = rightEdge < this.points.length;

        while (crawlLeft || crawlRight) {
            if (crawlLeft) {
                PointObjectPair<E> pair = this.points[leftEdge--];
                if (leftEdge < 0) {
                    crawlLeft = false;
                }
                float horizontalDistanceSq = (pair.x - x) * (pair.x - x);

                if (horizontalDistanceSq >= collector[lastElement].distanceSq) {
                    crawlLeft = false;
                } else {
                    float objectDistanceSq = horizontalDistanceSq + (pair.y - y) * (pair.y - y);
                    if (objectDistanceSq < collector[lastElement].distanceSq) {
                        int i = lastElement;
                        while (i > 0 && objectDistanceSq < collector[i--].distanceSq);
                        System.arraycopy(collector, i, collector, i + 1, lastElement - i);
                        collector[i] = new CachedObject<E>(pair, objectDistanceSq);
                    }
                }
            }
            if (crawlRight) {
                PointObjectPair<E> pair = this.points[rightEdge++];
                if (rightEdge == this.points.length) {
                    crawlRight = false;
                }
                float horizontalDistanceSq = (pair.x - x) * (pair.x - x);

                if (horizontalDistanceSq >= collector[lastElement].distanceSq) {
                    crawlRight = false;
                } else {
                    float objectDistanceSq = horizontalDistanceSq + (pair.y - y) * (pair.y - y);
                    if (objectDistanceSq < collector[lastElement].distanceSq) {
                        int i = lastElement;
                        while (i > 0 && objectDistanceSq < collector[i--].distanceSq);
                        System.arraycopy(collector, i, collector, i + 1, lastElement - i);
                        collector[i] = new CachedObject<E>(pair, objectDistanceSq);
                    }
                }
            }
        }

        for (CachedObject<E> collected : collector) {
            if (collected.object == null) {
                this.queryKnn(x, y, neighbourCount, out);
            }
            out.accept(collected.object.object);
        }
    }
}
