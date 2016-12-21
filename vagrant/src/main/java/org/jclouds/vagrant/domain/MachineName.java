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
package org.jclouds.vagrant.domain;

public class MachineName {
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
