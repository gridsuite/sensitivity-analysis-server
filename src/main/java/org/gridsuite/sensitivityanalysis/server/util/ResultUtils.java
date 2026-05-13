/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server.util;

import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public final class ResultUtils {

    private ResultUtils() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    private static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    /**
     * Extracts all UUIDs from the given variation ID string based on a predefined format.
     * "[uuid1, uuid2] (REGULAR)" — finds all UUIDs inside brackets.
     *
     * @param varId the variation ID string containing possible UUIDs, must not be null
     * @return a list of UUIDs extracted from the provided variation ID string; if no UUIDs are found, returns an empty list
     */
    public static List<UUID> extractUuidsFromVariationId(@NonNull String varId) {
        List<UUID> uuids = new ArrayList<>();
        Matcher matcher = Pattern.compile(UUID_REGEX, Pattern.CASE_INSENSITIVE).matcher(varId);
        while (matcher.find()) {
            uuids.add(UUID.fromString(matcher.group()));
        }
        return uuids;
    }

    /**
     * Resolves a variation ID string by replacing all UUIDs found within the string with their corresponding names
     * from the provided map. If a UUID has no corresponding name in the map, it is left unchanged in the string.
     *
     * @param varId the variation ID string containing possible UUIDs, must not be null
     * @param nameByUuid a map containing UUID-to-name mappings used to replace UUIDs in the variation ID string; must not be null
     * @return the resolved variation ID string with UUIDs replaced by their corresponding names where applicable
     */
    public static String resolveForVariationId(@NonNull String varId, Map<UUID, String> nameByUuid) {
        return Pattern.compile(UUID_REGEX, Pattern.CASE_INSENSITIVE)
                .matcher(varId)
                .replaceAll(match -> nameByUuid.getOrDefault(UUID.fromString(match.group()), match.group()));
    }

    /**
     * Formats a variation ID string by concatenating a formatted list of UUIDs and a distribution type.
     *
     * @param uuids a list of UUIDs to be included in the variation ID, must not be null
     * @param distributionType the distribution type to append to the variation ID, must not be null
     * @return a string representing the formatted variation ID, which includes the concatenated UUIDs and the distribution type
     */
    public static String formatVariationId(List<UUID> uuids, String distributionType) {
        return joinToStringIds(uuids) + " (" + distributionType + ")";
    }

    /**
     * Joins a list of UUIDs into a single string where each UUID is separated by a comma
     * and the entire string is enclosed in square brackets.
     *
     * @param uuids the list of UUIDs to be joined into a string must not be null
     * @return a string representation of the list of UUIDs, formatted as "[uuid1, uuid2, ...]"
     */
    public static String joinToStringIds(List<UUID> uuids) {
        return "[" + uuids.stream().map(UUID::toString).collect(Collectors.joining(", ")) + "]";
    }
}
