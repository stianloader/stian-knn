package org.stianloader.stianknn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Code heavily based on <a href="https://gist.github.com/Ret-Mode/98cfe91a655e8f496902071a372e4f6c">a gist made by someone else.</a>
 *
 * @param <E>
 */
public class SpatialQueryArray<@NotNull E> implements SpatialIndexKNN<E> {

    private static class ResultContainer<T> {
        public int found;
        public float maxDist2;
        public List<Results<T>> result;

        ResultContainer(int neighbours) {
            this.result = new ArrayList<>(neighbours);
            this.found = 0;
            this.maxDist2 = Float.POSITIVE_INFINITY;

            for (int i = 0; i < neighbours; i++) {
                this.result.add(new Results<>());
            }
        }

        void addValueFull(float dist2, T obj) {
            Results<T> r = this.result.get(this.found - 1);
            r.distance2 = dist2;
            r.object = obj;

            int prevFound = this.found - 2;
            while (prevFound >= 0) {
                Results<T> prevObj = this.result.get(prevFound);
                if (prevObj.distance2 > r.distance2) {
                    this.result.set(prevFound + 1, prevObj);
                    this.result.set(prevFound, r);
                    prevFound--;
                } else {
                    break;
                }
            }

            this.maxDist2 = this.result.get(this.found - 1).distance2;
        }

        void addValueNotFull(float dist2, T obj) {
            Results<T> r = this.result.get(this.found);
            r.distance2 = dist2;
            r.object = obj;

            int prevFound = this.found - 1;
            while (prevFound >= 0) {
                Results<T> prevObj = this.result.get(prevFound);
                if (prevObj.distance2 > r.distance2) {
                    this.result.set(prevFound + 1, prevObj);
                    this.result.set(prevFound, r);
                    prevFound--;
                } else {
                    break;
                }
            }

            this.maxDist2 = this.result.get(this.found++).distance2;
        }

        void reset(int nearestNeighbours) {
            this.found = 0;
            this.maxDist2 = Float.POSITIVE_INFINITY;

            while (this.result.size() < nearestNeighbours) {
                this.result.add(new Results<>());
            }
        }
    }

    private static class Results<T> {
        public float distance2;
        @Nullable
        public T object;
    }

    private final float cellHeight;
    /**
     * Amount of cells a strip has (or the width of the grid, in cells).
     */
    private final int cellStripSize;
    private final float cellWidth;
    private final float maxX;
    private final float maxY;

    private final float minX;

    private final float minY;

    /**
     * The grid where all the points within the {@link SpatialQueryArray} are located within.
     * The grid is laid out with horizontal strips being kept intact, with y values closer to
     * positive infinity being put more towards to the end of the list, with y values closer to
     * negative infinity tending towards the front of the list.
     */
    private final List<PointObjectPair<E>>[] points;

    private final ResultContainer<E> rc;

    /**
     * Amount of cells in a vertical column within the grid.
     * Note that only rows (called strips as far as this class is concerned) are stored
     * continuously in memory, individual columns will be chopped up in memory, but will
     * be stored with a stride corresponding to the size of the rows, as defined
     * by {@link SpatialQueryArray#cellStripSize}.
     */
    private final int verticalCellCount;

    @SuppressWarnings("unchecked")
    public SpatialQueryArray(Collection<PointObjectPair<E>> points, float minX, float minY, float maxX, float maxY, float cellWidth, float cellHeight) {
        this.maxX = maxX;
        this.maxY = maxY;
        this.minX = minX;
        this.minY = minY;
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.cellStripSize = (int) ((this.maxX - this.minX) / this.cellWidth) + 1;
        this.verticalCellCount = ((int) ((this.maxY - this.minY) / this.cellHeight) + 1);

        this.rc = new ResultContainer<>(40);
        this.points = new List[this.cellStripSize * verticalCellCount];

        for (int i = 0; i < this.points.length; i++) {
            this.points[i] = new ArrayList<>();
        }

        for (PointObjectPair<E> pair : points) {
            int cellX = Math.max(0, (int) ((Math.min(pair.x, this.maxX) - this.minX) / this.cellWidth));
            int cellY = Math.max(0, (int) ((Math.min(pair.y, this.maxY) - this.minY) / this.cellHeight));

            this.points[cellY * this.cellStripSize + cellX].add(pair);
        }
    }

    @Override
    @Nullable
    public E query1nn(float x, float y) {
        AtomicReference<E> ref = new AtomicReference<>();
        this.queryKnn(x, y, 1, ref::set);
        return ref.get();
    }

    @Override
    public void queryKnn(float x, float y, int nearestNeighbours, @NotNull Consumer<@NotNull E> out) {
        final int cellX = Math.max(0, (int) ((Math.min(x, this.maxX) - this.minX) / this.cellWidth));
        final int cellY = Math.max(0, (int) ((Math.min(y, this.maxY) - this.minY) / this.cellHeight));

        this.rc.reset(nearestNeighbours);

        List<PointObjectPair<E>> values = this.points[cellY * this.cellStripSize + cellX];
        for (int i = 0; i < values.size(); ++i) {
            PointObjectPair<E> pop = values.get(i);
            float x2 = x - pop.x;
            float y2 = y - pop.y;
            float dst2 = x2*x2 + y2*y2;

            if (this.rc.found < nearestNeighbours) {
                this.rc.addValueNotFull(dst2, pop.object);
            } else if (dst2 < this.rc.maxDist2) {
                this.rc.addValueFull(dst2, pop.object);
            }
        }

        float cellMinX = cellX * this.cellWidth + this.minX;
        float cellMaxX = cellMinX + this.cellWidth;
        float cellMinY = cellY * this.cellHeight + this.minY;
        float cellMaxY = cellMinY + this.cellHeight;

        float nearestCellBorderX = Math.min(Math.abs(x - cellMinX), Math.abs(x - cellMaxX));
        float nearestCellBorderY = Math.min(Math.abs(x - cellMinY), Math.abs(x - cellMaxY));
        float nearestCellBorderDist = Math.min(nearestCellBorderX, nearestCellBorderY);

        if (this.rc.found != nearestNeighbours || this.rc.maxDist2 > nearestCellBorderDist * nearestCellBorderDist) {

            int cellXLow = cellX;
            int cellXUp = cellX;
            int cellYLow = cellY;
            int cellYUp = cellY;

            while (true) {

                int startX = cellXLow;
                boolean shrinkX = false;
                if (cellXLow > 0) {
                    startX--;
                    shrinkX = true;
                }

                int endX = cellXUp;
                boolean growX = false;
                if (cellXUp < this.cellStripSize - 1) {
                    endX++;
                    growX = true;
                }

                int startY = cellYLow;
                boolean shrinkY = false;
                if (cellYLow > 0) {
                    startY--;
                    shrinkY = true;
                }

                int endY = cellYUp;
                boolean growY = false;
                if (cellYUp < this.verticalCellCount - 1) {
                    endY++;
                    growY = true;
                }

                if (shrinkY) {
                    for (int cx = startX; cx <= endX; cx++) {
                        values = this.points[startY * this.cellStripSize + cx];
                        for (int i = 0; i < values.size(); i++) {
                            PointObjectPair<E> pop = values.get(i);
                            float x2 = x - pop.x;
                            float y2 = y - pop.y;
                            float dst2 = x2 * x2 + y2 * y2;

                            if (this.rc.found < nearestNeighbours) {
                                this.rc.addValueNotFull(dst2, pop.object);
                            } else if (dst2 < this.rc.maxDist2) {
                                this.rc.addValueFull(dst2, pop.object);
                            }
                        }
                    }
                }

                if (growY) {
                    for (int cx = startX; cx <= endX; cx++) {
                        values = this.points[endY * this.cellStripSize + cx];
                        for (int i = 0; i < values.size(); i++) {
                            PointObjectPair<E> pop = values.get(i);
                            float x2 = x - pop.x;
                            float y2 = y - pop.y;
                            float dst2 = x2 * x2 + y2 * y2;

                            if (this.rc.found < nearestNeighbours) {
                                this.rc.addValueNotFull(dst2, pop.object);
                            } else if (dst2 < this.rc.maxDist2) {
                                this.rc.addValueFull(dst2, pop.object);
                            }
                        }
                    }
                }

                if (shrinkX) {
                    for (int cY = cellYLow; cY <= cellYUp; cY++){
                        values = this.points[cY * this.cellStripSize + startX];
                        for (int i = 0; i < values.size(); i++) {
                            PointObjectPair<E> pop = values.get(i);
                            float x2 = x - pop.x;
                            float y2 = y - pop.y;
                            float dst2 = x2 * x2 + y2 * y2;

                            if (this.rc.found < nearestNeighbours) {
                                this.rc.addValueNotFull(dst2, pop.object);
                            } else if (dst2 < this.rc.maxDist2) {
                                this.rc.addValueFull(dst2, pop.object);
                            }
                        }
                    }
                }

                if (growX) {
                    for (int cY = cellYLow; cY <= cellYUp; cY++) {
                        values = this.points[cY * this.cellStripSize + endX];
                        for (int i = 0; i < values.size(); i++) {
                            PointObjectPair<E> pop = values.get(i);
                            float x2 = x - pop.x;
                            float y2 = y - pop.y;
                            float dst2 = x2*x2 + y2*y2;

                            if (this.rc.found < nearestNeighbours) {
                                this.rc.addValueNotFull(dst2, pop.object);
                            } else if (dst2 < this.rc.maxDist2) {
                                this.rc.addValueFull(dst2, pop.object);
                            }
                        }
                    }
                }

                if (growX || shrinkX) {
                    nearestCellBorderX += this.cellWidth;
                }

                if (growY || shrinkY) {
                    nearestCellBorderY += this.cellHeight;
                } else if (!(growX || shrinkX)) {
                    break; // Emergency break
                }

                nearestCellBorderDist = Math.min(nearestCellBorderX, nearestCellBorderY);
                float distSqr = nearestCellBorderDist * nearestCellBorderDist;

                if (this.rc.found == nearestNeighbours && this.rc.maxDist2 < distSqr) {
                    break;
                }

                cellYLow = Math.max(0, cellYLow - 1);
                cellYUp = Math.min(this.verticalCellCount - 1, cellYUp + 1);
                cellXLow = Math.max(0, cellXLow - 1);
                cellXUp = Math.min(this.cellStripSize - 1, cellXUp + 1);
            }
        } 

        for (int i = 0; i < this.rc.found; i++) {
            @Nullable
            E object = this.rc.result.get(i).object;
            assert object != null;
            out.accept(object);
        }
    }
}
