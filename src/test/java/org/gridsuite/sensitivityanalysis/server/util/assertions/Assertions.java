/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util.assertions;

import com.powsybl.loadflow.LoadFlowParameters;
import org.assertj.core.util.CheckReturnValue;
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.SensitivityAnalysisParametersInfos;

/**
 *  @author Tristan Chuine <tristan.chuine at rte-france.com>
 * {@link org.assertj.core.api.Assertions Assertions} completed with our custom assertions classes.
 */
public class Assertions extends org.assertj.core.api.Assertions {
    @CheckReturnValue
    public static DTOAssert<SensitivityAnalysisParametersInfos> assertThat(SensitivityAnalysisParametersInfos actual) {
        return new DTOAssert<>(actual);
    }
    @CheckReturnValue
    public static DTOAssert<LoadFlowParameters> assertThat(LoadFlowParameters actual) {
        return new DTOAssert<>(actual);
    }
    @CheckReturnValue
    public static DTOAssert<SensitivityInjectionsSet> assertThat(SensitivityInjectionsSet actual) {
        return new DTOAssert<>(actual);
    }
    @CheckReturnValue
    public static DTOAssert<SensitivityInjection> assertThat(SensitivityInjection actual) {
        return new DTOAssert<>(actual);
    }
    @CheckReturnValue
    public static DTOAssert<SensitivityHVDC> assertThat(SensitivityHVDC actual) {
        return new DTOAssert<>(actual);
    }
    @CheckReturnValue
    public static DTOAssert<SensitivityPST> assertThat(SensitivityPST actual) {
        return new DTOAssert<>(actual);
    }
    @CheckReturnValue
    public static DTOAssert<SensitivityNodes> assertThat(SensitivityNodes actual) {
        return new DTOAssert<>(actual);
    }
}
