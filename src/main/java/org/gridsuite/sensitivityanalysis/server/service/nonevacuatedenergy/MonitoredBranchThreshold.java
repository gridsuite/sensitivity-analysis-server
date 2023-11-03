/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy;

import com.powsybl.iidm.network.Branch;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
public class MonitoredBranchThreshold {
    Branch<?> branch;

    boolean istN;

    String nLimitName;   // null if istN = true

    Float nCoeff;

    boolean istNm1;

    String nm1LimitName;  // null if istNm1 = true

    Float nm1Coeff;

    public MonitoredBranchThreshold(Branch<?> branch) {
        this.branch = branch;
        this.istN = false;
        this.istNm1 = false;
    }
}
