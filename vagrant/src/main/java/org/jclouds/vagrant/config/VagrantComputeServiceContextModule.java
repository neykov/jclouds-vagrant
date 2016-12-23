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
package org.jclouds.vagrant.config;

import java.util.Map;

import org.jclouds.compute.ComputeServiceAdapter;
import org.jclouds.compute.config.ComputeServiceAdapterContextModule;
import org.jclouds.compute.config.PersistNodeCredentialsModule;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadata.Status;
import org.jclouds.compute.domain.internal.ArbitraryCpuRamTemplateBuilderImpl;
import org.jclouds.compute.domain.internal.TemplateBuilderImpl;
import org.jclouds.compute.strategy.PopulateDefaultLoginCredentialsForImageStrategy;
import org.jclouds.domain.Location;
import org.jclouds.functions.IdentityFunction;
import org.jclouds.vagrant.compute.VagrantComputeServiceAdapter;
import org.jclouds.vagrant.domain.VagrantNode;
import org.jclouds.vagrant.functions.BoxToImage;
import org.jclouds.vagrant.functions.MachineStateToJcloudsStatus;
import org.jclouds.vagrant.functions.MachineToNodeMetadata;
import org.jclouds.vagrant.internal.VagrantIOListener;
import org.jclouds.vagrant.strategy.VagrantDefaultImageCredentials;
import org.jclouds.vagrant.suppliers.VagrantHardwareSupplier;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

import vagrant.api.CommandIOListener;
import vagrant.api.domain.Box;
import vagrant.api.domain.MachineState;

public class VagrantComputeServiceContextModule extends ComputeServiceAdapterContextModule<VagrantNode, Hardware, Box, Location> {

   @Override
   protected void configure() {
      super.configure();
      bind(new TypeLiteral<ComputeServiceAdapter<VagrantNode, Hardware, Box, Location>>() {
      }).to(VagrantComputeServiceAdapter.class);
      bind(new TypeLiteral<Function<VagrantNode, NodeMetadata>>() {
      }).to(MachineToNodeMetadata.class);
      bind(new TypeLiteral<Function<MachineState, Status>>() {
      }).to(MachineStateToJcloudsStatus.class);
      bind(new TypeLiteral<Function<Box, Image>>() {
      }).to(BoxToImage.class);
      bind(new TypeLiteral<Supplier<Map<String, Hardware>>>() {
      }).to(VagrantHardwareSupplier.class);
      bind(new TypeLiteral<Function<Hardware, Hardware>>() {
      }).to(this.<Hardware>castIdentityFunction());
      bind(new TypeLiteral<Function<Location, Location>>() {
      }).to(this.<Location>castIdentityFunction());
      bind(PopulateDefaultLoginCredentialsForImageStrategy.class).to(VagrantDefaultImageCredentials.class);
      bind(TemplateBuilderImpl.class).to(ArbitraryCpuRamTemplateBuilderImpl.class);
      bind(CommandIOListener.class).to(VagrantIOListener.class).in(Singleton.class);
   }

   @Override
   protected void install(Module module) {
      // override PersistNodeCredentialsModule bindings, any better way to do it?
      if (module instanceof PersistNodeCredentialsModule) {
          super.install(new PersistVagrantCredentialsModule());
      } else {
          super.install(module);
      }
   }

   @SuppressWarnings("unchecked")
   private <T> Class<Function<T, T>> castIdentityFunction() {
      return (Class<Function<T, T>>)(Class<?>)IdentityFunction.class;
   }

}
