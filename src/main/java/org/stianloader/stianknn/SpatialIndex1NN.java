package org.stianloader.stianknn;

import org.jetbrains.annotations.Nullable;

/**
 * A 2D spatial index that is capable of looking up the nearest element to a provided point.
 *
 * <p>Mutability is undefined for this interface, it is best to assume that it is unsupported.
 * The behaviour of elements with equal distance (and thus overlapping points) are undefined
 * by this interface.
 *
 * @param <T> The type of the elements stored in the index.
 */
public interface SpatialIndex1NN<T> {

    @Nullable
    T query1nn(float x, float y);

}
