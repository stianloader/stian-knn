package org.stianloader.stianknn;

import org.jetbrains.annotations.Nullable;

/**
 * A 2D spatial index that is capable of looking up the nearest element within a defined
 * ring around a provided point. That is, it returns the element with the smallest distance
 * larger than a provided minimum distance to a provided point, ensuring that the returned
 * element has no distance larger than a provided maximum distance. These distances
 * may be arbitrarily large or small - that is {@link Float#NEGATIVE_INFINITY} or {@link Float#POSITIVE_INFINITY}
 * are perfectly acceptable and would result in behaviour more comparable to a {@link SpatialIndex1NN}.
 *
 * <p>Mutability is undefined for this interface, it is best to assume that it is unsupported.
 * The behaviour of elements with equal distance (and thus overlapping points) are undefined
 * by this interface.
 *
 * <p>Keep in mind that {@link #query1nn(float, float, float, float)} works with squared distances,
 * this is mainly motivated by performance concerns.
 *
 * @param <T> The type of the elements stored in the index.
 */
public interface SpatialRingIndex1NN<T> extends SpatialIndex1NN<T> {

    @Override
    @Nullable
    default T query1nn(float x, float y) {
        return this.query1nn(x, y, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    @Nullable
    T query1nn(float x, float y, float minDistanceSquared, float maxDistanceSquared);
}
