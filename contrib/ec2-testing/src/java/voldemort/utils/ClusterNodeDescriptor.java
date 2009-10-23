/*
 * Copyright 2009 LinkedIn, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package voldemort.utils;

import java.util.List;

public class ClusterNodeDescriptor {

    private String hostName;

    private int id;

    private List<Integer> partitions;

    public ClusterNodeDescriptor(String hostName, int id, List<Integer> partitions) {
        this.hostName = hostName;
        this.id = id;
        this.partitions = partitions;
    }

    public String getHostName() {
        return hostName;
    }

    public int getId() {
        return id;
    }

    public List<Integer> getPartitions() {
        return partitions;
    }

}
