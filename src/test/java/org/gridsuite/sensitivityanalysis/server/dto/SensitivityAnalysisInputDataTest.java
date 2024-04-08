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
import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import lombok.SneakyThrows;

import org.gridsuite.sensitivityanalysis.server.service.ActionsService;
import org.gridsuite.sensitivityanalysis.server.service.FilterService;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisInputBuilderService;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisRunContext;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class SensitivityAnalysisInputDataTest {
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final String VARIANT_ID = VariantManagerConstants.INITIAL_VARIANT_ID;
    private static final Network NETWORK = new NetworkFactoryImpl().createNetwork("ghost network", "absent format");

    @MockBean
    private ActionsService actionsService;

    @MockBean
    private FilterService filterService;

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
            .sensitivityInjectionsSets(List.of(SensitivityInjectionsSet.builder()
                .monitoredBranches(List.of(new EquipmentsContainer(UUID.randomUUID(), "u1"), new EquipmentsContainer(UUID.randomUUID(), "u2")))
                .injections(List.of(new EquipmentsContainer(UUID.randomUUID(), "u3"), new EquipmentsContainer(UUID.randomUUID(), "u4")))
                .distributionType(SensitivityAnalysisInputData.DistributionType.REGULAR)
                .contingencies(List.of(new EquipmentsContainer(UUID.randomUUID(), "u5"))).build()))
            .sensitivityInjections(List.of(SensitivityInjection.builder()
                .monitoredBranches(List.of(new EquipmentsContainer(UUID.randomUUID(), "u6"), new EquipmentsContainer(UUID.randomUUID(), "u7")))
                .injections(List.of(new EquipmentsContainer(UUID.randomUUID(), "u8"), new EquipmentsContainer(UUID.randomUUID(), "u9")))
                .contingencies(List.of(new EquipmentsContainer(UUID.randomUUID(), "u10"), new EquipmentsContainer(UUID.randomUUID(), "u11"))).build()))
            .sensitivityHVDCs(List.of(SensitivityHVDC.builder()
                .monitoredBranches(List.of(new EquipmentsContainer(UUID.randomUUID(), "u12")))
                .sensitivityType(SensitivityAnalysisInputData.SensitivityType.DELTA_MW)
                .hvdcs(List.of(new EquipmentsContainer(UUID.randomUUID(), "u13")))
                .contingencies(List.of(new EquipmentsContainer(UUID.randomUUID(), "u14"))).build()))
            .sensitivityPSTs(List.of(SensitivityPST.builder()
                .monitoredBranches(List.of(new EquipmentsContainer(UUID.randomUUID(), "u15")))
                .sensitivityType(SensitivityAnalysisInputData.SensitivityType.DELTA_A)
                .psts(List.of(new EquipmentsContainer(UUID.randomUUID(), "u16"), new EquipmentsContainer(UUID.randomUUID(), "u17")))
                .contingencies(List.of(new EquipmentsContainer(UUID.randomUUID(), "u18"))).build()))
            .sensitivityNodes(List.of(SensitivityNodes.builder()
                .monitoredVoltageLevels(List.of(new EquipmentsContainer(UUID.randomUUID(), "u19")))
                .equipmentsInVoltageRegulation(List.of(new EquipmentsContainer(UUID.randomUUID(), "u20")))
                .contingencies(List.of()).build()))
            .parameters(SensitivityAnalysisParameters.load())
            .build();

        String result1 = objectWriter.writeValueAsString(sensitivityAnalysisInputData1);
        SensitivityAnalysisInputData sensitivityAnalysisInputData2 = mapper.readValue(result1, new TypeReference<>() { });
        String result2 = objectWriter.writeValueAsString(sensitivityAnalysisInputData2);
        assertEquals(result1, result2);
    }

    @Test
    public void testEmptyInputTranslation() {
        SensitivityAnalysisInputBuilderService inputBuilderService;
        given(filterService.getIdentifiablesFromFilters(any(), any(), any())).willThrow(new RuntimeException("FilterException"));
        given(actionsService.getContingencyList(any(), any(), any())).willThrow(new RuntimeException("ContingencyException"));
        inputBuilderService = new SensitivityAnalysisInputBuilderService(actionsService, filterService);
        SensitivityAnalysisInputData.SensitivityAnalysisInputDataBuilder<?, ?> inputBuilder = SensitivityAnalysisInputData.builder();
        SensitivityAnalysisInputData inputData = inputBuilder
            .sensitivityInjectionsSets(List.of())
            .sensitivityInjections(List.of())
            .sensitivityHVDCs(List.of())
            .sensitivityPSTs(List.of())
            .sensitivityNodes(List.of())
            .parameters(SensitivityAnalysisParameters.load())
            .build();
        ReporterModel reporter = new ReporterModel("a", "b");
        SensitivityAnalysisRunContext context;
        context = new SensitivityAnalysisRunContext(NETWORK_UUID, VARIANT_ID, null, null, null, null, inputData);
        inputBuilderService.build(context, NETWORK, reporter);
        Collection<Report> reports;
        reports = reporter.getReports();
        assertThat(reports, not(nullValue()));
        assertThat(reports.size(), is(0));
    }

    @Test
    public void testFilterPbInputTranslation() {
        SensitivityAnalysisInputBuilderService inputBuilderService;
        given(filterService.getIdentifiablesFromFilters(any(), any(), any())).willThrow(new RuntimeException("FilterException"));
        given(actionsService.getContingencyList(any(), any(), any())).willThrow(new RuntimeException("ContingencyException"));
        inputBuilderService = new SensitivityAnalysisInputBuilderService(actionsService, filterService);
        SensitivityAnalysisInputData.SensitivityAnalysisInputDataBuilder<?, ?> inputBuilder = SensitivityAnalysisInputData.builder();
        ReporterModel reporter = new ReporterModel("a", "b");
        SensitivityAnalysisRunContext context;

        SensitivityAnalysisInputData inputData = inputBuilder
            .sensitivityInjectionsSets(List.of())
            .sensitivityHVDCs(List.of())
            .sensitivityPSTs(List.of())
            .sensitivityNodes(List.of())
            .parameters(SensitivityAnalysisParameters.load())
            .sensitivityInjections(List.of(SensitivityInjection.builder()
                .monitoredBranches(List.of(new EquipmentsContainer(UUID.randomUUID(), "u6"), new EquipmentsContainer(UUID.randomUUID(), "u7")))
                .injections(List.of(new EquipmentsContainer(UUID.randomUUID(), "u8"), new EquipmentsContainer(UUID.randomUUID(), "u9")))
                .contingencies(List.of(new EquipmentsContainer(UUID.randomUUID(), "u10"), new EquipmentsContainer(UUID.randomUUID(), "u11")))
                .build()))
            .build();
        context = new SensitivityAnalysisRunContext(NETWORK_UUID, VARIANT_ID, null, null, null, null, inputData);
        inputBuilderService.build(context, NETWORK, reporter);
        Collection<Report> reports = reporter.getReports();
        assertThat(reports, not(nullValue()));
        assertThat(reports.size(), is(3));
        Set<String> reportKeys = reports.stream().map(Report::getReportKey).collect(Collectors.toSet());
        assertThat(reportKeys.size(), is(2));
        assertThat(reportKeys, contains("contingencyTranslationFailure", "filterTranslationFailure"));
    }

    @Test
    @SneakyThrows
    public void testFilterWiderPbInputTranslation() {
        SensitivityAnalysisInputBuilderService inputBuilderService;
        inputBuilderService = new SensitivityAnalysisInputBuilderService(actionsService, filterService);
        SensitivityAnalysisInputData.SensitivityAnalysisInputDataBuilder<?, ?> inputBuilder = SensitivityAnalysisInputData.builder();
        SensitivityAnalysisRunContext context;

        SensitivityAnalysisInputData inputData = inputBuilder
            .build();
        context = new SensitivityAnalysisRunContext(NETWORK_UUID, VARIANT_ID, null, null, null, null, inputData);
        final ReporterModel reporter = new ReporterModel("a", "b");
        var thrown = assertThrows(NullPointerException.class, () -> inputBuilderService.build(context, NETWORK, reporter));
        assertThat(thrown, Matchers.instanceOf(NullPointerException.class));

        Collection<Report> reports = reporter.getReports();
        assertThat(reports, not(nullValue()));
        assertThat(reports.size(), is(1));
        Set<String> reportKeys = reports.stream().map(Report::getReportKey).collect(Collectors.toSet());
        assertThat(reportKeys.size(), is(1));
        assertThat(reportKeys, contains("sensitivityInputParametersTranslationFailure"));
    }
}
