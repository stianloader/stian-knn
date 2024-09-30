package org.stianloader.stianknn;

import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;

/**
 * A 2D spatial index that is capable of looking up the K nearest elements to a provided point.
 * Note that fewer than K elements may get returned if fewer than K elements exist in the index.
 *
 * <p>Mutability is undefined for this interface, it is best to assume that it is unsupported.
 * The behaviour of elements with equal distance (and thus overlapping points) are undefined
 * by this interface.
 *
 * <p>This interface does not specify the behaviour when K is extremely large. Large values of K
 * may result in overwhelming inefficiencies as well K elements are collected. This is especially
 * critical if only a small fraction of the K elements are actually being read or otherwise used.
 *
 * @param <T> The type of the elements stored in the index.
 */
public interface SpatialIndexKNN<T> extends SpatialIndex1NN<T> {

    void queryKnn(float x, float y, int neighbourCount, @NotNull Consumer<@NotNull T> out);

}
