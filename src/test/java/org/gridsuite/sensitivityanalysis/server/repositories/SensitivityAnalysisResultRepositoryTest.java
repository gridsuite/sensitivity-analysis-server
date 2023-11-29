/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.repositories;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.ContingencyContextType;
import com.powsybl.sensitivity.*;
import com.vladmihalcea.sql.SQLStatementCountValidator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gridsuite.sensitivityanalysis.server.util.TestUtils.assertRequestsCount;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class SensitivityAnalysisResultRepositoryTest {

    private static final SensitivityFunctionType MW_FUNC_TYPE = SensitivityFunctionType.BRANCH_ACTIVE_POWER_1;
    private static final SensitivityVariableType MW_VAR_TYPE = SensitivityVariableType.INJECTION_ACTIVE_POWER;
    private static final UUID RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5d");

    @Autowired
    private SensitivityAnalysisResultRepository sensitivityAnalysisResultRepository;

    @Before
    public void setUp() {
        sensitivityAnalysisResultRepository.deleteAll();
        SQLStatementCountValidator.reset();
    }

    private static List<SensitivityFactor> makeMWFactors(String funcId, String varId, List<String> mayContingencies) {
        List<SensitivityFactor> aleaFactors;
        if (mayContingencies == null) {
            aleaFactors = List.of(new SensitivityFactor(MW_FUNC_TYPE, funcId, MW_VAR_TYPE, varId, false, ContingencyContext.all()));
        } else {
            aleaFactors = mayContingencies.stream().map(aleaId ->
                    new SensitivityFactor(MW_FUNC_TYPE, funcId, MW_VAR_TYPE, varId, false,
                        ContingencyContext.create(aleaId, ContingencyContextType.SPECIFIC)))
                .collect(Collectors.toList());
            aleaFactors.add(0, new SensitivityFactor(MW_FUNC_TYPE, funcId, MW_VAR_TYPE, varId, false, ContingencyContext.none()));
        }
        return aleaFactors;
    }

    @Test
    public void deleteResultTest() {
        List<String> aleaIds = List.of("a1", "a2", "a3");
        final List<SensitivityAnalysisResult.SensitivityContingencyStatus> contingenciesStatuses = aleaIds.stream()
            .map(aleaId -> new SensitivityAnalysisResult.SensitivityContingencyStatus(aleaId, SensitivityAnalysisResult.Status.SUCCESS))
            .collect(Collectors.toList());

        final List<SensitivityFactor> sensitivityFactors = Stream.of(
            makeMWFactors("l1", "GEN", null), // factor index 0      ; for all()
            makeMWFactors("l2", "GEN", List.of()), // factor index 1 ; for none()
            makeMWFactors("l3", "LOAD", aleaIds)   // factor indices 2, 3, 4 & 5
        ).flatMap(Collection::stream).collect(Collectors.toList());

        final List<SensitivityValue> sensitivityValues = new ArrayList<>(List.of(
            // l1 x GEN, before + a1, a2, a3 (implicit)
            new SensitivityValue(0, -1, 500.1, 2.9),
            new SensitivityValue(0, 0, 500.3, 2.7),
            new SensitivityValue(0, 1, 500.4, 2.6),
            new SensitivityValue(0, 2, 500.5, 2.5),
            // l2 x GEN, just before
            new SensitivityValue(1, -1, 500.2, 2.8),
            // l3 x LOAD, before + a1, a2, a3 (explicit)
            new SensitivityValue(2, -1, 500.9, 2.1),
            new SensitivityValue(3, 1, 500.6, 2.4),
            new SensitivityValue(4, 0, 500.7, 2.3),
            new SensitivityValue(5, 2, 500.8, 2.2)
        ));
        Collections.shuffle(sensitivityValues);

        final SensitivityAnalysisResult result = new SensitivityAnalysisResult(sensitivityFactors,
            contingenciesStatuses,
            sensitivityValues);
        sensitivityAnalysisResultRepository.insert(RESULT_UUID, result, "OK");
        SQLStatementCountValidator.reset();

        sensitivityAnalysisResultRepository.delete(RESULT_UUID);

        // 3 deletes for one result :
        // - its global status
        // - its sensitivities
        // - the result itself
        assertRequestsCount(3, 0, 0, 3);
    }
}
