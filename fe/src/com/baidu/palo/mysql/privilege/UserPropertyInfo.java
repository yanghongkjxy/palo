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

package com.baidu.palo.mysql.privilege;

import com.baidu.palo.common.Pair;
import com.baidu.palo.common.io.Text;
import com.baidu.palo.common.io.Writable;

import com.google.common.collect.Lists;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;

public class UserPropertyInfo implements Writable {

    private String user;
    private List<Pair<String, String>> properties = Lists.newArrayList();

    private UserPropertyInfo() {

    }

    public UserPropertyInfo(String user, List<Pair<String, String>> properties) {
        this.user = user;
        this.properties = properties;
    }

    public String getUser() {
        return user;
    }

    public List<Pair<String, String>> getProperties() {
        return properties;
    }

    public static UserPropertyInfo read(DataInput in) throws IOException {
        UserPropertyInfo info = new UserPropertyInfo();
        info.readFields(in);
        return info;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        Text.writeString(out, user);
        out.writeInt(properties.size());
        for (Pair<String, String> entry : properties) {
            Text.writeString(out, entry.first);
            Text.writeString(out, entry.second);
        }
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        user = Text.readString(in);
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            String key = Text.readString(in);
            String val = Text.readString(in);
            properties.add(Pair.create(key, val));
        }
    }
}
