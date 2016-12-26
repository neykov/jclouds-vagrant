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

import org.testng.annotations.Test;

@Test(groups = "live", singleThreaded = true, testName = "CentOs7LiveTest")
public class CentOs7LiveTest extends VagrantComputeServiceAdapterLiveTest {

   @Override
   protected String getImageId() {
      return "centos/7";
   }

   @Test
   @Override
   public void testAScriptExecutionAfterBootWithBasicTemplate() throws Exception {
      // Fails on CentOS 7. Can't ssh back with user foo because SELinux not configured correctly.
      // "foo" is created out of the /home folder, /over/ridden is not white listed with the correct context.
      // Steps needed to configure SELinux before creating the user:
      //
      // semanage fcontext -a -e /home /over/ridden
      // mkdir /over/ridden
      // restorecon /over/ridden
      // useradd -d /over/ridden/foo foo
      //
      // semanage is not available on a default install - needs "yum install policycoreutils-python"
   }
}
