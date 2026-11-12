package com.epictalenttrust.hr.routing;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline place lookup. Turns a location written in a CV or typed by HR ("Anna Nagar, Madurai", "Bangalore",
 * "Sivakasi 626123") into a point on the map without sending anything to an outside service.
 *
 * <ul>
 *   <li>resources/geo/places.csv: about 110 Indian and UK cities with their other names</li>
 *   <li>resources/geo/in-pincodes.csv: every Indian PIN code with its post office town, district and state, so
 *       small towns and villages are placed too</li>
 * </ul>
 */
@Component
public class Places {

    public record Place(String name, String region, String country, GeoPoint point) {
    }

    /**
     * A located candidate: the town or city, its district and state, the PIN code if one was given or known, and
     * how it was found ("pin", "city", "town" or "district").
     */
    public record Located(String name, String district, String region, String country, String pin, GeoPoint point,
                          String matchedBy) {

        /** "Sivakasi, Virudhunagar district, Tamil Nadu 626123" style text for screens. */
        public String label() {
            StringBuilder out = new StringBuilder(name);
            if (district != null && !district.equalsIgnoreCase(name)) {
                out.append(", ").append(district);
            }
            if (region != null && !region.equalsIgnoreCase(name) && !region.equalsIgnoreCase(district)) {
                out.append(", ").append(region);
            }
            if (pin != null) {
                out.append(' ').append(pin);
            }
            return out.toString();
        }
    }

    private record Pin(String pin, GeoPoint point, String town, String district, String state, String officeType) {
    }

    private static final Pattern PIN = Pattern.compile("(?<!\\d)([1-9]\\d{2})\\s?(\\d{3})(?!\\d)");

    private final Map<String, Place> byName = new LinkedHashMap<>();
    private final List<String> namesLongestFirst;
    private final Map<String, Pin> pins = new HashMap<>();
    /** Towns with their own head or sub post office whose name is used only once in India. */
    private final Map<String, Pin> towns = new HashMap<>();
    /** District name to its main town (head post office) or, failing that, its middle point. */
    private final Map<String, Pin> districts = new HashMap<>();

    public Places() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("geo/places.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\|", -1);
                Place place = new Place(parts[0].strip(), parts[2].strip(), parts[3].strip(),
                        new GeoPoint(Double.parseDouble(parts[4]), Double.parseDouble(parts[5])));
                byName.putIfAbsent(key(place.name()), place);
                for (String alias : parts[1].split(";")) {
                    if (!alias.isBlank()) {
                        byName.putIfAbsent(key(alias), place);
                    }
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read geo/places.csv", ex);
        }
        namesLongestFirst = byName.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
        loadPinCodes();
    }

    private void loadPinCodes() {
        Map<String, List<Pin>> townCandidates = new HashMap<>();
        Map<String, List<Pin>> byDistrict = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("geo/in-pincodes.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\|", -1);
                GeoPoint point = p[1].isEmpty() ? null : new GeoPoint(Double.parseDouble(p[1]), Double.parseDouble(p[2]));
                Pin pin = new Pin(p[0], point, p[3], p[4], p[5], p[6]);
                pins.put(pin.pin(), pin);
                if (point != null) {
                    byDistrict.computeIfAbsent(key(pin.district()), k -> new ArrayList<>()).add(pin);
                    if (!"BO".equals(pin.officeType())) {
                        townCandidates.computeIfAbsent(key(pin.town()), k -> new ArrayList<>()).add(pin);
                    }
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read geo/in-pincodes.csv", ex);
        }
        byDistrict.forEach((name, list) -> {
            Pin main = list.stream().filter(p -> "HO".equals(p.officeType()) && key(p.town()).equals(name)).findFirst()
                    .or(() -> list.stream().filter(p -> "HO".equals(p.officeType())).findFirst())
                    .orElse(null);
            if (main == null) {
                double lat = list.stream().mapToDouble(p -> p.point().latitude()).sorted().skip(list.size() / 2).findFirst().orElse(0);
                double lng = list.stream().mapToDouble(p -> p.point().longitude()).sorted().skip(list.size() / 2).findFirst().orElse(0);
                Pin any = list.getFirst();
                main = new Pin(null, new GeoPoint(lat, lng), any.district(), any.district(), any.state(), "district");
            }
            if (name.length() >= 4) {
                districts.put(name, main);
            }
        });
        townCandidates.forEach((name, list) -> {
            // A town name used in more than one district (Anna Nagar, Gandhi Nagar) can't be placed by name alone.
            boolean oneDistrict = list.stream().map(p -> key(p.district())).distinct().count() == 1;
            if (oneDistrict && name.length() >= 4) {
                towns.put(name, list.stream().filter(p -> "HO".equals(p.officeType())).findFirst().orElse(list.getFirst()));
            }
        });
    }

    /**
     * Places a candidate's location: a PIN code first (the most precise), then a known city in the text, then a
     * town or district name. Returns empty if nothing in the text can be placed.
     */
    public Optional<Located> locate(String text, String countryHint) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher m = PIN.matcher(text);
        while (m.find()) {
            Pin pin = pins.get(m.group(1) + m.group(2));
            if (pin != null) {
                GeoPoint point = pin.point() != null ? pin.point()
                        : Optional.ofNullable(districts.get(key(pin.district()))).map(Pin::point).orElse(null);
                if (point != null) {
                    return Optional.of(new Located(pin.town(), pin.district(), pin.state(), "India", pin.pin(), point, "pin"));
                }
            }
        }
        Optional<Place> city = find(text, countryHint);
        if (city.isPresent()) {
            Place place = city.get();
            return Optional.of(new Located(place.name(), null, place.region(), place.country(), null, place.point(), "city"));
        }
        if (countryHint != null && !countryHint.isBlank() && !countryHint.toLowerCase(Locale.ROOT).contains("india")) {
            return Optional.empty();
        }
        for (String part : text.split("[,;/\\n]")) {
            String k = key(part.replaceAll("\\d", " "));
            if (k.isEmpty()) {
                continue;
            }
            Pin town = towns.get(k);
            if (town != null) {
                return Optional.of(new Located(town.town(), town.district(), town.state(), "India", null, town.point(), "town"));
            }
            Pin district = districts.get(k.replaceAll(" district$", ""));
            if (district != null) {
                return Optional.of(new Located(district.town(), district.district(), district.state(), "India", null,
                        district.point(), "district"));
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the place a piece of text refers to: an exact name first, then the longest known place name inside
     * the text (so "2nd Floor, Anna Nagar, Madurai 625020" finds Madurai). A country hint breaks ties.
     */
    public Optional<Place> find(String text, String countryHint) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String wanted = key(text);
        Place exact = byName.get(wanted);
        if (exact != null) {
            return Optional.of(exact);
        }
        String padded = " " + wanted + " ";
        List<Place> found = new ArrayList<>();
        for (String name : namesLongestFirst) {
            if (padded.contains(" " + name + " ")) {
                found.add(byName.get(name));
            }
        }
        if (found.isEmpty()) {
            return Optional.empty();
        }
        if (countryHint != null) {
            String country = countryHint.toLowerCase(Locale.ROOT);
            Optional<Place> inCountry = found.stream()
                    .filter(p -> country.contains(p.country().toLowerCase(Locale.ROOT))
                            || ("uk".equals(country) && "United Kingdom".equals(p.country())))
                    .findFirst();
            if (inCountry.isPresent()) {
                return inCountry;
            }
        }
        return Optional.of(found.getFirst());
    }

    static String key(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ").strip();
    }
}
