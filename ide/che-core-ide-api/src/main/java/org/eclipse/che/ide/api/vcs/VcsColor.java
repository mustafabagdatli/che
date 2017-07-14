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

public enum VcsColor {
    GREEN("LightGreen"),
    RED("LightCoral"),
    BLUE("CornflowerBlue");

    private final String color;

    VcsColor(String color) {
        this.color = color;
    }

    @Override
    public String toString() {
        return color;
    }

    public static VcsColor from(VcsStatus vcsStatus) {
        switch (vcsStatus) {
            case ADDED:
                return GREEN;
            case MODIFIED:
                return BLUE;
            case UNTRACKED:
                return RED;
            default:
                return null;
        }
    }
}
