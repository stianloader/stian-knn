package org.stianloader.stianknn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Code heavily based on {@link "https://gist.github.com/Ret-Mode/98cfe91a655e8f496902071a372e4f6c"}
 *
 * @param <E>
 */
public class SpatialQueryArray<E> {
    // constants - probably better parametrize this
    private static final int CELL_SIZE = 4;
    private static final float CELL_MIN_COORD = -120.f;
    private static final float CELL_MAX_COORD =  120.f;
    private static final int CELLS = (int)((CELL_MAX_COORD - CELL_MIN_COORD) / CELL_SIZE);
    private static final int CELLSSQR = CELLS*CELLS;

    public final List<PointObjectPair<E>>[] points;
    private final ResultContainer<E> rc;

    @SuppressWarnings("unchecked")
    public SpatialQueryArray(Collection<PointObjectPair<E>> points) {

        rc = new ResultContainer<E>(40);

        this.points = new ArrayList[CELLSSQR];
        for (int i = 0; i < CELLSSQR; ++i) {
            this.points[i] = new ArrayList<PointObjectPair<E>>();
        }

        for (PointObjectPair<E> pop : points) {

            int cellX = (int)(Math.max(0.f, Math.min((pop.x - CELL_MIN_COORD) / CELL_SIZE, CELLS-1)));
            int cellY = (int)(Math.max(0.f, Math.min((pop.y - CELL_MIN_COORD) / CELL_SIZE, CELLS-1)));

            this.points[cellY*CELLS+cellX].add(pop);
        }

    }

    private static class Results<T> {
        public T object;
        public float distance2;
    }

    private static class ResultContainer<T> {
        public List<Results<T>> result;
        public int found;
        public float maxDist2;

        ResultContainer(int neighbours) {
            result = new ArrayList<Results<T>>();

            for (int i = 0; i < neighbours; ++i) {
                result.add(new Results<>());
            }
            found = 0;
            maxDist2 = Float.MAX_VALUE;
        }

        void reset(int nearestNeighbours) {
            found = 0;
            maxDist2 = Float.MAX_VALUE;
            while (result.size() < nearestNeighbours) {
                result.add(new Results<>());
            }
        }

        void addValueNotFull(float dist2, T obj) {
            Results<T> r = result.get(found);
            r.distance2 = dist2;
            r.object = obj;
            
            int prevFound = found-1;
            while (prevFound >= 0) {
                Results<T> prevObj = result.get(prevFound);
                if (prevObj.distance2 > r.distance2) {
                    result.set(prevFound+1, prevObj);
                    result.set(prevFound, r);
                    prevFound--;
                } else {
                    break;
                }
            }
            maxDist2 = result.get(found).distance2;
            found++;
        }

        void addValueFull(float dist2, T obj) {
            Results<T> r = result.get(found-1);
            r.distance2 = dist2;
            r.object = obj;

            int prevFound = found - 2;
            while (prevFound >= 0) {
                Results<T> prevObj = result.get(prevFound);
                if (prevObj.distance2 > r.distance2) {
                    result.set(prevFound+1, prevObj);
                    result.set(prevFound, r);
                    prevFound--;
                } else {
                    break;
                }
            }
            maxDist2 = result.get(found-1).distance2;
        }
    }

    public void queryKnn(float x, float y, int nearestNeighbours, Consumer<E> out) {
        int cellX = (int)(Math.max(0.f, Math.min((x - CELL_MIN_COORD) / CELL_SIZE, CELLS-1)));
        int cellY = (int)(Math.max(0.f, Math.min((y - CELL_MIN_COORD) / CELL_SIZE, CELLS-1)));

        rc.reset(nearestNeighbours);

        List<PointObjectPair<E>> values = points[cellY*CELLS+cellX];
        for (int i = 0; i < values.size(); ++i) {
            PointObjectPair<E> pop = values.get(i);
            float x2 = x - pop.x;
            float y2 = y - pop.y;
            float dst2 = x2*x2 + y2*y2;

            if (rc.found < nearestNeighbours) {
                rc.addValueNotFull(dst2, pop.object);
            } else if (dst2 < rc.maxDist2) {
                rc.addValueFull(dst2, pop.object);
            }
        }
        
        float dist1XSmp = cellX * CELL_SIZE + CELL_MIN_COORD;
        float dist2XSmp = dist1XSmp + CELL_SIZE;
        float dist1YSmp = cellY * CELL_SIZE + CELL_MIN_COORD;
        float dist2YSmp = dist1YSmp + CELL_SIZE;
        
        float minDistXSmp = Math.min(Math.abs(x - dist1XSmp),Math.abs(x - dist2XSmp));
        float minDistYSmp = Math.min(Math.abs(x - dist1YSmp),Math.abs(x - dist2YSmp));
        float minDstSmp = Math.min(minDistXSmp, minDistYSmp);

        if (rc.found != nearestNeighbours || rc.maxDist2 > minDstSmp*minDstSmp) {

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
                if (cellXUp < CELLS-1) {
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
                if (cellYUp < CELLS-1) {
                    endY++;
                    growY = true;
                }

                if (shrinkY) {
                    for (int cx = startX; cx <= endX; ++cx){
                        values = points[startY*CELLS+cx];
                        for (int i = 0; i < values.size(); ++i) {
                            PointObjectPair<E> pop = values.get(i);
                            float x2 = x - pop.x;
                            float y2 = y - pop.y;
                            float dst2 = x2*x2 + y2*y2;
                
                            if (rc.found < nearestNeighbours) {
                                rc.addValueNotFull(dst2, pop.object);
                            } else if (dst2 < rc.maxDist2) {
                                rc.addValueFull(dst2, pop.object);
                            }
                        }
                    }
                }

                if (growY) {
                    for (int cx = startX; cx <= endX; ++cx){
                        values = points[endY*CELLS+cx];
                        for (int i = 0; i < values.size(); ++i) {
                            PointObjectPair<E> pop = values.get(i);
                            float x2 = x - pop.x;
                            float y2 = y - pop.y;
                            float dst2 = x2*x2 + y2*y2;
                
                            if (rc.found < nearestNeighbours) {
                                rc.addValueNotFull(dst2, pop.object);
                            } else if (dst2 < rc.maxDist2) {
                                rc.addValueFull(dst2, pop.object);
                            }
                        }
                    }
                }

                if (shrinkX) {
                    for (int cY = cellYLow; cY <= cellYUp; ++cY){
                        values = points[cY*CELLS+startX];
                        for (int i = 0; i < values.size(); ++i) {
                            PointObjectPair<E> pop = values.get(i);
                            float x2 = x - pop.x;
                            float y2 = y - pop.y;
                            float dst2 = x2*x2 + y2*y2;
                
                            if (rc.found < nearestNeighbours) {
                                rc.addValueNotFull(dst2, pop.object);
                            } else if (dst2 < rc.maxDist2) {
                                rc.addValueFull(dst2, pop.object);
                            }
                        }
                    }
                }

                if (growX) {
                    for (int cY = cellYLow; cY <= cellYUp; ++cY){
                        values = points[cY*CELLS+endX];
                        for (int i = 0; i < values.size(); ++i) {
                            PointObjectPair<E> pop = values.get(i);
                            float x2 = x - pop.x;
                            float y2 = y - pop.y;
                            float dst2 = x2*x2 + y2*y2;
                
                            if (rc.found < nearestNeighbours) {
                                rc.addValueNotFull(dst2, pop.object);
                            } else if (dst2 < rc.maxDist2) {
                                rc.addValueFull(dst2, pop.object);
                            }
                        }
                    }
                }

                minDstSmp += CELL_SIZE;
                float distSqr = minDstSmp * minDstSmp;
                if (rc.found == nearestNeighbours && rc.maxDist2 < distSqr) {
                    break;
                }
                cellYLow = Math.max(0, cellYLow-1);
                cellYUp = Math.min(CELLS-1, cellYUp+1);
                cellXLow = Math.max(0, cellXLow-1);
                cellXUp = Math.min(CELLS-1, cellXUp+1);
            }
        } 

        for (int i = 0; i < rc.found; ++i) {
            out.accept(rc.result.get(i).object);
        }
    }
}
