/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.isis.viewer.restfulobjects.rendering;

import java.util.List;

import org.apache.isis.applib.annotation.Render;
import org.apache.isis.applib.annotation.Where;
import org.apache.isis.applib.profiles.Localization;
import org.apache.isis.core.commons.authentication.AuthenticationSession;
import org.apache.isis.core.commons.config.IsisConfiguration;
import org.apache.isis.core.metamodel.adapter.ObjectAdapter;
import org.apache.isis.core.metamodel.adapter.mgr.AdapterManager;
import org.apache.isis.core.runtime.system.persistence.PersistenceSession;
import org.apache.isis.viewer.restfulobjects.rendering.domainobjects.DomainObjectReprRenderer;

public interface RendererContext {

    public String urlFor(final String url);

    public AuthenticationSession getAuthenticationSession();

    public IsisConfiguration getConfiguration();
    
    public PersistenceSession getPersistenceSession();

    /**
     * @deprecated - replaced by {@link #getPersistenceSession()}.
     */
    @Deprecated
    public AdapterManager getAdapterManager();

    public Where getWhere();

    public List<List<String>> getFollowLinks();

    public Localization getLocalization();

    boolean honorUiHints();

    boolean objectPropertyValuesOnly();

    boolean suppressDescribedByLinks();
    boolean suppressUpdateLink();
    boolean suppressMemberId();
    boolean suppressMemberLinks();
    boolean suppressMemberExtensions();
    boolean suppressMemberDisabledReason();

    /**
     * To avoid infinite loops when {@link Render.Type#EAGERLY eagerly} rendering graphs
     * of objects as {@link DomainObjectReprRenderer#asEventSerialization() events}.
     * 
     * <p>
     * @param objectAdapter - the object proposed to be rendered eagerly 
     * @return whether this adapter has already been rendered (implying the caller should not render the value).
     */
    public boolean canEagerlyRender(ObjectAdapter objectAdapter);

}
