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
package org.eclipse.che.ide.ext.git.client;

import org.eclipse.che.api.core.jsonrpc.commons.RequestHandlerConfigurator;
import org.eclipse.che.api.git.shared.Status;
import org.eclipse.che.api.project.shared.dto.event.GitChangeEventDto;
import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.parts.EditorMultiPartStack;
import org.eclipse.che.ide.api.parts.EditorTab;
import org.eclipse.che.ide.api.resources.File;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.api.vcs.VcsStatus;
import org.eclipse.che.ide.part.explorer.project.ProjectExplorerPresenter;
import org.eclipse.che.ide.resource.Path;
import org.eclipse.che.ide.resources.tree.FileNode;
import org.eclipse.che.ide.resources.tree.ResourceNode;
import org.eclipse.che.ide.ui.smartTree.Tree;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import static org.eclipse.che.ide.api.vcs.VcsStatus.ADDED;
import static org.eclipse.che.ide.api.vcs.VcsStatus.NOT_MODIFIED;
import static org.eclipse.che.ide.api.vcs.VcsStatus.UNTRACKED;

/**
 * Receives git checkout notifications caught by server side VFS file watching system.
 * Support two type of notifications: git branch checkout and git revision checkout.
 * After a notification is received it is processed and passed to and instance of
 * {@link NotificationManager}.
 */
@Singleton
public class GitChangesHandler {

    private final Provider<EditorAgent>              editorAgentProvider;
    private final Provider<ProjectExplorerPresenter> projectExplorerPresenterProvider;
    private final Provider<EditorMultiPartStack>     multiPartStackProvider;

    @Inject
    public GitChangesHandler(RequestHandlerConfigurator configurator,
                             Provider<EditorAgent> editorAgentProvider,
                             Provider<ProjectExplorerPresenter> projectExplorerPresenterProvider,
                             Provider<EditorMultiPartStack> multiPartStackProvider) {
        this.editorAgentProvider = editorAgentProvider;
        this.projectExplorerPresenterProvider = projectExplorerPresenterProvider;
        this.multiPartStackProvider = multiPartStackProvider;

        configureHandler(configurator);
    }

    private void configureHandler(RequestHandlerConfigurator configurator) {
        configurator.newConfiguration()
                    .methodName("event:git-change")
                    .paramsAsDto(GitChangeEventDto.class)
                    .noResult()
                    .withBiConsumer(this::apply);

        configurator.newConfiguration()
                    .methodName("event:git-index")
                    .paramsAsDto(Status.class)
                    .noResult()
                    .withBiConsumer(this::apply);
    }

    public void apply(String endpointId, GitChangeEventDto dto) {
        Tree tree = projectExplorerPresenterProvider.get().getTree();
        tree.getNodeStorage()
            .getAll()
            .stream()
            .filter(node -> node instanceof FileNode && ((ResourceNode)node).getData()
                                                                            .getLocation()
                                                                            .equals(Path.valueOf(dto.getPath())))
            .forEach(node -> {
                ((ResourceNode)node).getData().asFile().setVcsStatus(VcsStatus.from(dto.getType().toString()));
                tree.refresh(node);
            });

        editorAgentProvider.get()
                           .getOpenedEditors()
                           .stream()
                           .filter(editor -> editor.getEditorInput().getFile().getLocation().equals(Path.valueOf(dto.getPath())))
                           .forEach(editor -> {
                               VcsStatus vcsStatus = VcsStatus.from(dto.getType().toString());
                               EditorTab tab = multiPartStackProvider.get().getTabByPart(editor);
                               if (vcsStatus != null) {
                                   tab.setTitleColor(vcsStatus.getColor());
                               }
                           });
    }

    public void apply(String endpointId, Status status) {
        Tree tree = projectExplorerPresenterProvider.get().getTree();
        tree.getNodeStorage()
            .getAll()
            .stream()
            .filter(node -> node instanceof FileNode)
            .forEach(node -> {
                Resource resource = ((ResourceNode)node).getData();
                File file = resource.asFile();
                String nodeLocation = resource.getLocation().removeFirstSegments(1).toString();
                if (status.getUntracked().contains(nodeLocation) && file.getVcsStatus() != UNTRACKED) {
                    file.setVcsStatus(UNTRACKED);
                    tree.refresh(node);
                } else if (status.getAdded().contains(nodeLocation) && file.getVcsStatus() != ADDED) {
                    file.setVcsStatus(ADDED);
                    tree.refresh(node);
                } else if (!status.getUntracked().contains(nodeLocation) && file.getVcsStatus() == UNTRACKED) {
                    file.setVcsStatus(VcsStatus.NOT_MODIFIED);
                    tree.refresh(node);
                }
            });

        editorAgentProvider.get()
                           .getOpenedEditors()
                           .forEach(editor -> {
                               EditorTab tab = multiPartStackProvider.get().getTabByPart(editor);
                               String nodeLocation = tab.getFile().getLocation().removeFirstSegments(1).toString();
                               if (status.getUntracked().contains(nodeLocation)) {
                                   tab.setTitleColor(UNTRACKED.getColor());
                               } else if (status.getAdded().contains(nodeLocation)) {
                                   tab.setTitleColor(ADDED.getColor());
                               } else {
                                   tab.setTitleColor(NOT_MODIFIED.getColor());
                               }
                           });
    }
}
