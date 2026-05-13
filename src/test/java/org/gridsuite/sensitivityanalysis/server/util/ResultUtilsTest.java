/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
class ResultUtilsTest {

    // --- extractUuidsFromVariationId ---

    @Test
    void extractUuidsFromVariationIdSingleUuid() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        String varId = "[" + uuid + "] (REGULAR)";

        List<UUID> result = ResultUtils.extractUuidsFromVariationId(varId);

        assertThat(result).containsExactly(uuid);
    }

    @Test
    void extractUuidsFromVariationIdMultipleUuids() {
        UUID uuid1 = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID uuid2 = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String varId = "[" + uuid1 + ", " + uuid2 + "] (REGULAR)";

        List<UUID> result = ResultUtils.extractUuidsFromVariationId(varId);

        assertThat(result).containsExactly(uuid1, uuid2);
    }

    @Test
    void extractUuidsFromVariationIdNoUuid() {
        String varId = "[Name1, Name2] (REGULAR)";

        List<UUID> result = ResultUtils.extractUuidsFromVariationId(varId);

        assertThat(result).isEmpty();
    }

    @Test
    void extractUuidsFromVariationIdEmptyString() {
        List<UUID> result = ResultUtils.extractUuidsFromVariationId("");

        assertThat(result).isEmpty();
    }

    @Test
    void extractUuidsFromVariationIdUuidCaseInsensitive() {
        String varId = "[AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE] (PROPORTIONAL)";

        List<UUID> result = ResultUtils.extractUuidsFromVariationId(varId);

        assertThat(result).containsExactly(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    }

    @Test
    void extractUuidsFromVariationIdNullThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ResultUtils.extractUuidsFromVariationId(null));
    }

    // --- resolveForVariationId ---

    @Test
    void resolveForVariationIdSingleUuidResolved() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        String varId = "[" + uuid + "] (REGULAR)";
        Map<UUID, String> nameByUuid = Map.of(uuid, "Name_1");

        String result = ResultUtils.resolveForVariationId(varId, nameByUuid);

        assertThat(result).isEqualTo("[Name_1] (REGULAR)");
    }

    @Test
    void resolveForVariationIdMultipleUuidsAllResolved() {
        UUID uuid1 = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID uuid2 = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String varId = "[" + uuid1 + ", " + uuid2 + "] (PROPORTIONAL)";
        Map<UUID, String> nameByUuid = Map.of(uuid1, "Name_1", uuid2, "Name_2");

        String result = ResultUtils.resolveForVariationId(varId, nameByUuid);

        assertThat(result).isEqualTo("[Name_1, Name_2] (PROPORTIONAL)");
    }

    @Test
    void resolveForVariationIdUuidNotInMapKeptAsIs() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        String varId = "[" + uuid + "] (REGULAR)";
        Map<UUID, String> nameByUuid = Map.of();

        String result = ResultUtils.resolveForVariationId(varId, nameByUuid);

        assertThat(result).isEqualTo(varId);
    }

    @Test
    void resolveForVariationIdPartiallyResolved() {
        UUID uuid1 = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID uuid2 = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String varId = "[" + uuid1 + ", " + uuid2 + "] (REGULAR)";
        Map<UUID, String> nameByUuid = Map.of(uuid1, "Name_1");

        String result = ResultUtils.resolveForVariationId(varId, nameByUuid);

        assertThat(result).isEqualTo("[Name_1, " + uuid2 + "] (REGULAR)");
    }

    @Test
    void resolveForVariationIdNoUuidInString() {
        String varId = "[Name1] (REGULAR)";
        Map<UUID, String> nameByUuid = Map.of();

        String result = ResultUtils.resolveForVariationId(varId, nameByUuid);

        assertThat(result).isEqualTo(varId);
    }

    @Test
    void resolveForVariationIdEmptyString() {
        String result = ResultUtils.resolveForVariationId("", Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void resolveForVariationIdNullThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ResultUtils.resolveForVariationId(null, Map.of()));
    }

    // --- formatVariationId ---

    @Test
    void formatVariationIdSingleUuid() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");

        String result = ResultUtils.formatVariationId(List.of(uuid), "REGULAR");

        assertThat(result).isEqualTo("[11111111-2222-3333-4444-555555555555] (REGULAR)");
    }

    @Test
    void formatVariationIdMultipleUuids() {
        UUID uuid1 = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID uuid2 = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        String result = ResultUtils.formatVariationId(List.of(uuid1, uuid2), "PROPORTIONAL");

        assertThat(result).isEqualTo("[11111111-2222-3333-4444-555555555555, aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee] (PROPORTIONAL)");
    }

    @Test
    void formatVariationIdEmptyList() {
        String result = ResultUtils.formatVariationId(List.of(), "REGULAR");

        assertThat(result).isEqualTo("[] (REGULAR)");
    }

    // --- joinToStringIds ---

    @Test
    void joinToStringIdsSingleUuid() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");

        String result = ResultUtils.joinToStringIds(List.of(uuid));

        assertThat(result).isEqualTo("[11111111-2222-3333-4444-555555555555]");
    }

    @Test
    void joinToStringIdsMultipleUuids() {
        UUID uuid1 = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID uuid2 = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        String result = ResultUtils.joinToStringIds(List.of(uuid1, uuid2));

        assertThat(result).isEqualTo("[11111111-2222-3333-4444-555555555555, aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee]");
    }

    @Test
    void joinToStringIdsEmptyList() {
        String result = ResultUtils.joinToStringIds(List.of());

        assertThat(result).isEqualTo("[]");
    }

    // --- roundtrip: formatVariationId -> extractUuidsFromVariationId ---

    @Test
    void roundtripFormatThenExtract() {
        UUID uuid1 = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID uuid2 = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        String formatted = ResultUtils.formatVariationId(List.of(uuid1, uuid2), "REGULAR");
        List<UUID> extracted = ResultUtils.extractUuidsFromVariationId(formatted);

        assertThat(extracted).containsExactly(uuid1, uuid2);
    }

    // --- roundtrip: formatVariationId -> resolveForVariationId ---

    @Test
    void roundtripFormatThenResolve() {
        UUID uuid1 = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID uuid2 = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        Map<UUID, String> nameByUuid = Map.of(uuid1, "Name_1", uuid2, "Name_2");

        String formatted = ResultUtils.formatVariationId(List.of(uuid1, uuid2), "REGULAR");
        String resolved = ResultUtils.resolveForVariationId(formatted, nameByUuid);

        assertThat(resolved).isEqualTo("[Name_1, Name_2] (REGULAR)");
    }
}
