package org.stianloader.stianknn;

import java.util.Iterator;

import org.jetbrains.annotations.NotNull;

/**
 * A 2D spatial index that is capable of looking up an arbitrary amount of elements based
 * on the distance of a point.
 */
public interface SpatialIndexIterable<E> extends SpatialIndexKNN<E> {

    /**
     * Create an iterator that fetches the element close to the point defined by the parameters <code>x</code>
     * and <code>y</code>. The iterator will return the elements closest to the point first, then return the
     * elements further away from the point.
     *
     * <p>Note that the iterator may be inefficient when fetching large amounts of points, though it concretely
     * depends on the implementation.
     *
     * @param x The x component of the position of the point which will be the origin for the proximity evaluations.
     * @param y The y component of the position of the point which will be the origin for the proximity evaluations.
     * @return An iterator that iterates over the elements based on the proximity from the given point.
     */
    Iterator<@NotNull E> createIterator(float x, float y);
}
