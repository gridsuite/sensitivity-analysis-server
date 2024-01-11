/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipInputStream;

import static com.vladmihalcea.sql.SQLStatementCountValidator.*;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
public final class TestUtils {

    private TestUtils() { }

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
}
