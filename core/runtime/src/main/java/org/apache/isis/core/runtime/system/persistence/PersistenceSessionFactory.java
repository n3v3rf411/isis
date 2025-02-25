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

package org.apache.isis.core.runtime.system.persistence;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.jdo.PersistenceManagerFactory;

import com.google.common.collect.Maps;

import org.datanucleus.PropertyNames;
import org.datanucleus.api.jdo.JDOPersistenceManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.isis.core.commons.authentication.AuthenticationSession;
import org.apache.isis.core.commons.components.ApplicationScopedComponent;
import org.apache.isis.core.commons.config.IsisConfiguration;
import org.apache.isis.core.commons.config.IsisConfigurationDefault;
import org.apache.isis.core.metamodel.services.ServicesInjectorSpi;
import org.apache.isis.core.metamodel.spec.SpecificationLoaderSpi;
import org.apache.isis.core.runtime.persistence.FixturesInstalledFlag;
import org.apache.isis.core.runtime.system.DeploymentType;
import org.apache.isis.objectstore.jdo.datanucleus.DataNucleusPersistenceMechanismInstaller;
import org.apache.isis.objectstore.jdo.datanucleus.JDOStateManagerForIsis;
import org.apache.isis.objectstore.jdo.service.RegisterEntities;

public class PersistenceSessionFactory
        implements ApplicationScopedComponent, FixturesInstalledFlag {

    private static final Logger LOG = LoggerFactory.getLogger(PersistenceSessionFactory.class);

    //region > constructor

    private final DeploymentType deploymentType;
    private final IsisConfigurationDefault configuration;

    public PersistenceSessionFactory(
            final DeploymentType deploymentType,
            final IsisConfigurationDefault isisConfiguration) {
        this.deploymentType = deploymentType;
        this.configuration = isisConfiguration;
    }

    //endregion

    //region > init, createDataNucleusApplicationComponents

    private DataNucleusApplicationComponents applicationComponents;

    public final void init() {
        this.applicationComponents = createDataNucleusApplicationComponents(configuration);
    }


    private DataNucleusApplicationComponents createDataNucleusApplicationComponents(
            final IsisConfiguration configuration) {

        if (applicationComponents == null || applicationComponents.isStale()) {

            final IsisConfiguration jdoObjectstoreConfig = configuration.createSubset(
                    DataNucleusPersistenceMechanismInstaller. JDO_OBJECTSTORE_CONFIG_PREFIX);

            final IsisConfiguration dataNucleusConfig = configuration.createSubset(DataNucleusPersistenceMechanismInstaller.DATANUCLEUS_CONFIG_PREFIX);
            final Map<String, String> datanucleusProps = dataNucleusConfig.asMap();
            addDataNucleusPropertiesIfRequired(datanucleusProps);

            final RegisterEntities registerEntities = new RegisterEntities(configuration.asMap());
            final Set<String> classesToBePersisted = registerEntities.getEntityTypes();

            applicationComponents = new DataNucleusApplicationComponents(jdoObjectstoreConfig, datanucleusProps, classesToBePersisted);
        }

        return applicationComponents;
    }

    private static void addDataNucleusPropertiesIfRequired(
            final Map<String, String> props) {

        // new feature in DN 3.2.3; enables dependency injection into entities
        putIfNotPresent(props, PropertyNames.PROPERTY_OBJECT_PROVIDER_CLASS_NAME, JDOStateManagerForIsis.class.getName());

        putIfNotPresent(props, "javax.jdo.PersistenceManagerFactoryClass", JDOPersistenceManagerFactory.class.getName());

        // previously we defaulted this property to "true", but that could cause the target database to be modified
        putIfNotPresent(props, PropertyNames.PROPERTY_SCHEMA_AUTOCREATE_SCHEMA, Boolean.FALSE.toString());

        putIfNotPresent(props, PropertyNames.PROPERTY_SCHEMA_VALIDATE_ALL, Boolean.TRUE.toString());
        putIfNotPresent(props, PropertyNames.PROPERTY_CACHE_L2_TYPE, "none");

        putIfNotPresent(props, PropertyNames.PROPERTY_PERSISTENCE_UNIT_LOAD_CLASSES, Boolean.TRUE.toString());

        String connectionFactoryName = props.get(PropertyNames.PROPERTY_CONNECTION_FACTORY_NAME);
        if(connectionFactoryName != null) {
            String connectionFactory2Name = props.get(PropertyNames.PROPERTY_CONNECTION_FACTORY2_NAME);
            String transactionType = props.get("javax.jdo.option.TransactionType");
            if(transactionType == null) {
                LOG.info("found non-JTA JNDI datasource (" + connectionFactoryName + ")");
                if(connectionFactory2Name != null) {
                    LOG.warn("found non-JTA JNDI datasource (" + connectionFactoryName + "); second '-nontx' JNDI datasource configured but will not be used (" + connectionFactory2Name +")");
                }
            } else
                LOG.info("found JTA JNDI datasource (" + connectionFactoryName + ")");
            if(connectionFactory2Name == null) {
                // JDO/DN itself will (probably) throw an exception
                LOG.error("found JTA JNDI datasource (" + connectionFactoryName + ") but second '-nontx' JNDI datasource *not* found");
            } else {
                LOG.info("... and second '-nontx' JNDI datasource found; " + connectionFactory2Name);
            }
            // nothing further to do
            return;
        } else {
            // use JDBC connection properties; put if not present
            LOG.info("did *not* find JNDI datasource; will use JDBC");

            putIfNotPresent(props, "javax.jdo.option.ConnectionDriverName", "org.hsqldb.jdbcDriver");
            putIfNotPresent(props, "javax.jdo.option.ConnectionURL", "jdbc:hsqldb:mem:test");
            putIfNotPresent(props, "javax.jdo.option.ConnectionUserName", "sa");
            putIfNotPresent(props, "javax.jdo.option.ConnectionPassword", "");
        }
    }

    private static void putIfNotPresent(
            final Map<String, String> props,
            String key,
            String value) {
        if(!props.containsKey(key)) {
            props.put(key, value);
        }
    }
    //endregion

    //region > shutdown
    public final void shutdown() {
        // no-op
    }

    //endregion


    //region > createPersistenceSession

    /**
     * Called by {@link org.apache.isis.core.runtime.system.session.IsisSessionFactory#openSession(AuthenticationSession)}.
     */
    public PersistenceSession createPersistenceSession(
            final ServicesInjectorSpi servicesInjector,
            final SpecificationLoaderSpi specificationLoader,
            final AuthenticationSession authenticationSession) {

        final FixturesInstalledFlag fixturesInstalledFlag = this;
        final PersistenceManagerFactory persistenceManagerFactory =
                applicationComponents.getPersistenceManagerFactory();

        return new PersistenceSession(
                configuration, servicesInjector, specificationLoader,
                authenticationSession, persistenceManagerFactory,
                fixturesInstalledFlag);
    }



    //endregion

    //region > FixturesInstalledFlag impl

    private Boolean fixturesInstalled;

    @Override
    public Boolean isFixturesInstalled() {
        return fixturesInstalled;
    }

    @Override
    public void setFixturesInstalled(final Boolean fixturesInstalled) {
        this.fixturesInstalled = fixturesInstalled;
    }

    //endregion


}
