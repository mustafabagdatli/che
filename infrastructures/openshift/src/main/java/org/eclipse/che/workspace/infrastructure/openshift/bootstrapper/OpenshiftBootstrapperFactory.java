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
package org.eclipse.che.workspace.infrastructure.openshift.bootstrapper;

import io.fabric8.kubernetes.api.model.Pod;

import com.google.inject.assistedinject.Assisted;

import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.installer.server.model.impl.InstallerImpl;

import javax.inject.Named;
import java.util.List;

/**
 * @author Sergii Leshchenko
 */
public interface OpenshiftBootstrapperFactory {
    OpenshiftBootstrapper create(@Assisted String machineName,
                                 @Assisted RuntimeIdentity runtimeIdentity,
                                 @Assisted List<InstallerImpl> agents,
                                 @Assisted Pod pod,
                                 @Assisted("containerName") String containerName);
}
