package org.stianloader.stianknn;

import org.jetbrains.annotations.NotNull;

public class PointObjectPair<T> implements Comparable<PointObjectPair<?>> {
    @NotNull
    public final T object;
    public final float x;
    public final float y;

    public PointObjectPair(@NotNull T object, float x, float y) {
        this.object = object;
        this.x = x;
        this.y = y;
    }

    @Override
    public int compareTo(PointObjectPair<?> o) {
        return Float.compare(this.x, o.x);
    }
}
