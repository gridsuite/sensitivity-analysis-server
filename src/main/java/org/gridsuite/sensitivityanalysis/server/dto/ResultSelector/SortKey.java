/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server.dto.ResultSelector;

/**
 * @author Seddik Yengui <seddik.yengui at rte-france.com>
 */

public enum SortKey {
    FUNCTION,
    VARIABLE,
    CONTINGENCY,
    REFERENCE,
    SENSITIVITY,
    POST_REFERENCE,
    POST_SENSITIVITY
}
