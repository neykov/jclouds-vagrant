/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jclouds.vagrant.domain;

import java.util.Collection;

import com.google.common.collect.ImmutableSet;

import vagrant.api.domain.Machine;
import vagrant.api.domain.MachineState;
import vagrant.api.domain.SshConfig;

public class VagrantNode {
    private final Machine machine;
    private SshConfig sshConfig;
    private Collection<String> networks = ImmutableSet.of();
    private MachineState machineState = MachineState.POWER_OFF;
    private String hostname;

    public VagrantNode(Machine machine) {
        this.machine = machine;
    }

    public void setMachineState(MachineState state) {
        this.machineState = state;
    }

    public void setSshConfig(SshConfig sshConfig) {
        this.sshConfig = sshConfig;
    }

    public void setNetworks(Collection<String> networks) {
        this.networks = networks;
    }

    public Machine getMachine() {
        return machine;
    }

    public SshConfig getSshConfig() {
        return sshConfig;
    }

    public Collection<String> getNetworks() {
        return networks;
    }

    public MachineState getMachineState() {
        return machineState;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

}
