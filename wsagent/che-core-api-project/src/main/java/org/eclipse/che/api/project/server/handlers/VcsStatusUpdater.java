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
package org.eclipse.che.api.project.server.handlers;

import org.eclipse.che.api.project.shared.dto.ItemReference;

/**
 * Updates vcs status of given {@link ItemReference} file.
 *
 * @author Igor Vinokur
 */
public interface VcsStatusUpdater {

    /**
     * Returns name of the version control system.
     */
    String getVcsName();

    /**
     * Set vcs status to attributes of the given file.
     *
     * @param reference
     *         file to update with vcs status
     */
    void updateStatus(ItemReference reference);
}
