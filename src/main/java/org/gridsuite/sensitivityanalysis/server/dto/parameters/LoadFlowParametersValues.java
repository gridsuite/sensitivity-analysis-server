/**
  Copyright (c) 2023, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server.dto.parameters;

import com.powsybl.loadflow.LoadFlowParameters;
import lombok.Builder;

import java.util.Map;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Builder
public record LoadFlowParametersValues(
    LoadFlowParameters commonParameters,
    Map<String, String> specificParameters) {
}
