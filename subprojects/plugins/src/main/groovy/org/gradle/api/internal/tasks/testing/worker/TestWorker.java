/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.testing.worker;

import org.gradle.api.Action;
import org.gradle.api.internal.project.DefaultServiceRegistry;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.listener.ContextClassLoaderProxy;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.process.internal.WorkerProcessContext;
import org.gradle.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.concurrent.CountDownLatch;

public class TestWorker implements Action<WorkerProcessContext>, RemoteTestClassProcessor, Serializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestWorker.class);
    public static final String WORKER_ID_SYS_PROPERTY = "org.gradle.test.worker";
    private final WorkerTestClassProcessorFactory factory;
    private CountDownLatch completed;
    private TestClassProcessor processor;
    private TestResultProcessor resultProcessor;
    private StoppableExecutor testExecutor;

    public TestWorker(WorkerTestClassProcessorFactory factory) {
        this.factory = factory;
    }

    public void execute(WorkerProcessContext workerProcessContext) {
        LOGGER.info("{} executing tests.", workerProcessContext.getDisplayName());

        testExecutor = new DefaultExecutorFactory().create(workerProcessContext.getDisplayName() + " test executor"); 
        completed = new CountDownLatch(1);

        System.setProperty(WORKER_ID_SYS_PROPERTY, workerProcessContext.getWorkerId().toString());
        
        ObjectConnection serverConnection = workerProcessContext.getServerConnection();

        IdGenerator<Object> idGenerator = new CompositeIdGenerator(workerProcessContext.getWorkerId(),
                new LongIdGenerator());

        DefaultServiceRegistry testServices = new DefaultServiceRegistry();
        testServices.add(IdGenerator.class, idGenerator);
        TestClassProcessor targetProcessor = factory.create(testServices);

        targetProcessor = new WorkerTestClassProcessor(targetProcessor, idGenerator.generateId(),
                workerProcessContext.getDisplayName(), new TrueTimeProvider());
        ContextClassLoaderProxy<TestClassProcessor> proxy = new ContextClassLoaderProxy<TestClassProcessor>(
                TestClassProcessor.class, targetProcessor, workerProcessContext.getApplicationClassLoader());
        processor = proxy.getSource();

        this.resultProcessor = serverConnection.addOutgoing(TestResultProcessor.class);

        serverConnection.addIncoming(RemoteTestClassProcessor.class, this);

        try {
            completed.await();
        } catch (InterruptedException e) {
            throw new UncheckedException(e);
        }
        LOGGER.info("{} finished executing tests.", workerProcessContext.getDisplayName());
    }

    public void startProcessing() {
        processor.startProcessing(resultProcessor);
    }

    static private class ExceptionHolder {
        public Throwable thrown;
    }
    
    public void processTestClass(final TestClassRunInfo testClass) {
        final CountDownLatch executionFinishedLatch = new CountDownLatch(1);
        final ExceptionHolder exceptionHolder = new ExceptionHolder();
        
        // Execute the tests in a separate thread to protect against the tests affecting
        // our current thread (which is reused for future messages)
        // http://issues.gradle.org/browse/GRADLE-1948
        testExecutor.execute(new Runnable() {
            public void run() {
                try {
                    processor.processTestClass(testClass);
                } catch (Throwable e) {
                    exceptionHolder.thrown = e;
                } finally {
                    executionFinishedLatch.countDown();
                }
            }
        });
        
        try {
            executionFinishedLatch.await();
        } catch (InterruptedException e) {
            throw UncheckedException.asUncheckedException(e);
        }
        
        if (exceptionHolder.thrown != null) {
            throw UncheckedException.asUncheckedException(exceptionHolder.thrown);
        }
    }

    public void stop() {
        try {
            processor.stop();
        } finally {
            completed.countDown();
        }
    }
}
