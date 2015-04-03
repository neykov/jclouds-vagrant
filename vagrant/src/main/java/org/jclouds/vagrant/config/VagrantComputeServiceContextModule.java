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

import org.jclouds.compute.ComputeServiceAdapter;
import org.jclouds.compute.config.ComputeServiceAdapterContextModule;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadata.Status;
import org.jclouds.domain.Location;
import org.jclouds.functions.IdentityFunction;
import org.jclouds.vagrant.compute.VagrantComputeServiceAdapter;
import org.jclouds.vagrant.functions.BoxToImage;
import org.jclouds.vagrant.functions.MachineStateToJcloudsStatus;
import org.jclouds.vagrant.functions.MachineToNodeMetadata;

import vagrant.api.domain.Box;
import vagrant.api.domain.Machine;
import vagrant.api.domain.MachineState;

import com.google.common.base.Function;
import com.google.inject.TypeLiteral;

public class VagrantComputeServiceContextModule extends ComputeServiceAdapterContextModule<Machine, Hardware, Box, Location> {

   @Override
   protected void configure() {
      super.configure();
      bind(new TypeLiteral<ComputeServiceAdapter<Machine, Hardware, Box, Location>>() {
      }).to(VagrantComputeServiceAdapter.class);
      bind(new TypeLiteral<Function<Machine, NodeMetadata>>() {
      }).to(MachineToNodeMetadata.class);
      bind(new TypeLiteral<Function<MachineState, Status>>() {
      }).to(MachineStateToJcloudsStatus.class);
      bind(new TypeLiteral<Function<Box, Image>>() {
      }).to(BoxToImage.class);
      bind(new TypeLiteral<Function<Hardware, Hardware>>() {
      }).to(this.<Hardware>getIdentityFunction());
      bind(new TypeLiteral<Function<Location, Location>>() {
      }).to(this.<Location>getIdentityFunction());
   }
   
   @SuppressWarnings("unchecked")
   private <T> Class<Function<T, T>> getIdentityFunction() {
      return (Class<Function<T, T>>)(Class<?>)IdentityFunction.class;
   }
}