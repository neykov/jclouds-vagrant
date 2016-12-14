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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;

import org.jclouds.compute.ComputeServiceAdapter;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.HardwareBuilder;
import org.jclouds.compute.domain.Processor;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.Volume.Type;
import org.jclouds.compute.domain.VolumeBuilder;
import org.jclouds.domain.Location;
import org.jclouds.domain.LocationBuilder;
import org.jclouds.domain.LocationScope;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.location.suppliers.all.JustProvider;
import org.jclouds.logging.Logger;
import org.jclouds.vagrant.domain.MachineName;
import org.jclouds.vagrant.domain.VagrantNode;
import org.jclouds.vagrant.util.VagrantUtils;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import vagrant.Vagrant;
import vagrant.api.VagrantApi;
import vagrant.api.domain.Box;
import vagrant.api.domain.Machine;
import vagrant.api.domain.MachineState;
import vagrant.api.domain.SshConfig;

public class VagrantComputeServiceAdapter implements ComputeServiceAdapter<VagrantNode, Hardware, Box, Location> {
   // TODO FIXME - use just as cache, expire items
   private static Map<String, VagrantNode> machines = new HashMap<String, VagrantNode>();
   private static final Pattern INTERFACE = Pattern.compile("inet ([0-9\\.]+)/(\\d+)");
   
   @Resource
   protected Logger logger = Logger.NULL;
   
   private final File nodeContainer;
   private final JustProvider locationSupplier;

   @Inject
   VagrantComputeServiceAdapter(@Named("vagrant.container-root") String nodeContainer, JustProvider locationSupplier) {
      this.nodeContainer = new File(checkNotNull(nodeContainer, "nodeContainer"));
      this.locationSupplier = checkNotNull(locationSupplier, "locationSupplier");
      this.nodeContainer.mkdirs();
   }

   @Override
   public NodeAndInitialCredentials<VagrantNode> createNodeWithGroupEncodedIntoName(String group, String name, Template template) {
      MachineName machineName = new MachineName(group, name);
      VagrantApi vagrant = getMachine(machineName);
      init(vagrant.getPath(), name, template);

      Machine newMachine = new Machine();
      newMachine.setId(group + "/" + name);
      newMachine.setName(name);
      newMachine.setStatus(MachineState.POWER_OFF);
      newMachine.setPath(getMachinePath(machineName));

//      Machine newMachine = vagrant.status(machineName.getName());
//      newMachine.setId(group + "/" + name);

      VagrantNode node = new VagrantNode(newMachine);

      machines.put(newMachine.getId(), node);

      return startMachine(vagrant, node);
   }

   private NodeAndInitialCredentials<VagrantNode> startMachine(VagrantApi vagrant, VagrantNode node) {
      Machine newMachine = node.getMachine();

      vagrant.up(newMachine.getName());

      SshConfig sshConfig = vagrant.sshConfig(newMachine.getName());
      node.setSshConfig(sshConfig);
      node.setNetworks(getNetworks(newMachine.getName(), vagrant));
      node.setHostname(getHostname(newMachine.getName(), vagrant));
      node.setMachineState(MachineState.RUNNING);
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

   private void init(File path, String name, Template template) {
      try {
         writeVagrantfile(path);
         initMachineConfig(path, name, template);
      } catch (IOException e) {
         throw new IllegalStateException("Unable to initialize Vagrant configuration at " + path + " for machine " + name, e);
      }
   }

   private void writeVagrantfile(File path) throws IOException {
      path.mkdirs();
      VagrantUtils.write(new File(path, "Vagrantfile"), getClass().getResourceAsStream("/Vagrantfile"));
   }

   private void initMachineConfig(File path, String name, Template template) throws IOException {
      File machines = new File(path, "machines");
      machines.mkdirs();
      
      File machineConfig = new File(machines, name + ".yaml");
      
      Charset encoding = Charset.forName("UTF-8");
      
      //write the config manually, no need to pull dependencies for now
      BufferedWriter out;
      FileOutputStream fileOut = new FileOutputStream(machineConfig);
      out = new BufferedWriter(new OutputStreamWriter(fileOut, encoding));
      
      try {
         out.write("box: " + template.getImage().getId() + "\n");
         out.write("memory: " + template.getHardware().getRam() + "\n");
         out.write("cpus: " + countProcessors(template));
      } finally {
         out.close();
      }
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
      Set<Hardware> hardware = Sets.newLinkedHashSet();
      hardware.add(hardware("micro", 512, 1));
      hardware.add(hardware("small", 1024, 1));
      hardware.add(hardware("medium", 2048, 2));
      hardware.add(hardware("large", 4096, 2));
      return hardware;
   }

   private Hardware hardware(String name, int ram, int cores) {
      return new HardwareBuilder()
              .ids(name)
              .hypervisor("VirtualBox")
              .name(name)
              .processor(new Processor(cores, 1))
              .ram(ram)
              .volume(new VolumeBuilder().bootDevice(true).durable(true).type(Type.LOCAL).build())
              .build();
   }

   @Override
   public Iterable<Box> listImages() {
      //TODO Include online images
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

      return machines.get(id);
   }

   @Override
   public void destroyNode(String id) {
       VagrantNode node = machines.get(id);
       node.setMachineState(null);
      MachineName machine = new MachineName(id);
      getMachine(machine).destroy(machine.getName());
      VagrantUtils.deleteFolder(getMachinePath(machine));
   }

   @Override
   public void rebootNode(String id) {
      MachineName machine = new MachineName(id);
      try {
          getMachine(machine).halt(machine.getName());
      } catch (IllegalStateException e) {
          logger.warn(e, "Failed graceful shutdown of machine " + id + " (for reboot). Will try to halt it forcefully instead.");
          getMachine(machine).haltForced(machine.getName());
      }
      getMachine(machine).up(machine.getName());
   }

   @Override
   public void resumeNode(String id) {
      MachineName machine = new MachineName(id);
      getMachine(machine).resume(machine.getName());
      VagrantNode node = machines.get(id);
      node.setMachineState(MachineState.RUNNING);
      node.getMachine().setStatus(MachineState.RUNNING);
   }

   @Override
   public void suspendNode(String id) {
      MachineName machine = new MachineName(id);
      getMachine(machine).suspend(machine.getName());
      VagrantNode node = machines.get(id);
      node.setMachineState(MachineState.SAVED);
      node.getMachine().setStatus(MachineState.SAVED);
   }

//   @Override
//   public Iterable<VagrantNode> listNodes() {
//       return ImmutableSet.of();
//   }
   @Override
   public Iterable<VagrantNode> listNodes() {
      return FluentIterable.from(Arrays.asList(nodeContainer.listFiles()))
          .transformAndConcat(new Function<File, Collection<VagrantNode>>() {
             @Override
             public Collection<VagrantNode> apply(File input) {
                VagrantApi vagrant = Vagrant.forPath(input);
                if (input.isDirectory() && vagrant.exists()) {
                   Collection<Machine> status = vagrant.status();
                   Collection<VagrantNode> nodes = new ArrayList<VagrantNode>(machines.size());
                   for (Machine m : status) {
                       VagrantNode n = machines.get(m.getId());
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

   private VagrantApi getMachine(MachineName machineName) {
      File nodePath = getMachinePath(machineName);
      return Vagrant.forPath(nodePath);
   }

   private File getMachinePath(MachineName machineName) {
      return new File(nodeContainer, machineName.getGroup());
   }

}
