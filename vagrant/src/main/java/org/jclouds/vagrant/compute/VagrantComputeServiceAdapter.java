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
package org.jclouds.vagrant.compute;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;

import org.jclouds.compute.ComputeServiceAdapter;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Processor;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.Volume;
import org.jclouds.compute.domain.Volume.Type;
import org.jclouds.domain.Location;
import org.jclouds.domain.LocationBuilder;
import org.jclouds.domain.LocationScope;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.location.suppliers.all.JustProvider;
import org.jclouds.logging.Logger;
import org.jclouds.vagrant.domain.VagrantNode;
import org.jclouds.vagrant.internal.VagrantNodeRegistry;
import org.jclouds.vagrant.util.MachineConfig;
import org.jclouds.vagrant.util.VagrantUtils;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

import vagrant.Vagrant;
import vagrant.api.VagrantApi;
import vagrant.api.domain.Box;
import vagrant.api.domain.Machine;
import vagrant.api.domain.MachineState;
import vagrant.api.domain.SshConfig;

public class VagrantComputeServiceAdapter implements ComputeServiceAdapter<VagrantNode, Hardware, Box, Location> {
   private static final Pattern INTERFACE = Pattern.compile("inet ([0-9\\.]+)/(\\d+)");
   
   @Resource
   protected Logger logger = Logger.NULL;
   
   private final File nodeContainer;
   private final JustProvider locationSupplier;
   private final VagrantNodeRegistry nodeRegistry;
   private final Supplier<Map<String, Hardware>> hardwareSupplier;

   @Inject
   VagrantComputeServiceAdapter(@Named("vagrant.container-root") String nodeContainer,
         JustProvider locationSupplier,
         VagrantNodeRegistry nodeRegistry,
         Supplier<Map<String, Hardware>> hardwareSupplier) {
      this.nodeContainer = new File(checkNotNull(nodeContainer, "nodeContainer"));
      this.locationSupplier = checkNotNull(locationSupplier, "locationSupplier");
      this.nodeRegistry = checkNotNull(nodeRegistry, "nodeRegistry");
      this.hardwareSupplier = checkNotNull(hardwareSupplier, "hardwareSupplier");
      this.nodeContainer.mkdirs();
   }

   @Override
   public NodeAndInitialCredentials<VagrantNode> createNodeWithGroupEncodedIntoName(String group, String name, Template template) {
      String machineName = removeFromStart(name, group);

      Machine newMachine = new Machine();
      newMachine.setId(group + "/" + machineName);
      newMachine.setName(machineName);
      newMachine.setStatus(MachineState.POWER_OFF);
      newMachine.setPath(new File(nodeContainer, group));
      VagrantNode node = new VagrantNode(newMachine);

      init(node, template);

      nodeRegistry.add(node);

      VagrantApi vagrant = getMachine(node);
      return startMachine(vagrant, node);
   }

   private NodeAndInitialCredentials<VagrantNode> startMachine(VagrantApi vagrant, VagrantNode node) {
      Machine newMachine = node.getMachine();

      String name = newMachine.getName();
      vagrant.up(name);

      SshConfig sshConfig = vagrant.sshConfig(name);
      node.setSshConfig(sshConfig);
      node.setNetworks(getNetworks(name, vagrant));
      node.setHostname(getHostname(name, vagrant));
      newMachine.setStatus(MachineState.RUNNING);

      String privateKey;
      try {
         privateKey = Files.toString(new File(sshConfig.getIdentityFile()), Charset.defaultCharset());
      } catch (IOException e) {
         throw new IllegalStateException("Invalid private key " + sshConfig.getIdentityFile(), e);
      }

      LoginCredentials loginCredentials = LoginCredentials.builder()
            .user(sshConfig.getUser())
            .privateKey(privateKey)
            .build();
      return new NodeAndInitialCredentials<VagrantNode>(node, newMachine.getId(), loginCredentials);
   }

   private Collection<String> getNetworks(String name, VagrantApi vagrant) {
       // TODO Add ifconfig fallback in case ip is not available; ifconfig not available on CentOS 7.
       String networks = vagrant.ssh(name, "ip address show | grep 'scope global'");
       Matcher m = INTERFACE.matcher(networks);
       Collection<String> ips = new ArrayList<String>();
       while (m.find()) {
          String network = m.group(1);
          // TODO figure out a more generic approach to ignore unreachable networkds (this one is the NAT'd address).
          if (network.startsWith("10.")) continue;
          ips.add(network);
       }
       return ips;
    }

   private String getHostname(String name, VagrantApi vagrant) {
       return vagrant.ssh(name, "hostname").trim();
    }

   private void init(VagrantNode node, Template template) {
      try {
         writeVagrantfile(node.getMachine().getPath());
         initMachineConfig(node, template);
      } catch (IOException e) {
         throw new IllegalStateException("Unable to initialize Vagrant configuration at " +
               node.getMachine().getPath() + " for machine " + node.getMachine().getId(), e);
      }
   }

   private void writeVagrantfile(File path) throws IOException {
      path.mkdirs();
      VagrantUtils.write(new File(path, "Vagrantfile"), getClass().getResourceAsStream("/Vagrantfile"));
   }

   private void initMachineConfig(VagrantNode node, Template template) {
      MachineConfig config = MachineConfig.newInstance(node);
      List<? extends Volume> volumes = template.getHardware().getVolumes();
      if (volumes != null) {
         if (volumes.size() == 1) {
            Volume volume = Iterables.getOnlyElement(volumes);
            if (volume.getType() != Type.LOCAL || volume.getSize() != null) {
               throw new IllegalStateException("Custom volume settings not supported. Volumes required: " + volumes);
            }
         } else if (volumes.size() > 1) {
            throw new IllegalStateException("Custom volume settings not supported. Volumes required: " + volumes);
         }
      }
      config.save(ImmutableMap.<String, Object>of(
            "box", template.getImage().getId(),
            "hardwareId", template.getHardware().getId(),
            "memory", Integer.toString(template.getHardware().getRam()),
            "cpus", Integer.toString(countProcessors(template))));
   }

   private int countProcessors(Template template) {
      int cnt = 0;
      for (Processor p : template.getHardware().getProcessors()) {
          cnt += p.getCores();
      }
      return cnt;
   }

   @Override
   public Iterable<Hardware> listHardwareProfiles() {
      return hardwareSupplier.get().values();
   }

   @Override
   public Iterable<Box> listImages() {
      return Vagrant.forPath(new File(".")).box().list();
   }

   @Override
   public Box getImage(final String id) {
      return Iterables.find(listImages(),
            new Predicate<Box>() {
               @Override
               public boolean apply(Box input) {
                  return id.equals(input.getName());
               }
            }, null);
   }

   @Override
   public Iterable<Location> listLocations() {
      Location provider = Iterables.getOnlyElement(locationSupplier.get());
      return ImmutableList.of(
              new LocationBuilder().id("localhost").description("localhost").parent(provider).scope(LocationScope.HOST).build());
   }

   @Override
   public VagrantNode getNode(String id) {
      // needed for BaseComputeServiceLiveTest.testAScriptExecutionAfterBootWithBasicTemplate()
      // waits for the thread updating the credentialStore to execute
      try {
         Thread.sleep(200);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw Throwables.propagate(e);
      }

      return nodeRegistry.get(id);
   }

   @Override
   public void destroyNode(String id) {
      VagrantNode node = nodeRegistry.get(id);
      node.setMachineState(null);
      getMachine(node).destroy(node.getMachine().getName());
      nodeRegistry.onTerminated(node);
      deleteMachine(node);
   }

   private void deleteMachine(VagrantNode node) {
      Machine machine = node.getMachine();
      File nodeFolder = machine.getPath();
      File machinesFolder = new File(nodeFolder, "machines");
      String filePattern = machine.getName() + ".";
      logger.debug("Deleting machine %s", machine.getId());
      VagrantUtils.deleteFiles(machinesFolder, filePattern);
      // No more machines in this group, remove everything
      if (machinesFolder.list().length == 0) {
         logger.debug("Machine %s is last in group, deleting Vagrant folder %s", machine.getId(), nodeFolder.getAbsolutePath());
         VagrantUtils.deleteFolder(nodeFolder);
      }
   }

   @Override
   public void rebootNode(String id) {
      VagrantNode node = nodeRegistry.get(id);
      String name = node.getMachine().getName();
      VagrantApi vagrant = getMachine(node);
      try {
          vagrant.halt(name);
      } catch (IllegalStateException e) {
          logger.warn(e, "Failed graceful shutdown of machine " + id + " (for reboot). Will try to halt it forcefully instead.");
          vagrant.haltForced(name);
      }
      vagrant.up(name);
   }

   @Override
   public void resumeNode(String id) {
      VagrantNode node = nodeRegistry.get(id);
      String name = node.getMachine().getName();
      VagrantApi vagrant = getMachine(node);
      vagrant.resume(name);
      node.setMachineState(MachineState.RUNNING);
      node.getMachine().setStatus(MachineState.RUNNING);
   }

   @Override
   public void suspendNode(String id) {
      VagrantNode node = nodeRegistry.get(id);
      String name = node.getMachine().getName();
      getMachine(node).suspend(name);
      node.setMachineState(MachineState.SAVED);
      node.getMachine().setStatus(MachineState.SAVED);
   }

   @Override
   public Iterable<VagrantNode> listNodes() {
      return FluentIterable.from(Arrays.asList(nodeContainer.listFiles()))
          .transformAndConcat(new Function<File, Collection<VagrantNode>>() {
             @Override
             public Collection<VagrantNode> apply(File input) {
                File machines = new File(input, "machines");
                VagrantApi vagrant = Vagrant.forPath(input);
                if (input.isDirectory() && machines.exists() && vagrant.exists()) {
                   Collection<Machine> status = vagrant.status();
                   Collection<VagrantNode> nodes = new ArrayList<VagrantNode>();
                   for (Machine m : status) {
                       VagrantNode n = nodeRegistry.get(m.getId());
                       if (n != null) {
                           nodes.add(n);
                       }
                   }
                   return nodes;
                } else {
                   return ImmutableList.of();
                }
           }
         });
   }

   @Override
   public Iterable<VagrantNode> listNodesByIds(final Iterable<String> ids) {
      return Iterables.filter(listNodes(), new Predicate<VagrantNode>() {
         @Override
         public boolean apply(VagrantNode input) {
            return Iterables.contains(ids, input.getMachine().getId());
         }
      });
   }

   private VagrantApi getMachine(VagrantNode node) {
      File nodePath = node.getMachine().getPath();
      return Vagrant.forPath(nodePath);
   }

   private String removeFromStart(String name, String group) {
      if (name.startsWith(group)) {
         String machineName = name.substring(group.length());
         // Can't pass names starting with dash on the command line
         if (machineName.startsWith("-")) {
            return machineName.substring(1);
         } else {
            return machineName;
         }
      } else {
         return name;
      }
   }

}
