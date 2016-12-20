/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jclouds.vagrant.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import org.jclouds.vagrant.domain.VagrantNode;

public class VagrantNodeRegistry {
   private static final long TERMINATED_NODES_EXPIRY_MS = TimeUnit.MINUTES.toMillis(5);
   private static final long VACUUM_PERIOD_MS = TimeUnit.SECONDS.toMillis(15);

   private static class TerminatedNode implements Delayed {
      long expiryTime;
      VagrantNode node;
      TerminatedNode(VagrantNode node) {
         this.expiryTime = System.currentTimeMillis() + TERMINATED_NODES_EXPIRY_MS;
         this.node = node;
      }
      @Override
      public int compareTo(Delayed o) {
         if (this == o) {
            return 0;
         } else if (o instanceof TerminatedNode) {
            TerminatedNode other = (TerminatedNode)o;
            if (expiryTime < other.expiryTime) {
               return -1;
            } else if (expiryTime > other.expiryTime) {
               return 1;
            } else {
               return 0;
            }
         } else {
            long diff = getDelay(TimeUnit.NANOSECONDS) - o.getDelay(TimeUnit.NANOSECONDS);
            if (diff < 0) {
               return -1;
            } else if (diff > 0) {
               return 1;
            } else {
               return 0;
            }
         }
      }
      @Override
      public long getDelay(TimeUnit unit) {
         return unit.convert(expiryTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
      }
   }
   private Map<String, VagrantNode> nodes = new ConcurrentHashMap<String, VagrantNode>();
   private DelayQueue<TerminatedNode> terminatedNodes = new DelayQueue<TerminatedNode>();

   private VacuumVagrantNode vacuumVagrantNode;
   private volatile long lastVacuumMs;

   public VagrantNodeRegistry(VacuumVagrantNode vacuumVagrantNode) {
      this.vacuumVagrantNode = vacuumVagrantNode;
   }

   public VagrantNode get(String id) {
      vacuum();
      return nodes.get(id);
   }

   protected void vacuum() {
      // No need to lock on lastVacuumMs - not critical if we miss/do double vacuuming.
      if (System.currentTimeMillis() - lastVacuumMs > VACUUM_PERIOD_MS) {
         TerminatedNode terminated;
         while ((terminated = terminatedNodes.poll()) != null) {
            vacuumVagrantNode.vacuum(terminated.node);
            nodes.remove(terminated.node.getMachine().getId());
         }
         lastVacuumMs = System.currentTimeMillis();
      }
   }

   public void add(VagrantNode node) {
      nodes.put(node.getMachine().getId(), node);
   }

   public void onTerminated(VagrantNode node) {
      terminatedNodes.add(new TerminatedNode(node));
   }

}
