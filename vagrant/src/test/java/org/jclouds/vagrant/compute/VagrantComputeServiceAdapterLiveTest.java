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

import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.internal.BaseComputeServiceLiveTest;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;

@Test(groups = "live", singleThreaded = true, testName = "VagrantComputeServiceAdapterLiveTest")
public class VagrantComputeServiceAdapterLiveTest extends BaseComputeServiceLiveTest {

   public VagrantComputeServiceAdapterLiveTest() {
      provider = "vagrant";
   }

   @Override
   protected Module getSshModule() {
      return new SshjSshClientModule();
   }

   @Override
   protected Template buildTemplate(TemplateBuilder templateBuilder) {
       templateBuilder
           .imageId("ubuntu/trusty64");
       return super.buildTemplate(templateBuilder);
   }

   @Override
   @Test(enabled = false)
   public void testCorrectAuthException() throws Exception {
      // Vagrant doesn't use credential info
   }

   @Override
   protected void checkTagsInNodeEquals(NodeMetadata node, ImmutableSet<String> tags) {
      // Vagrant doesn't support tags
      // TODO Could store it in the json
   }

   @Override
   protected void checkUserMetadataContains(NodeMetadata node, ImmutableMap<String, String> userMetadata) {
      // Vagrant doesn't support user metadata
      // TODO Could store it in the json
   }

   @Override
   @Test
   public void testOptionToNotBlock() throws Exception {
       // LoginCredentials are available only after the machine starts,
       // so can't return earlier.
   }
}
