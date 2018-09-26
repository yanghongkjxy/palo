// Copyright (c) 2018, Baidu.com, Inc. All Rights Reserved

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

package com.baidu.palo.qe;

import com.baidu.palo.common.InternalException;
import com.baidu.palo.thrift.TReportExecStatusParams;
import com.baidu.palo.thrift.TReportExecStatusResult;
import com.baidu.palo.thrift.TUniqueId;

import java.util.Map;

public interface QeProcessor {

    TReportExecStatusResult reportExecStatus(TReportExecStatusParams params);

    void registerQuery(TUniqueId queryId, Coordinator coord) throws InternalException;

    void registerQuery(TUniqueId queryId, QeProcessorImpl.QueryInfo info) throws InternalException;

    void unregisterQuery(TUniqueId queryId);

    Map<String, QueryStatisticsItem> getQueryStatistics();
}
