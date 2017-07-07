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
import org.eclipse.che.api.project.server.ProjectRegistry;
import org.eclipse.che.api.project.server.RegisteredProject;
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


public class GitIndexChangedDetector {
    private static final Logger LOG = getLogger(GitIndexChangedDetector.class);

    private static final String GIT_DIR         = ".git";
    private static final String INDEX_FILE      = "index";
    private static final String INCOMING_METHOD = "track:git-index";
    private static final String OUTGOING_METHOD = "event:git-index";
    private final RequestTransmitter   transmitter;
    private final FileWatcherManager   manager;
    private final ProjectRegistry      projectRegistry;
    private final GitConnectionFactory gitConnectionFactory;

    private final Set<String> endpointIds = newConcurrentHashSet();

    private int id;

    @Inject
    public GitIndexChangedDetector(RequestTransmitter transmitter,
                                   FileWatcherManager manager,
                                   ProjectRegistry projectRegistry,
                                   GitConnectionFactory gitConnectionFactory) {
        this.transmitter = transmitter;
        this.manager = manager;
        this.projectRegistry = projectRegistry;
        this.gitConnectionFactory = gitConnectionFactory;
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
        return it -> !isDirectory(it) &&
                     INDEX_FILE.equals(it.getFileName().toString()) &&
                     GIT_DIR.equals(it.getParent().getFileName().toString());
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
        return id -> {
            final RegisteredProject project = projectRegistry.getProject(path.substring(1, path.substring(1).indexOf("/") + 1));
            String projectPath = project.getBaseFolder().getVirtualFile().toIoFile().getAbsolutePath();
            try {
                Status status = gitConnectionFactory.getConnection(projectPath).status(StatusFormat.SHORT);
                Status statusDto = newDto(Status.class);
                statusDto.setAdded(status.getAdded());
                statusDto.setUntracked(status.getUntracked());
                statusDto.setChanged(status.getChanged());
                statusDto.setModified(status.getModified());
                transmitter.newRequest()
                           .endpointId(id)
                           .methodName(OUTGOING_METHOD)
                           .paramsAsDto(statusDto)
                           .sendAndSkipResult();
            } catch (GitException e) {
                LOG.error(e.getMessage());
            }
        };
    }
}
