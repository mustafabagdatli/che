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
package org.eclipse.che.api.workspace.server.bootstrap;

import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.notification.EventSubscriber;
import org.eclipse.che.api.workspace.shared.dto.event.BootstrapperStatusEvent;
import org.eclipse.che.api.workspace.shared.dto.event.InstallerLogEvent;
import org.eclipse.che.api.workspace.shared.dto.event.InstallerStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * It is needed only for debugging.
 * //TODO Remove before merge.
 *
 * @author Sergii Leshchenko
 */
public class BootstrapperLogger {
    private final static Logger LOG = LoggerFactory.getLogger(BootstrapperLogger.class);

    @Inject
    public BootstrapperLogger(EventService eventService) {
        eventService.subscribe(new EventSubscriber<InstallerLogEvent>() {
            @Override
            public void onEvent(InstallerLogEvent event) {
                LOG.info("{} Installer {}#{} {}",
                         event.getRuntimeId().getWorkspaceId(),
                         toSimpleName(event.getInstaller()),
                         event.getStream(),
                         event.getText());
            }
        }, InstallerLogEvent.class);

        eventService.subscribe(new EventSubscriber<BootstrapperStatusEvent>() {
            @Override
            public void onEvent(BootstrapperStatusEvent event) {
                LOG.info("{} => Boostrapper Status {}",
                         event.getRuntimeId().getWorkspaceId(),
                         event.getStatus());
            }
        }, BootstrapperStatusEvent.class);

        eventService.subscribe(new EventSubscriber<InstallerStatusEvent>() {
            @Override
            public void onEvent(InstallerStatusEvent event) {
                LOG.info("{} => Installer {} status {}",
                         event.getRuntimeId().getWorkspaceId(),
                         toSimpleName(event.getInstaller()),
                         event.getStatus());
            }
        }, InstallerStatusEvent.class);
    }

    private String toSimpleName(String installerName) {
        String[] split = installerName.split("\\.");
        return split[split.length - 1];
    }
}
