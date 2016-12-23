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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Set;

import org.jclouds.collect.Memoized;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.HardwareBuilder;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadata.Status;
import org.jclouds.compute.domain.NodeMetadataBuilder;
import org.jclouds.compute.domain.Processor;
import org.jclouds.compute.util.AutomaticHardwareIdSpec;
import org.jclouds.domain.Credentials;
import org.jclouds.domain.Location;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.vagrant.domain.VagrantNode;
import org.jclouds.vagrant.util.MachineConfig;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import vagrant.api.domain.MachineState;
import vagrant.api.domain.SshConfig;

public class MachineToNodeMetadata implements Function<VagrantNode, NodeMetadata> {
   private final Function<MachineState, Status> toPortableNodeStatus;
   private final Location location;
   private final Map<String, Credentials> credentialStore;
   private final Supplier<Map<String, Hardware>> hardwareSupplier;

   @Inject
   public MachineToNodeMetadata(Function<MachineState, Status> toPortableNodeStatus,
         @Memoized Supplier<Set<? extends Location>> locations,
         Map<String, Credentials> credentialStore,
         Supplier<Map<String, Hardware>> hardwareSupplier) {
      this.toPortableNodeStatus = checkNotNull(toPortableNodeStatus, "toPortableNodeStatus");
      this.location = Iterables.getOnlyElement(checkNotNull(locations, "locations").get());
      this.hardwareSupplier = checkNotNull(hardwareSupplier, "hardwareSupplier");
      this.credentialStore = credentialStore;
   }

   @Override
   public NodeMetadata apply(VagrantNode node) {
      MachineConfig machineConfig = MachineConfig.newInstance(node);

      Map<String, Object> config = machineConfig.load();
      String hardwareId = config.get("hardwareId").toString();
      Hardware hardware;
      if (AutomaticHardwareIdSpec.isAutomaticId(hardwareId)) {
         double cpus = Double.parseDouble(config.get("cpus").toString());
         int memory = Integer.parseInt(config.get("memory").toString());
         AutomaticHardwareIdSpec hardwareSpec = AutomaticHardwareIdSpec.automaticHardwareIdSpecBuilder(cpus, memory, Optional.<Float>absent());
         hardware = new HardwareBuilder()
               .id(hardwareSpec.toString())
               .providerId(hardwareSpec.toString())
               .processor(new Processor(cpus, 1.0))
               .ram(memory)
               .build();
      } else {
         hardware = hardwareSupplier.get().get(hardwareId);
         if (hardware == null) {
            throw new IllegalStateException("Unsupported hardwareId " + hardwareId + " for machine " + node.id());
         }
      }

      NodeMetadataBuilder nodeMetadataBuilder = new NodeMetadataBuilder()
            .ids(node.id())
            .name(node.name())
            .group(node.path().getName())
            .location(location)
            .hardware(hardware)
            .hostname(node.name())
            .status(toPortableNodeStatus.apply(node.machineState()))
            .loginPort(22)
            .privateAddresses(node.networks())
            .publicAddresses(ImmutableList.<String> of()).hostname(node.hostname());

      // Credentials could be changed by AdminAccess script, check store first
      Credentials credentials = credentialStore.get("node#" + node.id());
      if (credentials != null) {
         nodeMetadataBuilder.credentials(LoginCredentials.fromCredentials(credentials));
      } else if (node.sshConfig() != null) {
         SshConfig sshConfig = node.sshConfig();
         nodeMetadataBuilder.credentials(
               LoginCredentials.builder().user(sshConfig.getUser()).privateKey(sshConfig.getIdentityFile()).build());
      }
      return nodeMetadataBuilder.build();
   }

}
