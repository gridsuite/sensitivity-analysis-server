/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import com.powsybl.commons.PowsyblException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.gridsuite.sensitivityanalysis.server.SensitivityAnalysisControllerTest.DEFAULT_PROVIDER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class SensitivityAnalysisRunnerSupplierTest {

    @Autowired
    SensitivityAnalysisRunnerSupplier sensitivityAnalysisRunnerSupplier;

    @Test
    public void test() {
        assertEquals(DEFAULT_PROVIDER, sensitivityAnalysisRunnerSupplier.getRunner(DEFAULT_PROVIDER).getName());
        assertEquals(DEFAULT_PROVIDER, sensitivityAnalysisRunnerSupplier.getRunner(null).getName());

        PowsyblException e = assertThrows(PowsyblException.class, () -> sensitivityAnalysisRunnerSupplier.getRunner("XXX"));
        assertEquals("SensitivityAnalysisProvider 'XXX' not found", e.getMessage());
    }
}
