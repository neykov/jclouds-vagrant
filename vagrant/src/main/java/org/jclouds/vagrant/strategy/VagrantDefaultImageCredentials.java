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
package org.jclouds.vagrant.strategy;

import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;

import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.strategy.PopulateDefaultLoginCredentialsForImageStrategy;
import org.jclouds.domain.Credentials;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.domain.LoginCredentials.Builder;
import org.jclouds.javax.annotation.Nullable;
import org.jclouds.logging.Logger;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.io.Files;
import com.google.inject.Singleton;

@Singleton
public class VagrantDefaultImageCredentials implements PopulateDefaultLoginCredentialsForImageStrategy {

   @Resource
   protected Logger logger = Logger.NULL;

    protected final LoginCredentials creds;
    protected final Map<String, Credentials> credentialStore;
    protected final Map<OsFamily, LoginCredentials> osFamilyToCredentials;

    @Inject
    VagrantDefaultImageCredentials(@Nullable @Named("image") LoginCredentials creds,
             Map<String, Credentials> credentialStore, Map<OsFamily, LoginCredentials> osFamilyToCredentials) {
       this.creds = creds;
       this.credentialStore = credentialStore;
       this.osFamilyToCredentials = osFamilyToCredentials;
    }

    @Override
    public LoginCredentials apply(Object resourceToAuthenticate) {
        checkState(resourceToAuthenticate instanceof Image, "this is only valid for images, not %s",
                resourceToAuthenticate.getClass().getSimpleName());
       if (creds != null)
          return creds;
       Image image = Image.class.cast(resourceToAuthenticate);
       if (credentialStore.containsKey("image#" + image.getId()))
          return LoginCredentials.fromCredentials(credentialStore.get("image#" + image.getId()));
       if (image.getOperatingSystem() != null && image.getOperatingSystem().getFamily() != null
                && osFamilyToCredentials.containsKey(image.getOperatingSystem().getFamily())) {
          return osFamilyToCredentials.get(image.getOperatingSystem().getFamily());
       } else {
          File boxPath = getImagePath(image);
          String config = readBoxConfig(boxPath);
          return parseBoxCredentials(boxPath, config);
       }
    }

    private LoginCredentials parseBoxCredentials(File boxPath, String config) {
       String username = getKey(config, ".ssh.username").or("vagrant");
       Builder credBuilder = LoginCredentials.builder().user(username);
       Optional<String> password = getKey(config, ".ssh.password");
       if (password.isPresent()) {
          credBuilder.password(password.get());
       }
       Optional<String> privateKeyPath = getKey(config, ".ssh.private_key_path");
       if (privateKeyPath.isPresent()) {
          File privateKey = new File(boxPath, privateKeyPath.get());
          if (privateKey.exists()) {
             try {
               credBuilder.privateKey(Files.toString(privateKey, Charsets.UTF_8));
            } catch (IOException e) {
               throw new IllegalStateException("Failure reading private key file " +
                     privateKey.getAbsolutePath() + " for box " + boxPath.getAbsolutePath());
            }
          } else {
             logger.warn("Private key " + privateKeyPath.get() + " for box " +
                   boxPath.getAbsolutePath() + " not found at " + privateKey.getAbsolutePath() + ". Ignoring.");
          }
       }
       return credBuilder.build();
     }

    private Optional<String> getKey(String config, String key) {
       String keyQuoted = Pattern.quote(key);
       String search = keyQuoted + "\\s*=\\s*\"(.*)\"";
       Matcher matcher = Pattern.compile(search).matcher(config);
       if (matcher.find()) {
          return Optional.of(matcher.group(1));
       } else {
          return Optional.absent();
       }
    }

   private String readBoxConfig(File boxPath) {
        if (!boxPath.exists()) return "";
        try {
           return Files.toString(new File(boxPath, "Vagrantfile"), Charsets.UTF_8);
        } catch (IOException e) {
           throw new RuntimeException(e);
        }
     }
     private File getImagePath(Image image) {
        File boxes = new File(getVagrantHome(), "boxes");
        File boxPath = new File(boxes, getPathName(image));
        File versionPath = new File(boxPath, image.getVersion());
        File providerPath = new File(versionPath, image.getUserMetadata().get("provider"));
        return providerPath;
     }
     private String getPathName(Image image) {
        return image.getName().replace("/", "-VAGRANTSLASH-");
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
