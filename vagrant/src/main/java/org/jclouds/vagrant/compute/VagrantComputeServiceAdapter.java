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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.jclouds.compute.ComputeServiceAdapter;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.HardwareBuilder;
import org.jclouds.compute.domain.Template;
import org.jclouds.domain.Location;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.vagrant.util.VagrantUtils;

import vagrant.Vagrant;
import vagrant.api.VagrantApi;
import vagrant.api.domain.Box;
import vagrant.api.domain.Machine;
import vagrant.api.domain.SshConfig;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

public class VagrantComputeServiceAdapter implements ComputeServiceAdapter<Machine, Hardware, Box, Location> {
   private File nodeContainer;
   
   @Inject
   public VagrantComputeServiceAdapter(@Named("vagrant.container-root") String nodeContainer) {
      this.nodeContainer = new File(nodeContainer);
      this.nodeContainer.mkdirs();
   }

   private static class MachineName {
      private String group;
      private String name;
      public MachineName(String group, String name) {
         this.group = group;
         this.name = name;
      }
      public MachineName(String id) {
         String[] arr = id.split("/");
         group = arr[0];
         name = arr[1];
      }
      public String getGroup() {
         return group;
      }
      public String getName() {
         return name;
      }
   }

   @Override
   public NodeAndInitialCredentials<Machine> createNodeWithGroupEncodedIntoName(String group, String name, Template template) {
      MachineName machineName = new MachineName(group, name);
      VagrantApi vagrant = getMachine(machineName);
      init(vagrant.getPath(), name, template);
      vagrant.up(machineName.getName());
      SshConfig sshConfig = vagrant.sshConfig(machineName.getName());
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
      Machine newMachine = vagrant.status(machineName.getName());
      newMachine.setId(group + "/" + name);
      return new NodeAndInitialCredentials<Machine>(newMachine, newMachine.getId(), loginCredentials);
   }

   private void init(File path, String name, Template template) {
      try {
         writeVagrantfile(path);
         initMachineConfig(path, name, template);
      } catch (IOException e) {
         throw new RuntimeException(e);
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
      } finally {
         out.close();
      }
   }

   @Override
   public Iterable<Hardware> listHardwareProfiles() {
        Set<Hardware> hardware = Sets.newLinkedHashSet();
        hardware.add(new HardwareBuilder().ids("t1.micro").hypervisor("VirtualBox").name("t1.micro").ram(512).build());
        hardware.add(new HardwareBuilder().ids("m1.small").hypervisor("VirtualBox").name("m1.small").ram(1024).build());
        hardware.add(new HardwareBuilder().ids("m1.medium").hypervisor("VirtualBox").name("m1.medium").ram(3840).build());
        hardware.add(new HardwareBuilder().ids("m1.large").hypervisor("VirtualBox").name("m1.large").ram(7680).build());
        return hardware;
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
      return Collections.emptyList();
   }

   @Override
   public Machine getNode(String id) {
      MachineName machine = new MachineName(id);
      if (getMachinePath(machine).exists()) {
         return getMachine(machine).status(machine.getName());
      } else {
         Machine ret = new Machine();
         ret.setId(id);
         ret.setName(machine.getName());
         ret.setPath(getMachinePath(machine));
         ret.setStatus(null);
         return ret;
      }
   }

   @Override
   public void destroyNode(String id) {
      MachineName machine = new MachineName(id);
      getMachine(machine).destroy(machine.getName());
      VagrantUtils.deleteFolder(getMachinePath(machine));
   }

   @Override
   public void rebootNode(String id) {
      MachineName machine = new MachineName(id);
      getMachine(machine).reload(machine.getName());
   }

   @Override
   public void resumeNode(String id) {
      MachineName machine = new MachineName(id);
      getMachine(machine).resume(machine.getName());
   }

   @Override
   public void suspendNode(String id) {
      MachineName machine = new MachineName(id);
      getMachine(machine).suspend(machine.getName());
   }

   @Override
   public Iterable<Machine> listNodes() {
      File[] nodePaths = nodeContainer.listFiles(new FileFilter() {
         @Override
         public boolean accept(File arg0) {
            return arg0.isDirectory();
         }
      });
      return FluentIterable.from(Arrays.asList(nodePaths))
         .transformAndConcat(new Function<File, Collection<Machine>>() {
            @Override
            public Collection<Machine> apply(File input) {
               return Vagrant.forPath(input).status();
            }
         });
   }

   @Override
   public Iterable<Machine> listNodesByIds(final Iterable<String> ids) {
      return Iterables.filter(listNodes(), new Predicate<Machine>() {
         @Override
         public boolean apply(Machine input) {
            return Iterables.contains(ids, input.getId());
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
