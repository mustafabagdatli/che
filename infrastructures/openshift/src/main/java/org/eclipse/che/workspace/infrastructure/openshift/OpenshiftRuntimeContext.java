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
package org.eclipse.che.workspace.infrastructure.openshift;

import com.google.inject.assistedinject.Assisted;

import org.eclipse.che.api.core.ValidationException;
import org.eclipse.che.api.core.model.workspace.config.Environment;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.installer.server.InstallerRegistry;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.InternalInfrastructureException;
import org.eclipse.che.api.workspace.server.spi.InternalRuntime;
import org.eclipse.che.api.workspace.server.spi.RuntimeContext;
import org.eclipse.che.api.workspace.server.spi.RuntimeInfrastructure;
import org.eclipse.che.workspace.infrastructure.openshift.environment.OpenshiftEnvironment;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriBuilderException;
import java.net.URI;

import static org.eclipse.che.api.workspace.server.OutputEndpoint.OUTPUT_WEBSOCKET_ENDPOINT_BASE;


/**
 * @author Sergii Leshchenko
 */
public class OpenshiftRuntimeContext extends RuntimeContext {
    private final OpenshiftEnvironment  openshiftEnvironment;
    private final RuntimeContextFactory runtimeContextFactory;
    private final String                apiEndpoint;

    @Inject
    public OpenshiftRuntimeContext(@Assisted Environment environment,
                                   @Assisted OpenshiftEnvironment openshiftEnvironment,
                                   @Assisted RuntimeIdentity identity,
                                   @Assisted RuntimeInfrastructure infrastructure,
                                   InstallerRegistry installerRegistry,
                                   RuntimeContextFactory runtimeContextFactory,
                                   @Named("che.api") String apiEndpoint) throws ValidationException,
                                                                                InfrastructureException {
        super(environment, identity, infrastructure, installerRegistry);
        this.runtimeContextFactory = runtimeContextFactory;
        this.openshiftEnvironment = openshiftEnvironment;
        this.apiEndpoint = apiEndpoint;
    }

    @Override
    public URI getOutputChannel() throws InfrastructureException {
        try {
            final URI apiURI = URI.create(apiEndpoint);
            return UriBuilder.fromUri(apiURI)
                             .scheme("https".equals(apiURI.getScheme()) ? "wss" : "ws")
                             .replacePath(apiURI.getPath().replace("/api", ""))
                             .path(OUTPUT_WEBSOCKET_ENDPOINT_BASE)
                             .build();
        } catch (UriBuilderException | IllegalArgumentException ex) {
            throw new InternalInfrastructureException("Failed to get the output channel because: " +
                                                      ex.getLocalizedMessage());
        }
    }

    @Override
    public InternalRuntime getRuntime() {
        return runtimeContextFactory.createRuntime(environment, openshiftEnvironment, identity, this);
    }
}
