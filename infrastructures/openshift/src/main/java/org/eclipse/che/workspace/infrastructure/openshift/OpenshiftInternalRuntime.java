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

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;

import com.google.inject.assistedinject.Assisted;

import org.eclipse.che.api.core.model.workspace.runtime.Machine;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.core.model.workspace.runtime.ServerStatus;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.installer.server.InstallerRegistry;
import org.eclipse.che.api.installer.server.exception.InstallerException;
import org.eclipse.che.api.installer.server.model.impl.InstallerImpl;
import org.eclipse.che.api.workspace.server.URLRewriter;
import org.eclipse.che.api.workspace.server.model.impl.MachineImpl;
import org.eclipse.che.api.workspace.server.model.impl.ServerImpl;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.InternalInfrastructureException;
import org.eclipse.che.api.workspace.server.spi.InternalRuntime;
import org.eclipse.che.workspace.infrastructure.openshift.bootstrapper.OpenshiftBootstrapperFactory;
import org.eclipse.che.workspace.infrastructure.openshift.environment.OpenshiftEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

/**
 * @author Sergii Leshchenko
 */
public class OpenshiftInternalRuntime extends InternalRuntime<OpenshiftRuntimeContext> {
    private static final String OPENSHIFT_POD_STATUS_RUNNING = "Running";

    private static final Logger LOG = LoggerFactory.getLogger(OpenshiftInternalRuntime.class);

    private final RuntimeIdentity              identity;
    private final OpenshiftEnvironment         kubernetesEnvironment;
    private final OpenshiftClientFactory       clientFactory;
    private final InstallerRegistry            installerRegistry;
    private final EventService                 eventService;
    private final OpenshiftBootstrapperFactory openshiftBootstrapperFactory;

    @Inject
    public OpenshiftInternalRuntime(@Assisted OpenshiftRuntimeContext context,
                                    @Assisted RuntimeIdentity identity,
                                    @Assisted OpenshiftEnvironment openshiftEnvironment,
                                    URLRewriter urlRewriter,
                                    OpenshiftClientFactory clientFactory,
                                    InstallerRegistry installerRegistry,
                                    EventService eventService,
                                    OpenshiftBootstrapperFactory openshiftBootstrapperFactory) {
        super(context, urlRewriter);
        this.identity = identity;
        this.kubernetesEnvironment = openshiftEnvironment;
        this.clientFactory = clientFactory;
        this.installerRegistry = installerRegistry;
        this.eventService = eventService;
        this.openshiftBootstrapperFactory = openshiftBootstrapperFactory;
    }

    @Override
    protected void internalStart(Map<String, String> startOptions) throws InfrastructureException {
        try (OpenShiftClient client = clientFactory.create()) {
            String namespace = identity.getWorkspaceId();
            LOG.info("Trying to resolve project for workspace " + identity.getWorkspaceId());
            try {
                Project project = client.projects().withName(namespace).get();

                //TODO clean up project instead it recreation
                client.projects().delete(project);
                //Projects creation immediately after its removing doesn't work TODO Fix it
                client.projectrequests()
                      .createNew()
                      .withNewMetadata()
                      .withName(namespace)
                      .endMetadata()
                      .done();
            } catch (KubernetesClientException e) {
                if (e.getCode() == 403) {
                    // project is foreign or doesn't exist

                    //try to create project
                    client.projectrequests()
                          .createNew()
                          .withNewMetadata()
                          .withName(namespace)
                          .endMetadata()
                          .done();
                } else {
                    throw new InfrastructureException(e.getMessage(), e);
                }
            }

            LOG.info("Created new project for workspace " + identity.getWorkspaceId());

            // TODO Add Persistent Volumes claims for projects

            LOG.info("Creating pods from environment");
            for (Pod pod : kubernetesEnvironment.getPods().values()) {
                kubernetesEnvironment.addPod(client.pods()
                                                   .inNamespace(namespace)
                                                   .create(pod));
            }

            LOG.info("Creating services from environment");
            for (Service service : kubernetesEnvironment.getServices().values()) {
                kubernetesEnvironment.addService(client.services()
                                                       .inNamespace(namespace)
                                                       .create(service));
            }

            LOG.info("Creating routes from environment");
            for (Route route : kubernetesEnvironment.getRoutes().values()) {
                kubernetesEnvironment.addRoute(client.routes()
                                                     .inNamespace(namespace)
                                                     .create(route));
            }

            LOG.info("Waiting until pods created by deployment configs become available and bootstrapping it");

            for (Pod pod : kubernetesEnvironment.getPods().values()) {
                Pod runningPod = waitPod(pod);
                kubernetesEnvironment.addPod(runningPod);
                try {
                    for (ContainerStatus cointainer : runningPod.getStatus().getContainerStatuses()) {
                        //TODO Fix
                        List<InstallerImpl> installers =
                                installerRegistry.getOrderedInstallers(asList("org.eclipse.che.ws-agent",
                                                                              "org.eclipse.che.terminal"))
                                                 .stream()
                                                 .map(InstallerImpl::new)
                                                 .collect(toList());
                        openshiftBootstrapperFactory.create(pod.getMetadata().getName(),
                                                            identity,
                                                            installers,
                                                            pod,
                                                            cointainer.getName())
                                                    .bootstrap();
                    }
                } catch (InstallerException e) {
                    throw new InfrastructureException(e.getMessage(), e);
                }
            }
        } catch (InfrastructureException e) {
            //Just for logging TODO Remove later
            LOG.error("Error starting " + e.getMessage(), e);
        } catch (RuntimeException e) {
            LOG.error("Error starting " + e.getMessage(), e);
            throw new InfrastructureException(e.getMessage(), e);
        }

        LOG.info("Openshift Runtime for workspace " + identity.getWorkspaceId() + " started");
    }

    @Override
    public Map<String, ? extends Machine> getInternalMachines() {
        //TODO Believe it is not my code :) Rework resolving servers
        Map<String, MachineImpl> machines = new HashMap<>();
        String workspaceId = identity.getWorkspaceId();
        try (OpenShiftClient client = clientFactory.create()) {

            List<Pod> pods = client.pods().inNamespace(workspaceId).list().getItems();

            for (Pod pod : pods) {
                for (ContainerStatus status : pod.getStatus().getContainerStatuses()) {
                    machines.put(pod.getMetadata().getName() + "/" + status.getName(),
                                 new MachineImpl(new HashMap<>(), new HashMap<>()));
                }
            }

            List<Route> routes = client.routes().inNamespace(workspaceId).list().getItems();
            List<Service> services = client.services().inNamespace(workspaceId).list().getItems();

            for (Route route : routes) {
                String serviceName = route.getSpec().getTo().getName();

                //TODO Implement fetching protocol from it
                Service service = services.stream()
                                          .filter(s -> s.getMetadata().getName().equals(serviceName))
                                          .findAny()
                                          .get();

                List<Pod> servicesPods = client.pods()
                                               .inNamespace(workspaceId)
                                               .withLabels(service.getSpec().getSelector())
                                               .list()
                                               .getItems();

                for (Pod servicesPod : servicesPods) {
                    for (Container container : servicesPod.getSpec().getContainers()) {
                        for (ContainerPort containerPort : container.getPorts()) {
                            for (ServicePort servicePort : service.getSpec().getPorts()) {
                                if (containerPort.getContainerPort().equals(servicePort.getPort())) {
                                    String portName = route.getSpec().getPort().getTargetPort().getStrVal();

                                    machines.get(servicesPod.getMetadata().getName() + "/" + container.getName())
                                            .getServers()
                                            .put(portName,
                                                 new ServerImpl("http://" + route.getSpec().getHost(),
                                                                ServerStatus.UNKNOWN));
                                }
                            }

                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        return machines;
    }

    @Override
    protected void internalStop(Map<String, String> stopOptions) throws InfrastructureException {
        try (OpenShiftClient client = clientFactory.create()) {
            LOG.info("Stopping workspace " + identity.getWorkspaceId());
            try {
                Project project = client.projects()
                                        .withName(identity.getWorkspaceId())
                                        .get();
                //TODO Add cleaning of project instead of removing all project to save persistant volume
                client.projects()
                      .delete(project);
                LOG.info("Workspace " + identity.getWorkspaceId() + " stopped.");
            } catch (KubernetesClientException e) {
                //projects doesn't exist or is foreign
                LOG.info("Workspace " + identity.getWorkspaceId() + " was already stopped.");
            }
        }
    }

    @Override
    public Map<String, String> getProperties() {
        return Collections.emptyMap();
    }

    private Pod waitPod(Pod pod) throws InfrastructureException {
        LOG.info("Waiting POD " + pod.getMetadata().getName());

        for (int i = 0; i < 240; i++) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            try (OpenShiftClient client = clientFactory.create()) {
                Pod actualPod = client.pods()
                                      .inNamespace(pod.getMetadata().getNamespace())
                                      .withName(pod.getMetadata().getName())
                                      .get();

                if (actualPod == null) {
                    throw new InternalInfrastructureException("Can't find creted pod");
                }
                String status = actualPod.getStatus().getPhase();
                LOG.info("POD " + actualPod.getMetadata().getName() + " has status " + status);
                if (OPENSHIFT_POD_STATUS_RUNNING.equals(status)) {
                    return actualPod;
                }
            }
        }

        throw new InfrastructureException("Reached timeout waiting of pod for " + pod.getMetadata().getName());
    }
}
