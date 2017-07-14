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
package org.eclipse.che.api.git;

import org.eclipse.che.api.git.exception.GitException;
import org.eclipse.che.api.git.shared.Status;
import org.eclipse.che.api.git.shared.StatusFormat;
import org.eclipse.che.api.project.server.handlers.VcsStatusUpdater;
import org.eclipse.che.api.project.shared.dto.ItemReference;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Git implementation of {@link VcsStatusUpdater}.
 *
 * @author Igor Vinokur
 */
public class GitStatusUpdater implements VcsStatusUpdater {
    private final GitConnectionFactory gitConnectionFactory;

    @Inject
    public GitStatusUpdater(GitConnectionFactory gitConnectionFactory) {
        this.gitConnectionFactory = gitConnectionFactory;
    }

    @Override
    public String getVcsName() {
        return GitProjectType.TYPE_ID;
    }

    @Override
    public void updateStatus(ItemReference reference) {
        try {
            String projectPath = reference.getPath().substring(1);
            Status status = gitConnectionFactory.getConnection(projectPath.substring(0, projectPath.indexOf("/")))
                                                .status(StatusFormat.SHORT);

            Map<String, String> attributes = new HashMap<>(reference.getAttributes());
            String nodePath = reference.getPath().substring(reference.getPath().substring(1).indexOf("/") + 2);
            if (status.getUntracked().contains(nodePath)) {
                attributes.put("vcs.status", "untracked");
            } else if (status.getAdded().contains(nodePath)) {
                attributes.put("vcs.status", "added");
            } else if (status.getModified().contains(nodePath) || status.getChanged().contains(nodePath)) {
                attributes.put("vcs.status", "modified");
            }
            reference.setAttributes(attributes);

        } catch (GitException e) {
            e.printStackTrace();
        }
    }
}
