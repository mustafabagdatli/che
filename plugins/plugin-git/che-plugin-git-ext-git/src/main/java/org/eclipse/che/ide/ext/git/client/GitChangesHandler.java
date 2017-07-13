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

import com.google.common.base.Optional;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.core.jsonrpc.commons.RequestHandlerConfigurator;
import org.eclipse.che.api.git.shared.Status;
import org.eclipse.che.api.project.shared.dto.event.GitChangeEventDto;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.js.Executor;
import org.eclipse.che.api.promises.client.js.Promises;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.api.git.GitServiceClient;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.parts.EditorMultiPartStack;
import org.eclipse.che.ide.api.parts.EditorTab;
import org.eclipse.che.ide.api.resources.File;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.api.vcs.VcsStatus;
import org.eclipse.che.ide.api.vcs.VcsStatusProvider;
import org.eclipse.che.ide.part.explorer.project.ProjectExplorerPresenter;
import org.eclipse.che.ide.resource.Path;
import org.eclipse.che.ide.resources.impl.ResourceManager;
import org.eclipse.che.ide.resources.tree.ResourceNode;
import org.eclipse.che.ide.ui.smartTree.Tree;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

import static org.eclipse.che.ide.api.vcs.VcsStatus.ADDED;
import static org.eclipse.che.ide.api.vcs.VcsStatus.UNTRACKED;

/**
 * Receives git checkout notifications caught by server side VFS file watching system.
 * Support two type of notifications: git branch checkout and git revision checkout.
 * After a notification is received it is processed and passed to and instance of
 * {@link NotificationManager}.
 */
@Singleton
public class GitChangesHandler implements VcsStatusProvider {

    private final GitServiceClient                       serviceClient;
    private final AppContext                             appContext;
    private final ResourceManager.ResourceManagerFactory resourceManagerFactory;
    private final Provider<EditorAgent>                  editorAgentProvider;
    private final Provider<ProjectExplorerPresenter>     projectExplorerPresenterProvider;
    private final Provider<EditorMultiPartStack>         multiPartStackProvider;

    @Inject
    public GitChangesHandler(GitResources gitResources,
                             EventBus eventBus,
                             GitServiceClient serviceClient,
                             AppContext appContext,
                             ResourceManager.ResourceManagerFactory resourceManagerFactory,
                             RequestHandlerConfigurator configurator,
                             Provider<EditorAgent> editorAgentProvider,
                             Provider<ProjectExplorerPresenter> projectExplorerPresenterProvider,
                             Provider<EditorMultiPartStack> multiPartStackProvider) {
        this.serviceClient = serviceClient;
        this.appContext = appContext;
        this.resourceManagerFactory = resourceManagerFactory;
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
            .filter(node -> node instanceof ResourceNode &&
                            ((ResourceNode)node).getData().getLocation().equals(Path.valueOf(dto.getPath())))
            .forEach(node -> {
                ((ResourceNode)node).getData().asFile().setVcsStatus(VcsStatus.from(dto.getType().toString()));
                tree.refresh(node);
            });
        editorAgentProvider.get()
                           .getOpenedEditors()
                           .forEach(editor -> {
                               EditorTab tab = multiPartStackProvider.get().getTabByPart(editor);
                               String s = "dgsgd";
                           });
    }

    public void apply(String endpointId, Status dto) {
        Tree tree = projectExplorerPresenterProvider.get().getTree();
        tree.getNodeStorage()
            .getAll()
            .stream()
            .filter(node -> ((ResourceNode)node).getData() instanceof File)
            .forEach(node -> {
                File file = ((ResourceNode)node).getData().asFile();
                file.getVcsStatus();
                Path nodeLocation = ((ResourceNode)node).getData().getLocation();
                if (dto.getUntracked().contains(nodeLocation.removeFirstSegments(1).toString()) && file.getVcsStatus() != UNTRACKED) {
                    file.setVcsStatus(UNTRACKED);
                    tree.refresh(node);
                } else if (dto.getAdded().contains(nodeLocation.removeFirstSegments(1).toString()) && file.getVcsStatus() != ADDED) {
                    file.setVcsStatus(ADDED);
                    tree.refresh(node);
                } else if (file.getVcsStatus() == UNTRACKED) {
                    file.setVcsStatus(VcsStatus.NOT_MODIFIED);
                    tree.refresh(node);
                }
            });
    }

    @Override
    public String getVcs() {
        return "git";
    }

    @Override
    public void getVcsStatus(Path path) {
        Promise<Optional<File>> file = resourceManagerFactory.newResourceManager(appContext.getDevMachine()).getFile(path);
        file.then(arg -> {
            File file1 = arg.get();
            file1.getVcsStatus();
        });
    }
}
