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
import org.eclipse.che.api.project.server.handlers.GetTreeHandler;
import org.eclipse.che.api.project.shared.dto.ItemReference;
import org.eclipse.che.api.project.shared.dto.TreeElement;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Igor Vinokur
 */
public class GetTreeHandlerImpl implements GetTreeHandler {
    private final GitConnectionFactory gitConnectionFactory;

    @Inject
    public GetTreeHandlerImpl(GitConnectionFactory gitConnectionFactory) {
        this.gitConnectionFactory = gitConnectionFactory;
    }

    @Override
    public String getProjectType() {
        return GitProjectType.TYPE_ID;
    }

    @Override
    public void onGetTree(List<TreeElement> nodes) {
        try {
            String nodePath = nodes.get(0).getNode().getPath().substring(1);
            Status status = gitConnectionFactory.getConnection(nodePath.substring(0, nodePath.indexOf("/")))
                                                .status(StatusFormat.SHORT);
            nodes.forEach(treeElement -> {
                ItemReference node = treeElement.getNode();
                Map<String, String> attributes = new HashMap<>(node.getAttributes());
                if (status.getUntracked().contains(node.getPath().substring(node.getPath().substring(1).indexOf("/")+ 2))) {
                    attributes.put("vcs.status", "untracked");
                    node.setAttributes(attributes);
                }
            });
        } catch (GitException e) {
            e.printStackTrace();
        }
    }
}
