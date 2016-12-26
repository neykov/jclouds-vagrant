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
package org.jclouds.vagrant.internal;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jclouds.compute.domain.Image;
import org.jclouds.vagrant.reference.VagrantConstants;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.io.Files;

import vagrant.api.domain.Box;

public class BoxConfigParser {
   private String config;
   private File providerPath;

   private BoxConfigParser(String name, String version, String provider) {
      File boxes = new File(getVagrantHome(), VagrantConstants.VAGRANT_BOXES_SUBFOLDER);
      File boxPath = new File(boxes, name.replace("/", VagrantConstants.ESCAPE_SLASH));
      File versionPath = new File(boxPath, version);
      File providerPath = new File(versionPath, provider);
      File vagrantfilePath = new File(providerPath, VagrantConstants.VAGRANTFILE);

      if (!vagrantfilePath.exists()) {
         throw new IllegalStateException("Vagrantfile for box '" + name + "'" +
               " at " + vagrantfilePath.getAbsolutePath() + " not found");
      }

      try {
         config = Files.toString(vagrantfilePath, Charsets.UTF_8);
      } catch (IOException e) {
         throw new IllegalStateException("Failure reading box '" + name + "'" +
               " at " + vagrantfilePath.getAbsolutePath(), e);
      }

      this.providerPath = providerPath;
   }

   public File getFolder() {
      return providerPath;
   }

   public Optional<String> getKey(String key) {
      String keyQuoted = Pattern.quote(key);
      String search = keyQuoted + "\\s*=\\s*(.*)";
      Matcher matcher = Pattern.compile(search).matcher(config);
      if (matcher.find()) {
         return Optional.of(matcher.group(1).trim());
      } else {
         return Optional.absent();
      }
   }

   public Optional<String> getStringKey(String key) {
      String keyQuoted = Pattern.quote(key);
      String search = keyQuoted + "\\s*=\\s*\"(.*)\"";
      Matcher matcher = Pattern.compile(search).matcher(config);
      if (matcher.find()) {
         return Optional.of(matcher.group(1));
      } else {
         return Optional.absent();
      }
   }

   private File getVagrantHome() {
      String home = System.getProperty(VagrantConstants.ENV_VAGRANT_HOME);
      if (home != null) {
         return new File(home);
      } else {
         return new File(System.getProperty("user.home"), VagrantConstants.ENV_VAGRANT_HOME_DEFAULT);
      }
   }

   public static BoxConfigParser newInstance(Image image) {
      String provider = image.getUserMetadata().get("provider");
      return new BoxConfigParser(image.getName(), image.getVersion(), provider);
   }

   public static BoxConfigParser newInstance(Box box) {
      return new BoxConfigParser(box.getName(), box.getVersion(), box.getProvider());
   }

}
