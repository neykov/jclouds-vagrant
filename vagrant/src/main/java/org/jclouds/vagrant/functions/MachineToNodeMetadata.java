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

import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadata.Status;
import org.jclouds.compute.domain.NodeMetadataBuilder;
import org.jclouds.domain.LocationBuilder;
import org.jclouds.domain.LocationScope;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.vagrant.domain.VagrantNode;

import vagrant.Vagrant;
import vagrant.api.VagrantApi;
import vagrant.api.domain.Machine;
import vagrant.api.domain.MachineState;
import vagrant.api.domain.SshConfig;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class MachineToNodeMetadata implements Function<VagrantNode, NodeMetadata> {
    private static final Pattern INTERFACE = Pattern.compile("inet ([0-9\\.]+)/(\\d+)");

    Function<MachineState, Status> toPortableNodeStatus;

    @Inject
    public MachineToNodeMetadata(Function<MachineState, Status> toPortableNodeStatus) {
        this.toPortableNodeStatus = toPortableNodeStatus;
    }

    @Override
    public NodeMetadata apply(VagrantNode node) {
        Machine input = node.getMachine();
        SshConfig sshConfig = node.getSshConfig();
        NodeMetadataBuilder nodeMetadataBuilder = new NodeMetadataBuilder()
        .id(input.getId())
        .name(input.getName())
//        .group(???)
//      .operatingSystem(null)
        .location(new LocationBuilder().id("localhost").description("localhost").scope(LocationScope.HOST).build())
        .hostname(input.getName())
        .status(toPortableNodeStatus.apply(input.getStatus()))
        .loginPort(22)
        .privateAddresses(node.getNetworks())
        .publicAddresses(ImmutableList.<String>of())
        .credentials(LoginCredentials.builder().user(sshConfig.getUser()).privateKey(sshConfig.getIdentityFile()).build());
        return nodeMetadataBuilder.build();
    }
    
    public NodeMetadata apply2(Machine input) {
        VagrantApi vagrant = Vagrant.forMachine(input);
        NodeMetadataBuilder nodeMetadataBuilder = new NodeMetadataBuilder()
            .id(input.getId())
            .name(input.getName())
//            .group(???)
//          .operatingSystem(null)
            .location(new LocationBuilder().id("localhost").description("localhost").scope(LocationScope.HOST).build())
            .hostname(input.getName());
        if (input.exists()) {
            String networks = vagrant.ssh(input.getName(), "ip address show | grep 'scope global'");
            Matcher m = INTERFACE.matcher(networks);
            Collection<String> ips = new ArrayList<String>();
            while (m.find()) {
                ips.add(m.group(1));
            }

            SshConfig sshConfig = vagrant.sshConfig(input.getName());
            nodeMetadataBuilder
                .status(toPortableNodeStatus.apply(input.getStatus()))
                .loginPort(22)
                .privateAddresses(ips)
                .publicAddresses(ImmutableList.<String>of())
                .credentials(LoginCredentials.builder().user(sshConfig.getUser()).privateKey(sshConfig.getIdentityFile()).build());
        } else {
            nodeMetadataBuilder.status(Status.TERMINATED);
        }
        return nodeMetadataBuilder.build();
    }

}
