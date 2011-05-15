package de.lmu.ifi.dbs.elki.distance.distancefunction;

import de.lmu.ifi.dbs.elki.data.spatial.SpatialComparable;
import de.lmu.ifi.dbs.elki.database.query.distance.SpatialDistanceQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;

/**
 * API for a spatial primitive distance function.
 * 
 * @author Erich Schubert
 *
 * @param <V> Vector type
 * @param <D> Distance type
 */
public interface SpatialPrimitiveDistanceFunction<V extends SpatialComparable, D extends Distance<D>> extends PrimitiveDistanceFunction<V, D> {
  /**
   * Computes the distance between the two given MBRs according to this
   * distance function.
   * 
   * @param mbr1 the first MBR object
   * @param mbr2 the second MBR object
   * @return the distance between the two given MBRs according to this
   *         distance function
   */
  D minDist(SpatialComparable mbr1, SpatialComparable mbr2);

  /**
   * Computes the distance between the centroids of the two given MBRs
   * according to this distance function.
   * 
   * @param mbr1 the first MBR object
   * @param mbr2 the second MBR object
   * @return the distance between the centroids of the two given MBRs
   *         according to this distance function
   */
  D centerDistance(SpatialComparable mbr1, SpatialComparable mbr2);
  
  @Override
  public <T extends V> SpatialDistanceQuery<T, D> instantiate(Relation<T> relation);
}