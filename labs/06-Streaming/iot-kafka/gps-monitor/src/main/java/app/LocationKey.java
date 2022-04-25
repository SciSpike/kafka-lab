package app;

import com.github.davidmoten.geo.GeoHash;

public final class LocationKey {
  private final String geoHash;
  private final String objectId;

  public LocationKey(String objectId, double latitude, double longitude) {
    this.objectId = objectId;
    this.geoHash = GeoHash.encodeHash(longitude, latitude, 7);
  }

  public String getGeoHash() {
    return this.geoHash;
  }

  public String getObjectId() {
    return this.objectId;
  }

  @Override
  public int hashCode() {
    return (this.objectId + this.geoHash).hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof LocationKey)) return false;
    if (this == obj) return true;
    LocationKey that = (LocationKey) obj;
    return this.objectId.equals(that.objectId) && this.geoHash.equals(that.getGeoHash());
  }

  @Override
  public String toString() {
    return this.objectId + "@" + this.geoHash;
  }
}
