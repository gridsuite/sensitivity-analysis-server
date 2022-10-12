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

    private static UUID uuid1 = UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00d");
    private static UUID uuid2 = UUID.fromString("11111111-8cf0-11bd-b23e-10b96e4ef00d");
    private static UUID uuid3 = UUID.fromString("22222222-8cf0-11bd-b23e-10b96e4ef00d");
    private static UUID uuid4 = UUID.fromString("33333333-8cf0-11bd-b23e-10b96e4ef00d");
    private static UUID uuid5 = UUID.fromString("44444444-8cf0-11bd-b23e-10b96e4ef00d");
    private static UUID uuid6 = UUID.fromString("55555555-8cf0-11bd-b23e-10b96e4ef00d");
    private static UUID uuid7 = UUID.fromString("66666666-8cf0-11bd-b23e-10b96e4ef00d");
    private static UUID uuid8 = UUID.fromString("77777777-8cf0-11bd-b23e-10b96e4ef00d");
    private static UUID uuid9 = UUID.fromString("88888888-8cf0-11bd-b23e-10b96e4ef00d");
    private static UUID uuid10 = UUID.fromString("99999999-8cf0-11bd-b23e-10b96e4ef00d");
    private static UUID uuid11 = UUID.fromString("11111111-2222-11bd-b23e-10b96e4ef00d");
    private static UUID uuid12 = UUID.fromString("11111111-3333-11bd-b23e-10b96e4ef00d");
    private static UUID uuid13 = UUID.fromString("11111111-4444-11bd-b23e-10b96e4ef00d");
    private static UUID uuid14 = UUID.fromString("11111111-5555-11bd-b23e-10b96e4ef00d");
    private static UUID uuid15 = UUID.fromString("11111111-6666-11bd-b23e-10b96e4ef00d");
    private static UUID uuid16 = UUID.fromString("11111111-7777-11bd-b23e-10b96e4ef00d");
    private static UUID uuid17 = UUID.fromString("11111111-8888-11bd-b23e-10b96e4ef00d");
    private static UUID uuid18 = UUID.fromString("11111111-9999-11bd-b23e-10b96e4ef00d");
    private static UUID uuid19 = UUID.fromString("11111111-0000-11bd-b23e-10b96e4ef00d");
    private static UUID uuid20 = UUID.fromString("22222222-1111-11bd-b23e-10b96e4ef00d");

    @Before
    public void setUp() {
        objectWriter = mapper.writer().withDefaultPrettyPrinter();
    }

    @Test
    @SneakyThrows
    public void test() {
        SensitivityAnalysisInputData sensitivityAnalysisInputData1 = SensitivityAnalysisInputData.builder()
            .resultsThreshold(0.20)
            .sensitivityInjectionsSets(List.of(SensitivityAnalysisInputData.SensitivityInjectionsSet.builder()
                .monitoredBranches(List.of(uuid1, uuid2))
                .injections(List.of(uuid3, uuid4))
                .distributionType(SensitivityAnalysisInputData.DistributionType.REGULAR)
                .contingencies(List.of(uuid5)).build()))
            .sensitivityInjections(List.of(SensitivityAnalysisInputData.SensitivityInjection.builder()
                .monitoredBranches(List.of(uuid6, uuid7))
                .injections(List.of(uuid8, uuid9))
                .contingencies(List.of(uuid10, uuid11)).build()))
            .sensitivityHVDCs(List.of(SensitivityAnalysisInputData.SensitivityHVDC.builder()
                .monitoredBranches(List.of(uuid12))
                .sensitivityType(SensitivityAnalysisInputData.SensitivityType.DELTA_MW)
                .hvdcs(List.of(uuid13))
                .contingencies(List.of(uuid14)).build()))
            .sensitivityPSTs(List.of(SensitivityAnalysisInputData.SensitivityPST.builder()
                .monitoredBranches(List.of(uuid15))
                .sensitivityType(SensitivityAnalysisInputData.SensitivityType.DELTA_A)
                .psts(List.of(uuid16, uuid17))
                .contingencies(List.of(uuid18)).build()))
            .sensitivityNodes(List.of(SensitivityAnalysisInputData.SensitivityNodes.builder()
                .monitoredVoltageLevels(List.of(uuid19))
                .equipmentsInVoltageRegulation(List.of(uuid20))
                .contingencies(List.of()).build()))
            .parameters(SensitivityAnalysisParameters.load())
            .build();

        String result1 = objectWriter.writeValueAsString(sensitivityAnalysisInputData1);
        SensitivityAnalysisInputData sensitivityAnalysisInputData2 = mapper.readValue(result1, new TypeReference<>() { });
        String result2 = objectWriter.writeValueAsString(sensitivityAnalysisInputData2);
        assertEquals(result1, result2);
    }
}
