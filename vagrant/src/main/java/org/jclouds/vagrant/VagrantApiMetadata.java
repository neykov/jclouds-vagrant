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
package org.jclouds.vagrant;

import java.net.URI;
import java.util.Properties;

import org.jclouds.apis.ApiMetadata;
import org.jclouds.apis.internal.BaseApiMetadata;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.rest.internal.BaseHttpApiMetadata;
import org.jclouds.vagrant.config.VagrantComputeServiceContextModule;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;

public class VagrantApiMetadata extends BaseApiMetadata {

   public VagrantApiMetadata() {
      this(new Builder());
   }

   protected VagrantApiMetadata(Builder builder) {
      super(builder);
   }

   @Override
   public Builder toBuilder() {
      return new Builder().fromApiMetadata(this);
   }

   public static class Builder extends BaseApiMetadata.Builder<Builder> {

      protected Builder() {
         Properties defaultProperties = BaseApiMetadata.defaultProperties();
         defaultProperties.setProperty("vagrant.image.login-user", "vagrant");
         defaultProperties.setProperty("vagrant.container-root", System.getProperty("user.home") + "/.jagrant/machines");

         id("vagrant")
         .name("Vagrant API")
         .identityName("User")
         .credentialName("Password")
         .defaultProperties(BaseHttpApiMetadata.defaultProperties())
         .defaultEndpoint("http://0.0.0.0:0/")
         .documentation(URI.create("https://www.virtualbox.org/sdkref/index.html"))
         .view(ComputeServiceContext.class)
         .defaultIdentity("guest")
         .defaultCredential("guest")
         .defaultProperties(defaultProperties)
         .defaultModules(ImmutableSet.<Class<? extends Module>>of(VagrantComputeServiceContextModule.class));
      }

      @Override
      public ApiMetadata build() {
         return new VagrantApiMetadata(this);
      }

      @Override
      protected Builder self() {
         return this;
      }

   }
}
