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

import java.util.Collection;
import java.util.Map;

import javax.inject.Named;

import org.jclouds.compute.domain.Image;
import org.jclouds.vagrant.api.VagrantBoxApiFacade;
import org.jclouds.vagrant.reference.VagrantConstants;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.FluentIterable;
import com.google.inject.Inject;

public class ImageSupplier<B> implements Supplier<Collection<Image>>, Function<String, Image> {
   private final String provider;
   private final Function<Collection<B>, Collection<B>> outdatedBoxesFilter;
   private final VagrantBoxApiFacade.Factory<B> cliFactory;
   private final Function<B, Image> boxToImage;

   @Inject
   ImageSupplier(
         @Named(VagrantConstants.JCLOUDS_VAGRANT_PROVIDER) String provider,
         Function<Collection<B>, Collection<B>> outdatedBoxesFilter,
         VagrantBoxApiFacade.Factory<B> cliFactory,
         Function<B, Image> boxToImage) {
      this.provider = provider.isEmpty() ? null : provider;
      this.outdatedBoxesFilter = outdatedBoxesFilter;
      this.cliFactory = cliFactory;
      this.boxToImage = boxToImage;
   }

   @Override
   public Collection<Image> get() {
      Collection<B> boxes = outdatedBoxesFilter.apply(cliFactory.create().listBoxes());
      return FluentIterable.from(boxes)
         .transform(boxToImage)
         .filter(new Predicate<Image>() {
            @Override
            public boolean apply(Image input) {
               if (provider == null) return true;

               Map<String, String> userMeta = input.getUserMetadata();
               if (userMeta == null) return false;

               return provider.equals(userMeta.get(VagrantConstants.USER_META_PROVIDER));
            }
         }).toList();
   }

   @Override
   public Image apply(String id) {
      B box = cliFactory.create().getBox(id);
      return boxToImage.apply(box);
   }

}
