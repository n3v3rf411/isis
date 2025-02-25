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

package org.apache.isis.applib.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.jdo.annotations.NotPersistent;

import org.apache.isis.applib.services.eventbus.PropertyDomainEvent;
import org.apache.isis.applib.spec.Specification;

/**
 * Domain semantics for domain object property.
 */
@Inherited
@Target({ ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Property {

    /**
     * Indicates that changes to the property that should be posted to the
     * {@link org.apache.isis.applib.services.eventbus.EventBusService event bus} using a custom (subclass of)
     * {@link org.apache.isis.applib.services.eventbus.PropertyDomainEvent}.
     *
     * <p>For example:
     * </p>
     *
     * <pre>
     * public static class StartDateChanged extends PropertyDomainEvent { ... }
     *
     * &#64;Property(domainEvent=StartDateChanged.class)
     * public LocalDate getStartDate() { ...}
     * </pre>
     *
     * <p>
     * Only domain services should be registered as subscribers; only domain services are guaranteed to be instantiated
     * and resident in memory.  The typical implementation of a domain service subscriber is to identify the impacted
     * entities, load them using a repository, and then to delegate to the event to them.
     * </p>
     *
     * <p>
     * This subclass must provide a no-arg constructor; the fields are set reflectively.
     * </p>
     */
    Class<? extends PropertyDomainEvent<?,?>> domainEvent() default PropertyDomainEvent.Default.class;


    // //////////////////////////////////////

    /**
     * Indicates where the property is not visible to the user.
     */
    Where hidden() default Where.NOWHERE;


    // //////////////////////////////////////

    /**
     * Whether the properties of this domain object can be edited, or collections of this object be added to/removed from.
     *
     * <p>
     *     Note that non-editable objects can nevertheless have actions invoked upon them.
     * </p>
     */
    Editing editing() default Editing.AS_CONFIGURED;

    /**
     * If {@link #editing()} is set to {@link Editing#DISABLED},
     * then the reason to provide to the user as to why this property cannot be edited.
     */
    String editingDisabledReason() default "";


    // //////////////////////////////////////


    /**
     * The maximum entry length of a field.
     *
     * <p>
     *     The default value (<code>-1</code>) indicates that no maxLength has been specified.
     * </p>
     */
    int maxLength() default -1;

    // //////////////////////////////////////


    /**
     * The {@link org.apache.isis.applib.spec.Specification}(s) to be satisfied by this property.
     *
     * <p>
     * If more than one is provided, then all must be satisfied (in effect &quot;AND&quot;ed together).
     * </p>
     */
    Class<? extends Specification>[] mustSatisfy() default {};


    // //////////////////////////////////////

    /**
     * Indicates that the property should be excluded from snapshots.
     *
     * <p>
     *     To ensure that the property is actually not persisted in the objectstore, also annotate with {@link NotPersistent}.
     * </p>
     */
    boolean notPersisted() default false;


    // //////////////////////////////////////

    /**
     * Whether this property is optional or is mandatory (ie required).
     *
     * <p>
     *     For properties the default value, {@link org.apache.isis.applib.annotation.Optionality#DEFAULT}, usually
     *     means that the property is required unless it has been overridden by {@link javax.jdo.annotations.Column}
     *     with its {@link javax.jdo.annotations.Column#allowsNull() allowNulls()} attribute set to true.
     * </p>
     */
    Optionality optionality() default Optionality.DEFAULT;


    // //////////////////////////////////////

    /**
     * Regular expression pattern that a value should conform to, and can be formatted as.
     */
    String regexPattern() default "";

    /**
     * Pattern flags, as per {@link java.util.regex.Pattern#compile(String, int)} .
     *
     * <p>
     *     The default value, <code>0</code>, means that no flags have been specified.
     * </p>
     */
    int regexPatternFlags() default 0;


}