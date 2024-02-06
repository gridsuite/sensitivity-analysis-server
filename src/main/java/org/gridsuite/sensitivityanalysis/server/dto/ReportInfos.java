/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server.dto;

import java.util.UUID;

/**
 * @author Florent MILLOT <florent.millot at rte-france.com>
 */
public record ReportInfos(
    UUID reportUuid,
    String reporterId,
    String reportType
) {
}
