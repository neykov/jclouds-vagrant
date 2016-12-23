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
package org.jclouds.vagrant.reference;

public final class VagrantConstants {
   private VagrantConstants() {}

   public static final String VAGRANT_HOME = "vagrant.home";
   public static final String VAGRANTFILE = "Vagrantfile";
   public static final String DEFAULT_USERNAME = "vagrant";
   
   public static final String ENV_VAGRANT_HOME = "VAGRANT_HOME";
   public static final String VAGRANT_HOME_DEFAULT = ".vagrant.d";
   public static final String VAGRANT_BOXES_SUBFOLDER = "boxes";
   
   public static final String ESCAPE_SLASH = "-VAGRANTSLASH-";
   
   public static final String MACHINES_CONFIG_SUBFOLDER = "machines";
   public static final String MACHINES_CONFIG_EXTENSION = ".yaml";

   // Config file keys
   public static final String CONFIG_BOX = "box";
   public static final String CONFIG_HARDWARE_ID = "hardwareId";
   public static final String CONFIG_MEMORY = "memory";
   public static final String CONFIG_CPUS = "cpus";
   public static final String CONFIG_USERNAME = "username";
   public static final String CONFIG_PASSWORD = "password";
}
