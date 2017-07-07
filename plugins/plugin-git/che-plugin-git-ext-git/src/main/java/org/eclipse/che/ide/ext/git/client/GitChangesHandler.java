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

import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.core.jsonrpc.commons.RequestHandlerConfigurator;
import org.eclipse.che.api.git.shared.Status;
import org.eclipse.che.api.project.shared.dto.event.GitChangeEventDto;
import org.eclipse.che.ide.api.data.tree.HasAttributes;
import org.eclipse.che.ide.api.data.tree.Node;
import org.eclipse.che.ide.api.git.GitServiceClient;
import org.eclipse.che.ide.api.machine.events.WsAgentStateEvent;
import org.eclipse.che.ide.api.machine.events.WsAgentStateHandler;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.part.explorer.project.ProjectExplorerPresenter;
import org.eclipse.che.ide.resource.Path;
import org.eclipse.che.ide.resources.tree.ResourceNode;
import org.eclipse.che.ide.ui.smartTree.Tree;
import org.eclipse.che.ide.ui.smartTree.event.ExpandNodeEvent;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.eclipse.che.ide.api.machine.events.WsAgentStateEvent.TYPE;

/**
 * Receives git checkout notifications caught by server side VFS file watching system.
 * Support two type of notifications: git branch checkout and git revision checkout.
 * After a notification is received it is processed and passed to and instance of
 * {@link NotificationManager}.
 */
@Singleton
public class GitChangesHandler {

    private Tree tree;

    @Inject
    public GitChangesHandler(EventBus eventBus,
                             GitServiceClient serviceClient,
                             RequestHandlerConfigurator configurator,
                             Provider<ProjectExplorerPresenter> projectExplorerPresenterProvider) {

        eventBus.addHandler(TYPE, new WsAgentStateHandler() {
            @Override
            public void onWsAgentStarted(WsAgentStateEvent wsAgentStateEvent) {
                tree = projectExplorerPresenterProvider.get().getTree();
                tree.addExpandHandler(
                        event -> serviceClient.getStatus(((ResourceNode)event.getNode()).getData().getLocation().uptoSegment(1))
                                              .then(status -> {
                                                  List<String> changed = new ArrayList<>();
                                                  changed.addAll(status.getModified());
                                                  changed.addAll(status.getChanged());

                                                  tree.getAllChildNodes(Collections.singletonList(event.getNode()), false)
                                                      .forEach(node -> {
                                                          setColour((ResourceNode)node, changed, "CornflowerBlue");
                                                          setColour((ResourceNode)node, status.getUntracked(), "red");
                                                          setColour((ResourceNode)node, status.getAdded(), "green");
                                                      });
                                              }));

                tree.addNodeAddedHandler(event -> event.getNodes()
                                                       .forEach(node -> {
                                                           HasAttributes attributesNode = (HasAttributes)node;
                                                           Map<String, List<String>> map = attributesNode.getAttributes();
                                                           map.put("colours", Collections.singletonList("red"));
                                                           attributesNode.setAttributes(map);
                                                           tree.refresh(node);
                                                       }));
            }

            @Override
            public void onWsAgentStopped(WsAgentStateEvent event) {

            }
        });

        configureHandler(configurator);
    }

    private void setColour(ResourceNode node, List<String> statusFiles, String colour) {

        statusFiles.stream()
                   .filter(change -> node.getData()
                                         .getLocation()
                                         .removeFirstSegments(1)
                                         .equals(Path.valueOf(change)))
                   .findAny()
                   .ifPresent(change -> {
                       Map<String, List<String>> map = node.getAttributes();
                       map.put("colours", Collections.singletonList(colour));
                       node.setAttributes(map);
                       tree.refresh(node);
                   });
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
        tree.getNodeStorage()
            .getAll()
            .stream()
            .filter(node -> node instanceof ResourceNode &&
                            ((ResourceNode)node).getData().getLocation().equals(Path.valueOf(dto.getPath())))
            .forEach(node -> {
                HasAttributes attributesNode = (HasAttributes)node;
                Map<String, List<String>> map = attributesNode.getAttributes();
                switch (dto.getType()) {
                    case NEW:
                        map.put("colours", Collections.singletonList("LightGreen"));
                        attributesNode.setAttributes(map);
                        break;
                    case MODIFIED:
                        map.put("colours", Collections.singletonList("CornflowerBlue"));
                        attributesNode.setAttributes(map);
                        break;
                    case UNTRACKED:
                        map.put("colours", Collections.singletonList("LightCoral"));
                        attributesNode.setAttributes(map);
                        break;
                    case UNMODIFIED:
                        map.remove("colours");
                        attributesNode.setAttributes(map);
                        break;

                }
                tree.refresh(node);
            });
    }

    public void apply(String endpointId, Status dto) {
        tree.getNodeStorage()
            .getAll()
            .forEach(node -> {
                setColour((ResourceNode)node, dto.getUntracked(), "red");
                setColour((ResourceNode)node, dto.getAdded(), "green");
            });
    }
}
