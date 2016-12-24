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
package org.jclouds.vagrant.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.jclouds.util.Closeables2;
import org.jclouds.vagrant.domain.VagrantNode;
import org.jclouds.vagrant.reference.VagrantConstants;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;

public class MachineConfig {
   private File configPath;

   public static MachineConfig newInstance(File path, String name) {
      return new MachineConfig(path, name);
   }

   public static MachineConfig newInstance(VagrantNode node) {
      return newInstance(node.path(), node.name());
   }

   protected MachineConfig(File path, String name) {
      this.configPath = new File(new File(path, VagrantConstants.MACHINES_CONFIG_SUBFOLDER), name + ".yaml");
   }

   public Map<String, Object> load() {
      Map<String, Object> config = new LinkedHashMap<String, Object>();
      Properties yaml = new Properties();
      FileInputStream fileIn;
      try {
         fileIn = new FileInputStream(configPath);
      } catch (FileNotFoundException e) {
         throw new IllegalStateException("Machine config not found: " + configPath.getAbsolutePath(), e);
      }
      Reader in = new InputStreamReader(fileIn, Charsets.UTF_8);
      try {
         // Poor man's YAML parser. It's controlled content, generated by us - not coming from a user so it's fine. 
         yaml.load(in);
      } catch (IOException e) {
         throw new IllegalStateException("Failed loading machine config " + configPath.getAbsolutePath(), e);
      } finally {
         Closeables2.closeQuietly(in);
      }
      for (String key : yaml.stringPropertyNames()) {
         config.put(key, yaml.getProperty(key));
      }
      return config;
   }

   // Write the config ad-hoc, imitating yaml which can be read by ruby
   // Could pull in snakeyaml to be more resilient to edge-cases in values
   // Alternatively use JSON if jclouds already depends on it in core
   public void save(Map<String, Object> config) {
      File parent = configPath.getParentFile();
      if (!parent.exists() && !parent.mkdirs()) {
         if (!parent.exists()) {
             throw new IllegalStateException("Failure creating folder " + parent.getAbsolutePath());
         }
      }

      String output = Joiner.on("\n").withKeyValueSeparator(": ").join(config);

      FileOutputStream fileOut = null;
      BufferedWriter out = null;

      try {
         fileOut = new FileOutputStream(configPath);
         out = new BufferedWriter(new OutputStreamWriter(fileOut, Charsets.UTF_8));
         out.write(output);
      } catch (IOException e) {
         throw new IllegalStateException("Failed writing to machine config file " + configPath.getAbsolutePath(), e);
      } finally {
         if (out != null) {
            Closeables2.closeQuietly(out);
         } else if (fileOut != null) {
            Closeables2.closeQuietly(fileOut);
         }
      }
   }
}