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

package com.baidu.palo.common.proc;

import com.baidu.palo.common.AnalysisException;
import com.baidu.palo.qe.QeProcessorImpl;
import com.baidu.palo.qe.QueryStatisticsItem;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/*
 * show proc "/current_queries"
 */
public class CurrentQueryStatisticsProcDir implements ProcDirInterface {
    public static final ImmutableList<String> TITLE_NAMES = new ImmutableList.Builder<String>()
            .add("QueryId").add("Database").add("User").add("ExecTime").build();

    private static final int EXEC_TIME_INDEX = 3;

    @Override
    public boolean register(String name, ProcNodeInterface node) {
        return false;
    }

    @Override
    public ProcNodeInterface lookup(String name) throws AnalysisException {
        if (Strings.isNullOrEmpty(name)) {
            return null;
        }
        final Map<String, QueryStatisticsItem> statistic = QeProcessorImpl.INSTANCE.getQueryStatistics();
        final QueryStatisticsItem item = statistic.get(name);
        if (item == null) {
            throw new AnalysisException(name + " does't exist.");
        }
        return new CurrentQuerySqlProcDir(item);
    }

    @Override
    public ProcResult fetchResult() throws AnalysisException {
        final BaseProcResult result = new BaseProcResult();
        final Map<String, QueryStatisticsItem> statistic = QeProcessorImpl.INSTANCE.getQueryStatistics();
        result.setNames(TITLE_NAMES.asList());
        final List<List<String>> sortedRowData = Lists.newArrayList();
        for (QueryStatisticsItem item : statistic.values()) {
            final List<String> values = Lists.newArrayList();
            values.add(item.getQueryId());
            values.add(item.getDb());
            values.add(item.getUser());
            values.add(item.getQueryExecTime());
            sortedRowData.add(values);
        }
        // sort according to ExecTime
        sortedRowData.sort(new Comparator<List<String>>() {
            @Override
            public int compare(List<String> l1, List<String> l2) {
                final int execTime1 = Integer.valueOf(l1.get(EXEC_TIME_INDEX));
                final int execTime2 = Integer.valueOf(l2.get(EXEC_TIME_INDEX));
                return execTime1 <= execTime2 ? 1 : -1;
            }
        });
        result.setRows(sortedRowData);
        return result;
    }
}
