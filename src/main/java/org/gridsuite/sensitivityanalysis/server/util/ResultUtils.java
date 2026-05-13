/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server.util;

import lombok.NonNull;

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

    public static List<UUID> extractUuidsFromVariationId(@NonNull String varId) {
        List<UUID> uuids = new ArrayList<>();
        Matcher matcher = Pattern.compile(UUID_REGEX, Pattern.CASE_INSENSITIVE).matcher(varId);
        while (matcher.find()) {
            uuids.add(UUID.fromString(matcher.group()));
        }
        return uuids;
    }

    public static String resolveForVariationId(@NonNull String varId, Map<UUID, String> nameByUuid) {
        return Pattern.compile(UUID_REGEX, Pattern.CASE_INSENSITIVE)
                .matcher(varId)
                .replaceAll(match -> nameByUuid.getOrDefault(UUID.fromString(match.group()), match.group()));
    }

    public static String formatVariationId(List<UUID> uuids, String distributionType) {
        return joinToStringIds(uuids) + " (" + distributionType + ")";
    }

    public static String joinToStringIds(List<UUID> uuids) {
        return "[" + uuids.stream().map(UUID::toString).collect(Collectors.joining(", ")) + "]";
    }
}
