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
package org.jclouds.vagrant.config;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Named;

import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadataBuilder;
import org.jclouds.compute.internal.PersistNodeCredentials;
import org.jclouds.domain.Credentials;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.javax.annotation.Nullable;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.functions.CredentialsFromAdminAccess;
import org.jclouds.vagrant.domain.MachineName;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.io.Files;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Names;

public class PersistVagrantCredentialsModule extends AbstractModule {

   static class RefreshCredentialsForNodeIfRanAdminAccess implements Function<NodeMetadata, NodeMetadata> {
      protected final Map<String, Credentials> credentialStore;
      protected final String nodeContainer;
      protected final Statement statement;

      @Inject
      protected RefreshCredentialsForNodeIfRanAdminAccess(
            @Named("vagrant.container-root") String nodeContainer,
            Map<String, Credentials> credentialStore,
            @Nullable @Assisted Statement statement) {
         this.nodeContainer = nodeContainer;
         this.credentialStore = credentialStore;
         this.statement = statement;
      }

        @Override
        public NodeMetadata apply(NodeMetadata input) {
           if (statement == null)
              return input;
           Credentials credentials = CredentialsFromAdminAccess.INSTANCE.apply(statement);
           if (credentials != null) {
              LoginCredentials creds = LoginCredentials.fromCredentials(credentials);
              input = NodeMetadataBuilder.fromNodeMetadata(input).credentials(creds).build();
              credentialStore.put("node#" + input.getId(), input.getCredentials());
              updateMachine(nodeContainer, input.getId(), creds);
           }
           return input;
        }

     }

     static class RefreshCredentialsForNode extends RefreshCredentialsForNodeIfRanAdminAccess {

        @Inject
        public RefreshCredentialsForNode(
            @Named("vagrant.container-root") String nodeContainer,
            Map<String, Credentials> credentialStore,
            @Assisted @Nullable Statement statement) {
           super(nodeContainer, credentialStore, statement);
        }

        @Override
        public NodeMetadata apply(NodeMetadata input) {
           input = super.apply(input);
           if (input.getCredentials() != null) {
              credentialStore.put("node#" + input.getId(), input.getCredentials());
              updateMachine(nodeContainer, input.getId(), input.getCredentials());
           }
           return input;
        }

     }

     private static void updateMachine(String nodeContainer, String id, LoginCredentials credentials) {
        MachineName machineName = new MachineName(id);
        File machines = new File(nodeContainer, machineName.getGroup() + "/machines");
        File machineConfig = new File(machines, machineName.getName() + ".yaml");

        String str = readFile(machineConfig);
        Map<String, String> config = new HashMap<String, String>(Splitter.on('\n').trimResults().withKeyValueSeparator(':').split(str.trim()));
        config.remove("username");
        config.remove("password");
        config.remove("private_key_path");
        config.put("username", credentials.getUser());
        if (credentials.getOptionalPassword().isPresent()) {
            config.put("password", credentials.getOptionalPassword().get());
        }
        if (credentials.getOptionalPrivateKey().isPresent()) {
            File privateKeyFile = new File(machineConfig.getParentFile(), machineName.getName() + "." + credentials.getUser() + ".key");
            config.put("private_key_path", privateKeyFile.getAbsolutePath());
            write(privateKeyFile, credentials.getOptionalPrivateKey().get());
        }

        write(machineConfig, config);
     }

    private static String readFile(File machineConfig) {
        try {
            return Files.toString(machineConfig, Charsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Can't read machine chonfig " + machineConfig.getAbsolutePath(), e);
        }
    }

    private static void write(File machineConfig, Map<String, String> config) {
        StringBuilder buff = new StringBuilder();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            buff.append(entry.getKey())
                .append(": ")
                .append(entry.getValue().trim())
                .append("\n");
        }
        write(machineConfig, buff.toString());
    }

    private static void write(File path, String config) {
        Charset encoding = Charset.forName("UTF-8");

        try {
            // write the config manually, no need to pull dependencies for now
            BufferedWriter out;
            FileOutputStream fileOut = new FileOutputStream(path);
            out = new BufferedWriter(new OutputStreamWriter(fileOut, encoding));
    
            try {
                out.write(config);
            } finally {
                out.close();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Can't update file " + path.getAbsolutePath(), e);
        }
    }

    @Override
    protected void configure() {
       install(new FactoryModuleBuilder()
             .implement(new TypeLiteral<Function<NodeMetadata, NodeMetadata>>() {},
                     Names.named("ifAdminAccess"),
                     RefreshCredentialsForNodeIfRanAdminAccess.class)
             .implement(new TypeLiteral<Function<NodeMetadata, NodeMetadata>>() {},
                     Names.named("always"),
                     RefreshCredentialsForNode.class)
             .build(PersistNodeCredentials.class));
    }

}
