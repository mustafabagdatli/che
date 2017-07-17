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
package org.eclipse.che.workspace.infrastructure.openshift.exec;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Callback;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.utils.InputStreamPumper;
import io.fabric8.openshift.client.OpenShiftClient;
import okhttp3.Response;

import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.workspace.infrastructure.openshift.OpenshiftClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Sergii Leshchenko
 */
public class PodExecer {
    private static final Logger LOG = LoggerFactory.getLogger(PodExecer.class);

    private final OpenshiftClientFactory clientFactory;
    private final Pod                    pod;
    private final String                 containerId;

    @Inject
    public PodExecer(OpenshiftClientFactory clientFactory, Pod pod, String containerId) {
        this.clientFactory = clientFactory;
        this.pod = pod;
        this.containerId = containerId;
    }

    public void exec(String... command) throws InfrastructureException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        OpenShiftClient openShiftClient = clientFactory.create();

        //TODO Add using watchdog instead of streaming logging
//        ExecWatchdog watchdog = new ExecWatchdog(command);
        try (ExecWatch watch = openShiftClient.pods()
                                              .inNamespace(pod.getMetadata().getNamespace())
                                              .withName(pod.getMetadata().getName())
                                              .inContainer(containerId)
                                              .redirectingOutput()
                                              .redirectingError()
                                              //TODO Investigate why redirection output and listener doesn't work together
//                                              .usingListener(watchdog)
                                              .exec(encode(command));
             InputStreamPumper outputPump = new InputStreamPumper(watch.getOutput(),
                                                                  new SystemOutCallback(command));
             InputStreamPumper errorPump = new InputStreamPumper(watch.getError(),
                                                                 new SystemOutCallback(command))
        ) {
            Future<?> outFuture = executor.submit(outputPump);
            Future<?> errFuture = executor.submit(errorPump);
            // Short-term worksaround; the Futures above seem to never finish.
            Thread.sleep(2500);

//            try {
//                watchdog.wait(5, TimeUnit.MINUTES);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                throw new InfrastructureException(e.getMessage(), e);
//            }

        } catch (KubernetesClientException e) {
            throw new InfrastructureException(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
            openShiftClient.close();
        }
    }

    private String[] encode(String[] toEncode) throws InfrastructureException {
        String[] encoded = new String[toEncode.length];
        for (int i = 0; i < toEncode.length; i++) {
            try {
                encoded[i] = URLEncoder.encode(toEncode[i], "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new InfrastructureException(e.getMessage(), e);
            }
        }
        return encoded;
    }

    private class SystemOutCallback implements Callback<byte[]> {
        String[] command;

        public SystemOutCallback(String[] command) {
            this.command = command;
        }

        @Override
        public void call(byte[] data) {
//            LOG.info("Command " + Arrays.toString(command) + " out: " + new String(data));
            System.out.print("Command " + Arrays.toString(command) + " out: " + new String(data));
        }
    }

    private class ExecWatchdog implements ExecListener {
        private final CountDownLatch latch;
        private final String[]       command;

        private ExecWatchdog(String[] command) {
            this.latch = new CountDownLatch(1);
            this.command = command;
        }

        @Override
        public void onOpen(Response response) {
            LOG.info("Started executing " + Arrays.toString(command));
        }

        @Override
        public void onFailure(Throwable t, Response response) {
            LOG.info("Failed executing " + Arrays.toString(command));
            latch.countDown();
        }

        @Override
        public void onClose(int code, String reason) {
            LOG.info("Done executing " + Arrays.toString(command));
            latch.countDown();
        }

        public void wait(long timeout, TimeUnit timeUnit) throws InterruptedException {
            latch.await(timeout, timeUnit);
        }
    }
}
