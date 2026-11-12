package com.epictalenttrust.hr.candidate;

import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.routing.GeoPoint;
import com.epictalenttrust.hr.routing.Places;
import com.epictalenttrust.hr.routing.Places.Located;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Where each candidate is. The location comes from the CV when the candidate is added; when the CV has none, the AI
 * interviewer asks for it; HR can set or correct it at any time. It is stored on the candidate, placed on the map
 * (PIN code, town, district or city), and used to suggest partner assessment centres in the nearest city.
 */
@Service
public class CandidateLocationService {

    public enum Source { CV, AI_INTERVIEW, HR }

    /**
     * @param text   what the CV, the AI interviewer or HR said, as written
     * @param label  the place it was matched to ("Sivakasi, Virudhunagar, Tamil Nadu 626123"), or null if it
     *               couldn't be placed on the map
     */
    public record Location(String text, String label, String place, String district, String region, String country,
                           String pin, GeoPoint point, Source source, Instant updatedAt) {

        public boolean placed() {
            return point != null;
        }
    }

    private final JdbcClient jdbc;
    private final Places places;
    private final Clock clock;

    public CandidateLocationService(JdbcClient jdbc, Places places, Clock clock) {
        this.jdbc = jdbc;
        this.places = places;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<Location> find(UUID candidateId) {
        return jdbc.sql("""
                        select location_text, location_pin, location_place, location_district, location_region,
                               location_country, location_lat, location_lng, location_source, location_updated_at
                          from candidates where id = :id and location_text is not null
                        """)
                .param("id", candidateId)
                .query((rs, n) -> {
                    Double lat = (Double) rs.getObject("location_lat");
                    Double lng = (Double) rs.getObject("location_lng");
                    String place = rs.getString("location_place");
                    String district = rs.getString("location_district");
                    String region = rs.getString("location_region");
                    String pin = rs.getString("location_pin");
                    String label = place == null ? null
                            : new Located(place, district, region, rs.getString("location_country"), pin, null, null).label();
                    return new Location(rs.getString("location_text"), label, place, district, region,
                            rs.getString("location_country"), pin, lat == null || lng == null ? null : new GeoPoint(lat, lng),
                            Source.valueOf(rs.getString("location_source").toUpperCase(java.util.Locale.ROOT)),
                            Rows.instant(rs, "location_updated_at"));
                })
                .optional();
    }

    /**
     * After a CV is read: takes the "Current location" line, then the address at the top, then the first preferred
     * location. Doesn't overwrite a location given in the AI interview or set by HR.
     */
    @Transactional
    public void fromCv(UUID candidateId, UUID applicationId) {
        String source = jdbc.sql("select location_source from candidates where id = :id").param("id", candidateId)
                .query(String.class).optional().orElse(null);
        if (source != null && !"cv".equals(source)) {
            return;
        }
        record Cv(String current, String city, String country, List<String> preferred) {
        }
        Cv cv = jdbc.sql("""
                        select k.current_location, c.city, c.country, k.preferred_locations
                          from candidates c left join application_key_facts k on k.application_id = :a
                         where c.id = :id
                        """)
                .param("id", candidateId).param("a", applicationId)
                .query((rs, n) -> {
                    java.sql.Array array = rs.getArray("preferred_locations");
                    List<String> preferred = array == null ? List.of()
                            : Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList();
                    return new Cv(blank(rs.getString("current_location")), blank(rs.getString("city")),
                            rs.getString("country"), preferred);
                })
                .optional().orElse(null);
        if (cv == null) {
            return;
        }
        List<String> tries = new ArrayList<>();
        if (cv.current() != null) tries.add(cv.current());
        if (cv.city() != null) tries.add(cv.city());
        cv.preferred().stream().map(CandidateLocationService::blank).filter(java.util.Objects::nonNull).findFirst()
                .ifPresent(tries::add);
        if (tries.isEmpty()) {
            return;
        }
        for (String text : tries) {
            Optional<Located> found = places.locate(text, cv.country());
            if (found.isPresent()) {
                save(candidateId, text, found.get(), Source.CV);
                return;
            }
        }
        // Something was written but couldn't be placed: keep it so HR can see and correct it.
        save(candidateId, tries.getFirst(), null, Source.CV);
    }

    /**
     * The AI interviewer asked the candidate where they are. Fills in the location when the CV didn't give one (or
     * gave one that couldn't be placed); a location HR set is kept.
     */
    @Transactional
    public void fromAiInterview(UUID candidateId, String text, String pin) {
        String said = blank(String.join(" ", blank(text) == null ? "" : text, blank(pin) == null ? "" : pin));
        if (said == null) {
            return;
        }
        Optional<Location> current = find(candidateId);
        if (current.isPresent() && (current.get().source() == Source.HR
                || (current.get().source() == Source.CV && current.get().placed()))) {
            return;
        }
        save(candidateId, said, places.locate(said, null).orElse(null), Source.AI_INTERVIEW);
    }

    /** HR sets or corrects the location. It must be somewhere the portal can place on the map. */
    @Transactional
    public Location setByHr(UUID candidateId, String text) {
        String value = blank(text);
        if (value == null) {
            throw ApiException.badRequest("location_required", "Type the candidate's town or city, or a PIN code.");
        }
        if (value.length() > 200) {
            throw ApiException.badRequest("location_too_long", "Keep the location under 200 characters.");
        }
        Located found = places.locate(value, null).orElseThrow(() -> ApiException.badRequest("location_not_found",
                "We couldn't find \"" + value + "\" on the map. Add the PIN code (for example 626123) or the nearest town."));
        save(candidateId, value, found, Source.HR);
        return find(candidateId).orElseThrow();
    }

    /** HR changed "Current location" in the key facts: use it as the location if it can be placed. */
    @Transactional
    public void fromKeyFacts(UUID candidateId, String text) {
        String value = blank(text);
        if (value == null) {
            return;
        }
        Optional<Location> current = find(candidateId);
        if (current.isPresent() && value.equalsIgnoreCase(current.get().text())) {
            return;
        }
        places.locate(value, null).ifPresent(found -> save(candidateId, value, found, Source.HR));
    }

    private void save(UUID candidateId, String text, Located found, Source source) {
        jdbc.sql("""
                        update candidates set location_text = :text, location_pin = :pin, location_place = :place,
                               location_district = :district, location_region = :region, location_country = :country,
                               location_lat = :lat, location_lng = :lng, location_source = :source,
                               location_updated_at = :at
                         where id = :id
                        """)
                .param("text", text.strip())
                .param("pin", found == null ? null : found.pin())
                .param("place", found == null ? null : found.name())
                .param("district", found == null ? null : found.district())
                .param("region", found == null ? null : found.region())
                .param("country", found == null ? null : found.country())
                .param("lat", found == null ? null : found.point().latitude(), java.sql.Types.DOUBLE)
                .param("lng", found == null ? null : found.point().longitude(), java.sql.Types.DOUBLE)
                .param("source", source.name().toLowerCase(java.util.Locale.ROOT))
                .param("at", Timestamp.from(clock.instant()))
                .param("id", candidateId)
                .update();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
