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

import java.util.Set;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.internal.BaseComputeServiceContextLiveTest;
import org.testng.annotations.Test;

import com.google.common.collect.Iterables;

@Test(groups = "live", singleThreaded = true, enabled = true, testName = "WindowsLiveTest")
public class WindowsLiveTest extends BaseComputeServiceContextLiveTest {

   protected ComputeService client;

   public WindowsLiveTest() {
      provider = "vagrant";
   }

   @Override
   protected void initializeContext() {
      super.initializeContext();
      client = view.getComputeService();
   }

   protected TemplateBuilder templateBuilder() {
      TemplateBuilder templateBuilder = client.templateBuilder();
      if (templateBuilderSpec != null) {
         templateBuilder = templateBuilder.from(templateBuilderSpec);
      }
      templateBuilder.imageId("boxcutter/eval-win7x86-enterprise");
      return templateBuilder;
   }

   protected Template buildTemplate(TemplateBuilder templateBuilder) {
      return templateBuilder.build();
   }

   @Test
   public void testGet() throws Exception {
      Set<? extends NodeMetadata> nodes = client.createNodesInGroup("vagrant-", 1, buildTemplate(templateBuilder()));
      client.destroyNode(Iterables.getOnlyElement(nodes).getId());
   }
}