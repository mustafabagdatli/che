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

import org.eclipse.che.api.core.jsonrpc.commons.RequestHandlerConfigurator;
import org.eclipse.che.api.core.jsonrpc.commons.RequestTransmitter;
import org.eclipse.che.api.git.exception.GitException;
import org.eclipse.che.api.git.shared.Status;
import org.eclipse.che.api.git.shared.StatusFormat;
import org.eclipse.che.api.project.shared.dto.event.GitChangeEventDto;
import org.eclipse.che.api.vfs.watcher.FileWatcherManager;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.nio.file.PathMatcher;
import java.util.Set;
import java.util.function.Consumer;

import static com.google.common.collect.Sets.newConcurrentHashSet;
import static java.nio.file.Files.isDirectory;
import static org.eclipse.che.api.vfs.watcher.FileWatcherManager.EMPTY_CONSUMER;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.slf4j.LoggerFactory.getLogger;


public class GitChangesDetector {
    private static final Logger LOG = getLogger(GitChangesDetector.class);

    private static final String INCOMING_METHOD = "track:git-change";
    private static final String OUTGOING_METHOD = "event:git-change";

    private final RequestTransmitter transmitter;
    private final FileWatcherManager manager;
    private final GitConnection      gitConnection;

    private final Set<String> endpointIds = newConcurrentHashSet();

    private int id;

    @Inject
    public GitChangesDetector(RequestTransmitter transmitter, FileWatcherManager manager, GitConnection gitConnection) {
        this.transmitter = transmitter;
        this.manager = manager;
        this.gitConnection = gitConnection;
    }

    @Inject
    public void configureHandler(RequestHandlerConfigurator configurator) {
        configurator.newConfiguration()
                    .methodName(INCOMING_METHOD)
                    .noParams()
                    .noResult()
                    .withConsumer(endpointIds::add);
    }

    @PostConstruct
    public void startWatcher() {
        id = manager.registerByMatcher(matcher(), createConsumer(), modifyConsumer(), deleteConsumer());
    }

    @PreDestroy
    public void stopWatcher() {
        manager.unRegisterByMatcher(id);
    }


    private PathMatcher matcher() {
        return it -> !isDirectory(it);
    }

    private Consumer<String> createConsumer() {
        return fsEventConsumer();
    }

    private Consumer<String> modifyConsumer() {
        return fsEventConsumer();
    }

    private Consumer<String> deleteConsumer() {
        return EMPTY_CONSUMER;
    }

    private Consumer<String> fsEventConsumer() {
        return it -> endpointIds.forEach(transmitConsumer(it));
    }

    private Consumer<String> transmitConsumer(String path) {
        try {
            Status status = gitConnection.status(StatusFormat.SHORT);

            return new Consumer<String>() {
                @Override
                public void accept(String id) {
                    GitChangeEventDto.Type type;

                    if (status.getModified()
                              .stream()
                              .anyMatch(modified -> modified.equals(path.substring(path.indexOf("/") + 1)))) {
                        type = GitChangeEventDto.Type.MODIFIED;
                    } else if (status.getChanged()
                                     .stream()
                                     .anyMatch(modified -> modified.equals(path.substring(path.indexOf("/") + 1)))) {
                        type = GitChangeEventDto.Type.MODIFIED;
                    } else if (status.getAdded()
                                     .stream()
                                     .anyMatch(modified -> modified.equals(path.substring(path.indexOf("/") + 1)))) {
                        type = GitChangeEventDto.Type.NEW;
                    } else if (status.getUntracked()
                                     .stream()
                                     .anyMatch(modified -> modified.equals(path.substring(path.indexOf("/") + 1)))) {
                        type = GitChangeEventDto.Type.UNTRACKED;
                    } else {
                        type = GitChangeEventDto.Type.UNMODIFIED;
                    }

                    transmitter.newRequest()
                               .endpointId(id)
                               .methodName(OUTGOING_METHOD)
                               .paramsAsDto(newDto(GitChangeEventDto.class).withPath(path).withType(type))
                               .sendAndSkipResult();
                }
            };
        } catch (GitException e) {
            LOG.error(e.getMessage());
            return null;
        }

    }
}
