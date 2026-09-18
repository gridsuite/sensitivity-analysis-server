/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import com.google.common.io.ByteStreams;
import com.powsybl.commons.report.ReportNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipInputStream;

import static com.vladmihalcea.sql.SQLStatementCountValidator.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
public final class TestUtils {

    private TestUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static final String DEFAULT_PROVIDER = "OpenLoadFlow";

    public static void assertRequestsCount(long select, long insert, long update, long delete) {
        assertSelectCount(select);
        assertInsertCount(insert);
        assertUpdateCount(update);
        assertDeleteCount(delete);
    }

    public static byte[] unzip(byte[] zippedBytes) throws Exception {
        var zipInputStream = new ZipInputStream(new ByteArrayInputStream(zippedBytes));
        var buff = new byte[1024];
        if (zipInputStream.getNextEntry() != null) {
            var outputStream = new ByteArrayOutputStream();
            int l;
            while ((l = zipInputStream.read(buff)) > 0) {
                outputStream.write(buff, 0, l);
            }
            return outputStream.toByteArray();
        }
        return new byte[0];
    }

    public static void testReportNode(ReportNode reportNode, String reportsFile) throws IOException {
        Optional<ReportNode> report = reportNode.getChildren().stream().findFirst();
        assertTrue(report.isPresent());

        StringWriter sw = new StringWriter();
        reportNode.print(sw);

        String expected;
        try (InputStream refStream = TestUtils.class.getResourceAsStream(reportsFile)) {
            assertNotNull(refStream);
            expected = new String(ByteStreams.toByteArray(refStream), StandardCharsets.UTF_8);
        }
        String expectedStr = normalizeLineSeparator(expected);
        String actualStr = normalizeLineSeparator(sw.toString());
        assertEquals(expectedStr, actualStr);
    }

    private static String normalizeLineSeparator(String str) {
        return Objects.requireNonNull(str).replace("\r\n", "\n")
                .replace("\r", "\n");
    }
}
