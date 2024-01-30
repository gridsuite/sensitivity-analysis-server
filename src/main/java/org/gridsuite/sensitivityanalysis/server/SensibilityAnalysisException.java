/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server;

import java.util.Objects;

/**
 * @author Seddik Yengui <seddik.yengui at rte-france.com>
 */

public class SensibilityAnalysisException extends RuntimeException {
    public enum Type {
        RESULT_NOT_FOUND,
        INVALID_EXPORT_PARAMS,
        FILE_EXPORT_ERROR,
    }

    private final Type type;

    public SensibilityAnalysisException(Type type) {
        super(Objects.requireNonNull(type.name()));
        this.type = type;
    }

    public SensibilityAnalysisException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public Type getType() {
        return type;
    }
}
