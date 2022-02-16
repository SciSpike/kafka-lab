package com.example;

import com.github.davidmoten.geo.GeoHash;

final public class LocationKey {
	private String geoHash;
	private String objectId;

	public LocationKey(String id, double latitude, double longitude) {
		System.out.println("Calculating geohash for " + id + "(" + latitude + "," + longitude + ")");
		this.objectId = id;
		try {
			this.geoHash = GeoHash.encodeHash(longitude, latitude, 7);
		}
		catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	final public String getGeoHash() {
		return this.geoHash;
	}

	final public String getId() {
		return this.objectId;
	}

	@Override
	public int hashCode() {
		return (this.objectId + this.geoHash).hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof LocationKey))
			return false;
		if (this == obj)
			return true;
		LocationKey other = (LocationKey) obj;
		return this.objectId.equals(other.objectId) && this.geoHash.equals(other.getGeoHash());
	}
	@Override
	public String toString() {
		return this.objectId + "@" + this.geoHash;
	}
}
