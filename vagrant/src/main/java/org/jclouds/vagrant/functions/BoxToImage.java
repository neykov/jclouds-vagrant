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
package org.jclouds.vagrant.functions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;

import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.Image.Status;
import org.jclouds.compute.domain.ImageBuilder;
import org.jclouds.compute.domain.OperatingSystem;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.domain.LoginCredentials;

import vagrant.api.domain.Box;

import com.google.common.base.Function;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;

public class BoxToImage implements Function<Box, Image> {

   @Override
   public Image apply(Box input) {
      OperatingSystem os = new OperatingSystem(inferOsFamily(input), input.getName(), inferOsVersion(input), null, input.getName(), true);
      File boxPath = getBoxPath(input);
      String config = readBoxConfig(boxPath);
      LoginCredentials cred = parseBoxCredentials(config);
      return new ImageBuilder()
         .id(input.getName())
         .name(input.getName())
         .version(inferOsVersion(input))
//         .description()
         .operatingSystem(os)
         .status(Status.AVAILABLE)
         .defaultCredentials(cred)
         .build();
   }
   private OsFamily inferOsFamily(Box input) {
      String name = input.getName().toUpperCase();
      for (OsFamily family : OsFamily.values()) {
         if (name.contains(family.name())) {
            return family;
         }
      }
      return OsFamily.UNRECOGNIZED;
   }
   private String inferOsVersion(Box input) {
      String name = input.getName().toLowerCase();
      if (name.contains("trusty")) {
         return "14.04";
      } else if (name.contains("wily")) {
         return "15.10";
      } else if (name.contains("precise")) {
         return "12.04";
      } else {
         return input.getVersion();
      }
   }
   private LoginCredentials parseBoxCredentials(String config) {
      return LoginCredentials.builder().user("vagrant").build();
   }
   private String readBoxConfig(File boxPath) {
      try {
         File boxConfig = new File(boxPath, "Vagrantfile");
         if (!boxConfig.exists()) return "";
         Writer out = new StringWriter();
         Charset encoding = Charset.forName("UTF-8");
         Reader in = new InputStreamReader(new FileInputStream(boxConfig), encoding);
         CharStreams.copy(in, out);
         Closeables.close(in, true);
         Closeables.close(out, true);
         return out.toString();
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }
   private File getBoxPath(Box input) {
      File boxes = new File(getVagrantHome(), "boxes");
      File boxPath = new File(boxes, getPathName(input));
      File providerPath = new File(boxPath, input.getProvider());
      File versionPath = new File(providerPath, inferOsVersion(input));
      return versionPath;
   }
   private String getPathName(Box input) {
      return input.getName().replace("/", "-VAGRANTSLASH-");
   }
   private File getVagrantHome() {
      String home = System.getProperty("VAGRANT_HOME");
      if (home != null) {
         return new File(home);
      } else {
         return new File(System.getProperty("user.home"), ".vagrant.d");
      }
   }
}
