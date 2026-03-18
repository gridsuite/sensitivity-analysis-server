/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.FactorCount;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.SensitivityAnalysisParametersInfos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author Antoine Bouhours <antoine.bouhours at rte-france.com>
 */
@ExtendWith(MockitoExtension.class)
class SensitivityAnalysisServiceFactorsCountTest {

    @Mock
    private ActionsService actionsService;

    @Mock
    private FilterService filterService;

    @InjectMocks
    private SensitivityAnalysisFactorCountService factorCountService;

    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final String VARIANT_ID = "variant1";

    private static final UUID BRANCH1_UUID = UUID.randomUUID();
    private static final UUID BRANCH2_UUID = UUID.randomUUID();
    private static final UUID GEN1_UUID = UUID.randomUUID();
    private static final UUID GEN2_UUID = UUID.randomUUID();
    private static final UUID HVDC1_UUID = UUID.randomUUID();
    private static final UUID PST1_UUID = UUID.randomUUID();
    private static final UUID VOLTAGE_LEVEL1_UUID = UUID.randomUUID();
    private static final UUID EQUIPMENT_REGULATION1_UUID = UUID.randomUUID();
    private static final UUID CONTINGENCY1_UUID = UUID.randomUUID();
    private static final UUID CONTINGENCY2_UUID = UUID.randomUUID();

    @Test
    void testInjectionsSetWithoutContingencies() {
        SensitivityAnalysisParametersInfos parameters = createParameters(
                List.of(new SensitivityInjectionsSet(List.of(BRANCH1_UUID, BRANCH2_UUID), null, null, List.of(), true)),
                null, null, null, null
        );

        mockFilterServiceResponse(Map.of("monitored-0", 2L));

        FactorCount result = factorCountService.getFactorCount(
                NETWORK_UUID, VARIANT_ID,
                parameters.getSensitivityInjectionsSet(),
                parameters.getSensitivityInjection(),
                parameters.getSensitivityHVDC(),
                parameters.getSensitivityPST(),
                parameters.getSensitivityNodes()
        );

        assertEquals(1L, result.variableCount());
        assertEquals(2L, result.resultCount(), "2 monitored × (1 base) = 2");
    }

    @Test
    void testInjectionsSetWithContingencies() {
        SensitivityAnalysisParametersInfos parameters = createParameters(
                List.of(new SensitivityInjectionsSet(List.of(BRANCH1_UUID, BRANCH2_UUID), null, null,
                        List.of(CONTINGENCY1_UUID, CONTINGENCY2_UUID), true)),
                null, null, null, null
        );

        mockFilterServiceResponse(Map.of("monitored-0", 2L));
        mockContingencyServiceResponse(Map.of("contingencies-0", 3L));

        FactorCount result = factorCountService.getFactorCount(
                NETWORK_UUID, VARIANT_ID,
                parameters.getSensitivityInjectionsSet(),
                parameters.getSensitivityInjection(),
                parameters.getSensitivityHVDC(),
                parameters.getSensitivityPST(),
                parameters.getSensitivityNodes()
        );

        assertEquals(1L, result.variableCount());
        assertEquals(8L, result.resultCount(), "2 monitored × (1 base + 3 contingencies) = 8");
    }

    @Test
    void testInjectionsWithoutContingencies() {
        SensitivityAnalysisParametersInfos parameters = createParameters(
                null,
                List.of(new SensitivityInjection(List.of(BRANCH1_UUID), List.of(GEN1_UUID, GEN2_UUID), List.of(), true)),
                null, null, null
        );

        mockFilterServiceResponse(Map.of(
                "monitored-0", 1L,
                "variables-0", 2L
        ));

        FactorCount result = factorCountService.getFactorCount(
                NETWORK_UUID, VARIANT_ID,
                parameters.getSensitivityInjectionsSet(),
                parameters.getSensitivityInjection(),
                parameters.getSensitivityHVDC(),
                parameters.getSensitivityPST(),
                parameters.getSensitivityNodes()
        );

        assertEquals(2L, result.variableCount());
        assertEquals(2L, result.resultCount(), "1 monitored × 2 variables × 1 (base)");
    }

    @Test
    void testInjectionsWithContingencies() {
        SensitivityAnalysisParametersInfos parameters = createParameters(
                null,
                List.of(new SensitivityInjection(List.of(BRANCH1_UUID, BRANCH2_UUID),
                        List.of(GEN1_UUID, GEN2_UUID), List.of(CONTINGENCY1_UUID), true)),
                null, null, null
        );

        mockFilterServiceResponse(Map.of(
                "monitored-0", 2L,
                "variables-0", 2L
        ));
        mockContingencyServiceResponse(Map.of("contingencies-0", 1L));

        FactorCount result = factorCountService.getFactorCount(
                NETWORK_UUID, VARIANT_ID,
                parameters.getSensitivityInjectionsSet(),
                parameters.getSensitivityInjection(),
                parameters.getSensitivityHVDC(),
                parameters.getSensitivityPST(),
                parameters.getSensitivityNodes()
        );

        assertEquals(2L, result.variableCount());
        assertEquals(8L, result.resultCount(), "2 monitored × 2 variables × (1 base + 1 contingency)");
    }

    @Test
    void testHVDC() {
        SensitivityAnalysisParametersInfos parameters = createParameters(
                null, null,
                List.of(new SensitivityHVDC(List.of(BRANCH1_UUID), null, List.of(HVDC1_UUID), List.of(), true)),
                null, null
        );

        mockFilterServiceResponse(Map.of(
                "monitored-0", 1L,
                "variables-0", 1L
        ));

        FactorCount result = factorCountService.getFactorCount(
                NETWORK_UUID, VARIANT_ID,
                parameters.getSensitivityInjectionsSet(),
                parameters.getSensitivityInjection(),
                parameters.getSensitivityHVDC(),
                parameters.getSensitivityPST(),
                parameters.getSensitivityNodes()
        );

        assertEquals(1L, result.variableCount());
        assertEquals(1L, result.resultCount());
    }

    @Test
    void testPST() {
        SensitivityAnalysisParametersInfos parameters = createParameters(
                null, null, null,
                List.of(new SensitivityPST(List.of(BRANCH1_UUID), null, List.of(PST1_UUID), List.of(), true)),
                null
        );

        mockFilterServiceResponse(Map.of(
                "monitored-0", 1L,
                "variables-0", 1L
        ));

        FactorCount result = factorCountService.getFactorCount(
                NETWORK_UUID, VARIANT_ID,
                parameters.getSensitivityInjectionsSet(),
                parameters.getSensitivityInjection(),
                parameters.getSensitivityHVDC(),
                parameters.getSensitivityPST(),
                parameters.getSensitivityNodes()
        );

        assertEquals(1L, result.variableCount());
        assertEquals(1L, result.resultCount());
    }

    @Test
    void testNodesWithEmpiricalFactor() {
        SensitivityAnalysisParametersInfos parameters = createParameters(
                null, null, null, null,
                List.of(new SensitivityNodes(List.of(VOLTAGE_LEVEL1_UUID),
                        List.of(EQUIPMENT_REGULATION1_UUID), List.of(), true))
        );

        mockFilterServiceResponse(Map.of(
                "monitored-0", 3L,
                "variables-0", 2L
        ));

        FactorCount result = factorCountService.getFactorCount(
                NETWORK_UUID, VARIANT_ID,
                parameters.getSensitivityInjectionsSet(),
                parameters.getSensitivityInjection(),
                parameters.getSensitivityHVDC(),
                parameters.getSensitivityPST(),
                parameters.getSensitivityNodes()
        );

        assertEquals(2L, result.variableCount());
        assertEquals(12L, result.resultCount(), "3 monitored × 2 variables × 1 (base) × 2 (empirical factor)");
    }

    @Test
    void testInactivatedFactorsAreIgnored() {
        SensitivityAnalysisParametersInfos parameters = createParameters(
                List.of(
                        new SensitivityInjectionsSet(List.of(BRANCH1_UUID), null, null, List.of(), true),
                        new SensitivityInjectionsSet(List.of(BRANCH2_UUID), null, null, List.of(), false)
                ),
                List.of(
                        new SensitivityInjection(List.of(BRANCH1_UUID), List.of(GEN1_UUID), List.of(), false)
                ),
                null, null, null
        );

        mockFilterServiceResponse(Map.of("monitored-0", 1L));

        FactorCount result = factorCountService.getFactorCount(
                NETWORK_UUID, VARIANT_ID,
                parameters.getSensitivityInjectionsSet(),
                parameters.getSensitivityInjection(),
                parameters.getSensitivityHVDC(),
                parameters.getSensitivityPST(),
                parameters.getSensitivityNodes()
        );

        assertEquals(1L, result.variableCount());
        assertEquals(1L, result.resultCount());
    }

    @Test
    void testEmptyParameters() {
        SensitivityAnalysisParametersInfos parameters = createParameters(
                null, null, null, null, null
        );

        FactorCount result = factorCountService.getFactorCount(
                NETWORK_UUID, VARIANT_ID,
                parameters.getSensitivityInjectionsSet(),
                parameters.getSensitivityInjection(),
                parameters.getSensitivityHVDC(),
                parameters.getSensitivityPST(),
                parameters.getSensitivityNodes()
        );

        assertEquals(0L, result.variableCount());
        assertEquals(0L, result.resultCount());
        verifyNoInteractions(filterService, actionsService);
    }

    @Test
    void testEmptyContingenciesListDoesNotCallActionsService() {
        SensitivityAnalysisParametersInfos parameters = createParameters(
                List.of(new SensitivityInjectionsSet(List.of(BRANCH1_UUID), null, null, List.of(), true)),
                null, null, null, null
        );

        mockFilterServiceResponse(Map.of("monitored-0", 1L));

        factorCountService.getFactorCount(
                NETWORK_UUID, VARIANT_ID,
                parameters.getSensitivityInjectionsSet(),
                parameters.getSensitivityInjection(),
                parameters.getSensitivityHVDC(),
                parameters.getSensitivityPST(),
                parameters.getSensitivityNodes()
        );

        verifyNoInteractions(actionsService);
    }

    @Test
    void testAllTypes() {
        SensitivityAnalysisParametersInfos parameters = createParameters(
                List.of(new SensitivityInjectionsSet(List.of(BRANCH1_UUID, BRANCH2_UUID), null, null,
                        List.of(CONTINGENCY1_UUID), true)),
                List.of(new SensitivityInjection(List.of(BRANCH1_UUID), List.of(GEN1_UUID, GEN2_UUID),
                        List.of(CONTINGENCY1_UUID, CONTINGENCY2_UUID), true)),
                List.of(new SensitivityHVDC(List.of(BRANCH2_UUID), null, List.of(HVDC1_UUID), List.of(), true)),
                List.of(new SensitivityPST(List.of(BRANCH1_UUID), null, List.of(PST1_UUID), List.of(), true)),
                List.of(new SensitivityNodes(List.of(VOLTAGE_LEVEL1_UUID),
                        List.of(EQUIPMENT_REGULATION1_UUID), List.of(CONTINGENCY1_UUID), true))
        );

        mockFilterServiceResponse(Map.of(
                "monitored-0", 2L,  // injections set
                "monitored-1", 1L,  // injections
                "variables-1", 2L,
                "monitored-2", 1L,  // hvdc
                "variables-2", 1L,
                "monitored-3", 1L,  // pst
                "variables-3", 1L,
                "monitored-4", 2L,  // nodes
                "variables-4", 3L
        ));

        mockContingencyServiceResponse(Map.of(
                "contingencies-0", 1L,  // injections set: 1 contingency
                "contingencies-1", 2L,  // injections: 2 contingencies
                "contingencies-4", 1L   // nodes: 1 contingency
        ));

        FactorCount result = factorCountService.getFactorCount(
                NETWORK_UUID, VARIANT_ID,
                parameters.getSensitivityInjectionsSet(),
                parameters.getSensitivityInjection(),
                parameters.getSensitivityHVDC(),
                parameters.getSensitivityPST(),
                parameters.getSensitivityNodes()
        );

        assertEquals(8L, result.variableCount());
        assertEquals(36L, result.resultCount(), "(injections set: 2 mon. × (1 base + 1 cont.)) + (injections: 1 mon. × 2 var. × (1 base + 2 cont.)) + (hvdc: 1 mon. × 1 var. × 1 base) + (pst: 1 mon. × 1 var. × 1 base) + (nodes: 2 mon. × 3 var. × 2 emp. fact. × (1 base + 1 cont.)) = 36");
    }

    @Test
    void testLargeNumbers() {
        SensitivityAnalysisParametersInfos parameters = createParameters(
                null,
                List.of(new SensitivityInjection(List.of(BRANCH1_UUID), List.of(GEN1_UUID), List.of(), true)),
                null, null, null
        );

        mockFilterServiceResponse(Map.of(
                "monitored-0", 50_000L,
                "variables-0", 10_000L
        ));

        FactorCount result = factorCountService.getFactorCount(
                NETWORK_UUID, VARIANT_ID,
                parameters.getSensitivityInjectionsSet(),
                parameters.getSensitivityInjection(),
                parameters.getSensitivityHVDC(),
                parameters.getSensitivityPST(),
                parameters.getSensitivityNodes()
        );

        assertEquals(10_000L, result.variableCount());
        assertEquals(500_000_000L, result.resultCount());
    }

    private SensitivityAnalysisParametersInfos createParameters(
            List<SensitivityInjectionsSet> injectionsSet,
            List<SensitivityInjection> injections,
            List<SensitivityHVDC> hvdc,
            List<SensitivityPST> pst,
            List<SensitivityNodes> nodes
    ) {
        SensitivityAnalysisParametersInfos params = new SensitivityAnalysisParametersInfos();
        params.setSensitivityInjectionsSet(injectionsSet != null ? injectionsSet : List.of());
        params.setSensitivityInjection(injections != null ? injections : List.of());
        params.setSensitivityHVDC(hvdc != null ? hvdc : List.of());
        params.setSensitivityPST(pst != null ? pst : List.of());
        params.setSensitivityNodes(nodes != null ? nodes : List.of());
        return params;
    }

    private void mockFilterServiceResponse(Map<String, Long> response) {
        when(filterService.getIdentifiablesCountByGroup(any(), eq(NETWORK_UUID), eq(VARIANT_ID)))
                .thenReturn(response);
    }

    private void mockContingencyServiceResponse(Map<String, Long> response) {
        when(actionsService.getContingencyCountByGroup(any(), eq(NETWORK_UUID), eq(VARIANT_ID)))
                .thenReturn(response);
    }
}
