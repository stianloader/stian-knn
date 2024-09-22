package org.stianloader.stianknn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Code heavily based on {@link "https://gist.github.com/Ret-Mode/98cfe91a655e8f496902071a372e4f6c"}
 *
 * @param <E>
 */
public class SpatialQueryArray<@NotNull E> {
    // constants - probably better parametrize this
    private static final int CELL_SIZE = 4;
    private static final float CELL_MIN_COORD = -120.f;
    private static final float CELL_MAX_COORD =  120.f;
    private static final int CELLS = (int) ((CELL_MAX_COORD - CELL_MIN_COORD) / CELL_SIZE);
    private static final int CELLSSQR = CELLS * CELLS;

    public final List<PointObjectPair<E>>[] points;
    private final ResultContainer<E> rc;

    @SuppressWarnings("unchecked")
    public SpatialQueryArray(Collection<PointObjectPair<E>> points) {

        this.rc = new ResultContainer<>(40);
        this.points = new ArrayList[SpatialQueryArray.CELLSSQR];

        for (int i = 0; i < SpatialQueryArray.CELLSSQR; i++) {
            this.points[i] = new ArrayList<>();
        }

        for (PointObjectPair<E> pair : points) {
            int cellX = (int)(Math.max(0, Math.min((pair.x - SpatialQueryArray.CELL_MIN_COORD) / SpatialQueryArray.CELL_SIZE, SpatialQueryArray.CELLS - 1)));
            int cellY = (int)(Math.max(0, Math.min((pair.y - SpatialQueryArray.CELL_MIN_COORD) / SpatialQueryArray.CELL_SIZE, SpatialQueryArray.CELLS - 1)));

            this.points[cellY * SpatialQueryArray.CELLS + cellX].add(pair);
        }
    }

    private static class Results<T> {
        @Nullable
        public T object;
        public float distance2;
    }

    private static class ResultContainer<T> {
        public List<Results<T>> result;
        public int found;
        public float maxDist2;

        ResultContainer(int neighbours) {
            this.result = new ArrayList<>(neighbours);
            this.found = 0;
            this.maxDist2 = Float.MAX_VALUE;

            for (int i = 0; i < neighbours; i++) {
                this.result.add(new Results<>());
            }
        }

        void reset(int nearestNeighbours) {
            this.found = 0;
            this.maxDist2 = Float.MAX_VALUE;

            while (this.result.size() < nearestNeighbours) {
                this.result.add(new Results<>());
            }
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
    }

    public void queryKnn(float x, float y, int nearestNeighbours, Consumer<E> out) {
        int cellX = (int)(Math.max(0, Math.min((x - SpatialQueryArray.CELL_MIN_COORD) / SpatialQueryArray.CELL_SIZE, SpatialQueryArray.CELLS - 1)));
        int cellY = (int)(Math.max(0, Math.min((y - SpatialQueryArray.CELL_MIN_COORD) / SpatialQueryArray.CELL_SIZE, SpatialQueryArray.CELLS - 1)));

        this.rc.reset(nearestNeighbours);

        List<PointObjectPair<E>> values = this.points[cellY * SpatialQueryArray.CELLS + cellX];
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

        float dist1XSmp = cellX * SpatialQueryArray.CELL_SIZE + SpatialQueryArray.CELL_MIN_COORD;
        float dist2XSmp = dist1XSmp + SpatialQueryArray.CELL_SIZE;
        float dist1YSmp = cellY * SpatialQueryArray.CELL_SIZE + SpatialQueryArray.CELL_MIN_COORD;
        float dist2YSmp = dist1YSmp + SpatialQueryArray.CELL_SIZE;

        float minDistXSmp = Math.min(Math.abs(x - dist1XSmp), Math.abs(x - dist2XSmp));
        float minDistYSmp = Math.min(Math.abs(x - dist1YSmp), Math.abs(x - dist2YSmp));
        float minDstSmp = Math.min(minDistXSmp, minDistYSmp);

        if (this.rc.found != nearestNeighbours || this.rc.maxDist2 > minDstSmp * minDstSmp) {

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
                if (cellXUp < SpatialQueryArray.CELLS - 1) {
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
                if (cellYUp < SpatialQueryArray.CELLS - 1) {
                    endY++;
                    growY = true;
                }

                if (shrinkY) {
                    for (int cx = startX; cx <= endX; cx++) {
                        values = this.points[startY * SpatialQueryArray.CELLS + cx];
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
                        values = this.points[endY * SpatialQueryArray.CELLS + cx];
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
                        values = points[cY * SpatialQueryArray.CELLS + startX];
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
                        values = this.points[cY * SpatialQueryArray.CELLS + endX];
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

                minDstSmp += SpatialQueryArray.CELL_SIZE;
                float distSqr = minDstSmp * minDstSmp;

                if (this.rc.found == nearestNeighbours && this.rc.maxDist2 < distSqr) {
                    break;
                }

                cellYLow = Math.max(0, cellYLow - 1);
                cellYUp = Math.min(SpatialQueryArray.CELLS - 1, cellYUp + 1);
                cellXLow = Math.max(0, cellXLow - 1);
                cellXUp = Math.min(SpatialQueryArray.CELLS - 1, cellXUp + 1);
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
