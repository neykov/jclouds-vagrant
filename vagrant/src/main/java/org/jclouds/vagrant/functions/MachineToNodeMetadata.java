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
package org.jclouds.vagrant.functions;

import java.util.Map;
import java.util.Set;

import org.jclouds.collect.Memoized;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadata.Status;
import org.jclouds.compute.domain.NodeMetadataBuilder;
import org.jclouds.domain.Credentials;
import org.jclouds.domain.Location;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.vagrant.domain.VagrantNode;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import vagrant.api.domain.Machine;
import vagrant.api.domain.MachineState;
import vagrant.api.domain.SshConfig;

public class MachineToNodeMetadata implements Function<VagrantNode, NodeMetadata> {
    private final Function<MachineState, Status> toPortableNodeStatus;
    private final Supplier<Set<? extends Location>> locations;
    private final Map<String, Credentials> credentialStore;

    @Inject
    public MachineToNodeMetadata(Function<MachineState, Status> toPortableNodeStatus,
            @Memoized Supplier<Set<? extends Location>> locations,
            Map<String, Credentials> credentialStore) {
        this.toPortableNodeStatus = toPortableNodeStatus;
        this.locations = locations;
        this.credentialStore = credentialStore;
    }

    @Override
    public NodeMetadata apply(VagrantNode node) {
        Machine input = node.getMachine();
        NodeMetadataBuilder nodeMetadataBuilder = new NodeMetadataBuilder()
        .ids(input.getId())
        .name(input.getName())
        .group(input.getPath().getName())
//      .operatingSystem(null)
        .location(Iterables.getOnlyElement(locations.get()))
        .hostname(input.getName())
        .status(toPortableNodeStatus.apply(input.getStatus()))
        .loginPort(22)
        .privateAddresses(node.getNetworks())
        .publicAddresses(ImmutableList.<String>of())
        .hostname(node.getHostname());

        // Credentials could be changed by AdminAccess script, check store first
        Credentials credentials = credentialStore.get("node#" + input.getId());
        if (credentials != null) {
            nodeMetadataBuilder.credentials(LoginCredentials.fromCredentials(credentials));
        } else if (node.getSshConfig() != null) {
            SshConfig sshConfig = node.getSshConfig();
            nodeMetadataBuilder.credentials(LoginCredentials.builder().user(sshConfig.getUser()).privateKey(sshConfig.getIdentityFile()).build());
        }
        return nodeMetadataBuilder.build();
    }

}
