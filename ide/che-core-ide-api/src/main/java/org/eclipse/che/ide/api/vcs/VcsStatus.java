/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.api.vcs;

import static java.util.Arrays.stream;

public enum VcsStatus {
    UNTRACKED("untracked"),
    ADDED("added"),
    MODIFIED("modified"),
    NOT_MODIFIED("not_modified");

    private String value;

    VcsStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static VcsStatus from(String value) {
        return stream(VcsStatus.values()).filter(vcsStatus -> vcsStatus.getValue().equals(value.toLowerCase()))
                                         .findAny()
                                         .orElse(null);
    }
}
