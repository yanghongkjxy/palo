// Modifications copyright (C) 2017, Baidu.com, Inc.
// Copyright 2017 The Apache Software Foundation

// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.baidu.palo.analysis;

import com.baidu.palo.catalog.AccessPrivilege;
import com.baidu.palo.catalog.Catalog;
import com.baidu.palo.cluster.ClusterNamespace;
import com.baidu.palo.common.AnalysisException;
import com.baidu.palo.common.ErrorCode;
import com.baidu.palo.common.ErrorReport;
import com.baidu.palo.common.FeNameFormat;
import com.baidu.palo.common.InternalException;
import com.baidu.palo.mysql.privilege.PaloPrivilege;
import com.baidu.palo.mysql.privilege.PrivBitSet;
import com.baidu.palo.mysql.privilege.PrivPredicate;
import com.baidu.palo.qe.ConnectContext;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;

import java.util.List;

// GRANT STMT
// grant privilege to some user, this is an administrator operation.
//
// GRANT privilege [, privilege] ON db.tbl TO user [ROLE 'role'];
public class GrantStmt extends DdlStmt {
    private UserIdentity userIdent;
    private String role;
    private TablePattern tblPattern;
    private List<PaloPrivilege> privileges;

    public GrantStmt(UserIdentity userIdent, String role, TablePattern tblPattern, List<AccessPrivilege> privileges) {
        this.userIdent = userIdent;
        this.role = role;
        this.tblPattern = tblPattern;
        PrivBitSet privs = PrivBitSet.of();
        for (AccessPrivilege accessPrivilege : privileges) {
            privs.or(accessPrivilege.toPaloPrivilege());
        }
        this.privileges = privs.toPrivilegeList();
    }

    public UserIdentity getUserIdent() {
        return userIdent;
    }

    public TablePattern getTblPattern() {
        return tblPattern;
    }

    public boolean hasRole() {
        return !Strings.isNullOrEmpty(role);
    }

    public String getQualifiedRole() {
        return role;
    }

    public List<PaloPrivilege> getPrivileges() {
        return privileges;
    }

    @Override
    public void analyze(Analyzer analyzer) throws AnalysisException, InternalException {
        super.analyze(analyzer);
        if (userIdent != null) {
            userIdent.analyze(analyzer.getClusterName());
        } else {
            FeNameFormat.checkUserName(role);
        }

        tblPattern.analyze(analyzer.getClusterName());

        if (privileges == null || privileges.isEmpty()) {
            throw new AnalysisException("No privileges in grant statement.");
        }

        if (role != null) {
            FeNameFormat.checkRoleName(role, false /* can not be superuser */);
            role = ClusterNamespace.getFullName(analyzer.getClusterName(), role);
        }

        if (!Catalog.getCurrentCatalog().getAuth().checkGlobalPriv(ConnectContext.get(), PrivPredicate.GRANT)) {
            ErrorReport.reportAnalysisException(ErrorCode.ERR_SPECIFIC_ACCESS_DENIED_ERROR,
                                                "GRANT");
        }
    }

    @Override
    public String toSql() {
        StringBuilder sb = new StringBuilder();
        sb.append("GRANT ").append(Joiner.on(", ").join(privileges));
        sb.append(" ON ").append(tblPattern).append(" TO ");
        if (!Strings.isNullOrEmpty(role)) {
            sb.append(" ROLE '").append(role).append("'");
        } else {
            sb.append(userIdent);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toSql();
    }
}
