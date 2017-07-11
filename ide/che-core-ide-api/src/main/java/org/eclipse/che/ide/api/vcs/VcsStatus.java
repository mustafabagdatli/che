package org.eclipse.che.ide.api.vcs;

import java.util.Optional;

import static java.util.Arrays.stream;

public enum VcsStatus {
    UNTRACKED("untracked"),
    ADDED("added"),
    REMOVED("removed"),
    MODIFIED("modified"),
    NOT_MODIFIED(null);

    private String value;

    VcsStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static VcsStatus from(String value) {
        return stream(VcsStatus.values()).filter(vcsStatus -> vcsStatus.getValue().equals(value))
                                         .findAny()
                                         .orElse(NOT_MODIFIED);
    }
}
