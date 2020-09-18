package net.langenmaier.strohstern.data.storage;

import java.math.BigDecimal;
import java.math.BigInteger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TargetService {

	private static final Double MIN_FOUND_DISTANCE = 10d;

	@Inject
	public EntityManager em;

	@ConfigProperty(name = "airrow.debug")
	Boolean debug;

	public Target findTarget(Session s) {
		Target t = null;
		try {
			String NEAREST_POINT_SQL = new StringBuilder()
				.append("SELECT ")
					.append("get_distance_in_meters_between_geo_locations(start.latitude, start.longitude, target.latitude, target.longitude) AS geo_distance,")
					.append("emoji_distance(start.status, target.status, 2) AS emoji_distance,")
					.append("target.id AS targetId, ")
					.append("target.latitude AS targetLatitude, ")
					.append("target.longitude AS targetLongitude, ")
					.append("target.status AS targetStatus")
				.append(" FROM Session AS start")
				.append(" INNER JOIN Session AS target ")
					.append("ON start.id != target.id ")
					.append("AND start.uuid = ?1 ")
					.append("AND target.updatedAt > (NOW() - INTERVAL 30 SECOND)")
				.append("ORDER BY (geo_distance*emoji_distance) ")
				.append("LIMIT 1;")
				.toString();
			Object o = em.createNativeQuery(NEAREST_POINT_SQL).setParameter(1, s.uuid).getSingleResult();
			if (o instanceof Object[]) {
				t = new Target();
				Object[] of = (Object[]) o;

				try {
					t.geo_distance = ((BigDecimal) of[0]).doubleValue();
					t.emoji_distance = (Double) of[1];
					t.id = ((BigInteger) of[2]).longValue();
					t.latitude = (Double) of[3];
					t.longitude = (Double) of[4];
					t.status = (String) of[5];
				} catch (NullPointerException npe) {
					npe.printStackTrace();
					System.out.println(of);
					return null;
				}
			}
		} catch (NoResultException e) {
			return null;
		}
		return t;
	}

	// http://www.movable-type.co.uk/scripts/latlong.html
	public Direction getDirection(Session s, Target t) {
		if (t == null) return null;

		double latStart = Math.toRadians(s.latitude);
		double longStart = Math.toRadians(s.longitude);
		double latEnd = Math.toRadians(t.latitude);
		double longEnd = Math.toRadians(t.longitude);

		Direction d = new Direction(GeoTools.getWebAngle(Math.toDegrees(GeoTools.getBearing(latStart, longStart, latEnd, longEnd))), t.status);
		if (t.geo_distance < MIN_FOUND_DISTANCE) {
			d.searchState = SearchState.FOUND;
		}
		if (debug) {
			d.target = t;
		}

		return d;
	}
}
