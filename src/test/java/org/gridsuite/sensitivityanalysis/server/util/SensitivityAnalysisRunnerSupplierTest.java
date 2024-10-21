/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import com.powsybl.commons.PowsyblException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.gridsuite.sensitivityanalysis.server.util.TestUtils.DEFAULT_PROVIDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@SpringBootTest(properties = "sensitivity-analysis.default-provider=" + DEFAULT_PROVIDER, classes = SensitivityAnalysisRunnerSupplier.class)
class SensitivityAnalysisRunnerSupplierTest {
    @Autowired
    private SensitivityAnalysisRunnerSupplier sensitivityAnalysisRunnerSupplier;

    @Test
    void testGetRunner() {
        assertEquals(DEFAULT_PROVIDER, sensitivityAnalysisRunnerSupplier.getRunner(DEFAULT_PROVIDER).getName());
        assertEquals(DEFAULT_PROVIDER, sensitivityAnalysisRunnerSupplier.getRunner(null).getName());

        PowsyblException e = assertThrows(PowsyblException.class, () -> sensitivityAnalysisRunnerSupplier.getRunner("XXX"));
        assertEquals("SensitivityAnalysisProvider 'XXX' not found", e.getMessage());
    }
}
