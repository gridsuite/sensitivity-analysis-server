/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class SensitivityAnalysisInputDataTest {
    @Autowired
    private ObjectMapper mapper;

    private ObjectWriter objectWriter;

    @Before
    public void setUp() {
        objectWriter = mapper.writer().withDefaultPrettyPrinter();
    }

    @Test
    @SneakyThrows
    public void test() {
        SensitivityAnalysisInputData sensitivityAnalysisInputData1 = SensitivityAnalysisInputData.builder()
            .resultsThreshold(0.20)
            .sensitivityInjectionsSets(List.of(SensitivityInjectionsSet.builder()
                .monitoredBranches(List.of(new FilterIdent(UUID.randomUUID(), "u1"), new FilterIdent(UUID.randomUUID(), "u2")))
                .injections(List.of(new FilterIdent(UUID.randomUUID(), "u3"), new FilterIdent(UUID.randomUUID(), "u4")))
                .distributionType(SensitivityAnalysisInputData.DistributionType.REGULAR)
                .contingencies(List.of(new FilterIdent(UUID.randomUUID(), "u5"))).build()))
            .sensitivityInjections(List.of(SensitivityInjection.builder()
                .monitoredBranches(List.of(new FilterIdent(UUID.randomUUID(), "u6"), new FilterIdent(UUID.randomUUID(), "u7")))
                .injections(List.of(new FilterIdent(UUID.randomUUID(), "u8"), new FilterIdent(UUID.randomUUID(), "u9")))
                .contingencies(List.of(new FilterIdent(UUID.randomUUID(), "u10"), new FilterIdent(UUID.randomUUID(), "u11"))).build()))
            .sensitivityHVDCs(List.of(SensitivityHVDC.builder()
                .monitoredBranches(List.of(new FilterIdent(UUID.randomUUID(), "u12")))
                .sensitivityType(SensitivityAnalysisInputData.SensitivityType.DELTA_MW)
                .hvdcs(List.of(new FilterIdent(UUID.randomUUID(), "u13")))
                .contingencies(List.of(new FilterIdent(UUID.randomUUID(), "u14"))).build()))
            .sensitivityPSTs(List.of(SensitivityPST.builder()
                .monitoredBranches(List.of(new FilterIdent(UUID.randomUUID(), "u15")))
                .sensitivityType(SensitivityAnalysisInputData.SensitivityType.DELTA_A)
                .psts(List.of(new FilterIdent(UUID.randomUUID(), "u16"), new FilterIdent(UUID.randomUUID(), "u17")))
                .contingencies(List.of(new FilterIdent(UUID.randomUUID(), "u18"))).build()))
            .sensitivityNodes(List.of(SensitivityNodes.builder()
                .monitoredVoltageLevels(List.of(new FilterIdent(UUID.randomUUID(), "u19")))
                .equipmentsInVoltageRegulation(List.of(new FilterIdent(UUID.randomUUID(), "u20")))
                .contingencies(List.of()).build()))
            .parameters(SensitivityAnalysisParameters.load())
            .build();

        String result1 = objectWriter.writeValueAsString(sensitivityAnalysisInputData1);
        SensitivityAnalysisInputData sensitivityAnalysisInputData2 = mapper.readValue(result1, new TypeReference<>() { });
        String result2 = objectWriter.writeValueAsString(sensitivityAnalysisInputData2);
        assertEquals(result1, result2);
    }
}
