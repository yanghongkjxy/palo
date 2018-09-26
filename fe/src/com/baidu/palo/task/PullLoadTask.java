// Copyright (c) 2017, Baidu.com, Inc. All Rights Reserved

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.baidu.palo.task;

import com.baidu.palo.analysis.BrokerDesc;
import com.baidu.palo.catalog.Database;
import com.baidu.palo.catalog.OlapTable;
import com.baidu.palo.common.Config;
import com.baidu.palo.common.InternalException;
import com.baidu.palo.common.Status;
import com.baidu.palo.load.BrokerFileGroup;
import com.baidu.palo.qe.Coordinator;
import com.baidu.palo.qe.QeProcessorImpl;
import com.baidu.palo.thrift.TBrokerFileStatus;
import com.baidu.palo.thrift.TQueryType;
import com.baidu.palo.thrift.TStatusCode;
import com.baidu.palo.thrift.TUniqueId;

import com.google.common.collect.Maps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// A pull load task is used to process one table of this pull load job.
public class PullLoadTask {
    private static final Logger LOG = LogManager.getLogger(PullLoadTask.class);
    // Input parameter
    public final long jobId;
    public final int taskId;
    public final Database db;
    public final OlapTable table;
    public final BrokerDesc brokerDesc;
    public final List<BrokerFileGroup> fileGroups;
    public final long jobDeadlineMs;

    // s
    private PullLoadTaskPlanner planner;

    // Useful things after executed
    private Map<String, Long> fileMap;
    private String trackingUrl;
    private Map<String, String> counters;
    private final long execMemLimit;

    // Runtime variables
    private enum State {
        RUNNING,
        FINISHED,
        FAILED,
        CANCELLED,
    }

    private TUniqueId executeId;
    private Coordinator curCoordinator;
    private State executeState = State.RUNNING;
    private Status executeStatus;
    private Thread curThread;

    public PullLoadTask(
            long jobId, int taskId,
            Database db, OlapTable table,
            BrokerDesc brokerDesc, List<BrokerFileGroup> fileGroups,
            long jobDeadlineMs, long execMemLimit) {
        this.jobId = jobId;
        this.taskId = taskId;
        this.db = db;
        this.table = table;
        this.brokerDesc = brokerDesc;
        this.fileGroups = fileGroups;
        this.jobDeadlineMs = jobDeadlineMs;
        this.execMemLimit = execMemLimit;
    }

    public void init(List<List<TBrokerFileStatus>> fileStatusList, int fileNum) throws InternalException {
        planner = new PullLoadTaskPlanner(this);
        planner.plan(fileStatusList, fileNum);
    }

    public Map<String, Long> getFileMap() {
        return fileMap;
    }

    public String getTrackingUrl() {
        return trackingUrl;
    }

    public Map<String, String> getCounters() {
        return counters;
    }

    private long getLeftTimeMs() {
        if (jobDeadlineMs <= 0) {
            return Config.pull_load_task_default_timeout_second * 1000;
        }
        return jobDeadlineMs - System.currentTimeMillis();
    }

    public synchronized void cancel() {
        if (curCoordinator != null) {
            curCoordinator.cancel();
        }
    }

    public synchronized boolean isFinished() {
        return executeState == State.FINISHED;
    }

    public Status getExecuteStatus() {
        return executeStatus;
    }

    public synchronized void onCancelled() {
        if (executeState == State.RUNNING) {
            executeState = State.CANCELLED;
            executeStatus = Status.CANCELLED;
        }
    }

    public synchronized void onFinished(Map<String, Long> fileMap,
                                        Map<String, String> counters,
                                        String trackingUrl) {
        if (executeState == State.RUNNING) {
            executeState = State.FINISHED;

            executeStatus = Status.OK;
            this.fileMap = fileMap;
            this.counters = counters;
            this.trackingUrl = trackingUrl;
        }
    }

    public synchronized void onFailed(TUniqueId id, Status failStatus) {
        if (executeState == State.RUNNING) {
            if (!executeId.equals(id)) {
                return;
            }
            executeState = State.FAILED;
            executeStatus = failStatus;
        }
    }

    public synchronized void onFailed(Status failStatus) {
        if (executeState == State.RUNNING) {
            executeState = State.FAILED;
            executeStatus = failStatus;
        }
    }

    private void actualExecute() {
        int waitSecond = (int) (getLeftTimeMs() / 1000);
        if (waitSecond <= 0) {
            onCancelled();
            return;
        }

        // TODO(zc): to refine coordinator
        try {
            curCoordinator.exec();
        } catch (Exception e) {
            onFailed(executeId, new Status(TStatusCode.INTERNAL_ERROR, "Coordinator execute failed."));
        }
        if (curCoordinator.join(waitSecond)) {
            Status status = curCoordinator.getExecStatus();
            if (status.ok()) {
                Map<String, Long> resultFileMap = Maps.newHashMap();
                for (String file : curCoordinator.getDeltaUrls()) {
                    resultFileMap.put(file, -1L);
                }
                onFinished(resultFileMap, curCoordinator.getLoadCounters(), curCoordinator.getTrackingUrl());
            } else {
                onFailed(executeId, status);
            }
        } else {
            onCancelled();
        }
    }

    public void executeOnce() throws InternalException {
        synchronized (this) {
            if (curThread != null) {
                throw new InternalException("Task already executing.");
            }
            curThread = Thread.currentThread();
            executeState = State.RUNNING;
            executeStatus = Status.OK;

            // New one query id,
            UUID uuid = UUID.randomUUID();
            executeId = new TUniqueId(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
            curCoordinator = new Coordinator(executeId, planner.getDescTable(),
                    planner.getFragments(), planner.getScanNodes(), db.getClusterName());
            curCoordinator.setQueryType(TQueryType.LOAD);
            curCoordinator.setExecMemoryLimit(execMemLimit);
            curCoordinator.setTimeout((int) (getLeftTimeMs() / 1000));
        }

        boolean needUnregister = false;
        try {
            QeProcessorImpl.INSTANCE
                     .registerQuery(executeId, curCoordinator);
            actualExecute();
            needUnregister = true;
        } catch (InternalException e) {
            onFailed(executeId, new Status(TStatusCode.INTERNAL_ERROR, e.getMessage()));
        } finally {
            if (needUnregister) {
                QeProcessorImpl.INSTANCE.unregisterQuery(executeId);
            }
            synchronized (this) {
                curThread = null;
                curCoordinator = null;
            }
        }
    }
}
