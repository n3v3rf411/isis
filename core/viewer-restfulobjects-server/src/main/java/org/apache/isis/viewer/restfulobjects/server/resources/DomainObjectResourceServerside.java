/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.isis.viewer.restfulobjects.server.resources;

import java.io.InputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.isis.applib.annotation.Where;
import org.apache.isis.core.commons.url.UrlEncodingUtils;
import org.apache.isis.core.metamodel.adapter.ObjectAdapter;
import org.apache.isis.core.metamodel.consent.Consent;
import org.apache.isis.core.metamodel.consent.InteractionInitiatedBy;
import org.apache.isis.core.metamodel.spec.ObjectSpecId;
import org.apache.isis.core.metamodel.spec.ObjectSpecification;
import org.apache.isis.core.metamodel.spec.feature.OneToManyAssociation;
import org.apache.isis.core.metamodel.spec.feature.OneToOneAssociation;
import org.apache.isis.viewer.restfulobjects.applib.JsonRepresentation;
import org.apache.isis.viewer.restfulobjects.applib.RepresentationType;
import org.apache.isis.viewer.restfulobjects.applib.RestfulMediaType;
import org.apache.isis.viewer.restfulobjects.applib.client.RestfulResponse;
import org.apache.isis.viewer.restfulobjects.applib.client.RestfulResponse.HttpStatusCode;
import org.apache.isis.viewer.restfulobjects.applib.domainobjects.DomainObjectResource;
import org.apache.isis.viewer.restfulobjects.rendering.RestfulObjectsApplicationException;
import org.apache.isis.viewer.restfulobjects.rendering.domainobjects.MemberReprMode;
import org.apache.isis.viewer.restfulobjects.rendering.service.RepresentationService;
import org.apache.isis.viewer.restfulobjects.rendering.service.conneg.PrettyPrinting;
import org.apache.isis.viewer.restfulobjects.rendering.util.Util;

@Path("/objects")
public class DomainObjectResourceServerside extends ResourceAbstract implements DomainObjectResource {

    // //////////////////////////////////////////////////////////
    // persist
    // //////////////////////////////////////////////////////////

    @Override
    @POST
    @Path("/{domainType}")
    @Consumes({ MediaType.WILDCARD })
    @Produces({
            MediaType.APPLICATION_JSON, RestfulMediaType.APPLICATION_JSON_OBJECT, RestfulMediaType.APPLICATION_JSON_ERROR,
            MediaType.APPLICATION_XML, RestfulMediaType.APPLICATION_XML_OBJECT, RestfulMediaType.APPLICATION_XML_ERROR
    })
    public Response persist(@PathParam("domainType") String domainType, final InputStream object) {

        init(RepresentationType.DOMAIN_OBJECT, Where.OBJECT_FORMS);

        final String objectStr = Util.asStringUtf8(object);
        final JsonRepresentation objectRepr = Util.readAsMap(objectStr);
        if (!objectRepr.isMap()) {
            throw RestfulObjectsApplicationException.createWithMessage(HttpStatusCode.BAD_REQUEST, "Body is not a map; got %s", objectRepr);
        }

        final ObjectSpecification domainTypeSpec = getSpecificationLoader().lookupBySpecId(ObjectSpecId.of(domainType));
        if (domainTypeSpec == null) {
            throw RestfulObjectsApplicationException.createWithMessage(HttpStatusCode.BAD_REQUEST, "Could not determine type of domain object to persist (no class with domainType Id of '%s')", domainType);
        }

        final ObjectAdapter objectAdapter = getResourceContext().getPersistenceSession().createTransientInstance(domainTypeSpec);

        final ObjectAdapterUpdateHelper updateHelper = new ObjectAdapterUpdateHelper(getResourceContext(), objectAdapter);

        final JsonRepresentation membersMap = objectRepr.getMap("members");
        if (membersMap == null) {
            throw RestfulObjectsApplicationException.createWithMessage(HttpStatusCode.BAD_REQUEST, "Could not find members map; got %s", objectRepr);
        }

        if (!updateHelper.copyOverProperties(membersMap, ObjectAdapterUpdateHelper.Intent.PERSISTING_NEW)) {
            throw RestfulObjectsApplicationException.createWithBody(HttpStatusCode.BAD_REQUEST, objectRepr, "Illegal property value");
        }

        final Consent validity = objectAdapter.getSpecification().isValid(objectAdapter, InteractionInitiatedBy.USER);
        if (validity.isVetoed()) {
            throw RestfulObjectsApplicationException.createWithBody(HttpStatusCode.BAD_REQUEST, objectRepr, validity.getReason());
        }
        getResourceContext().getPersistenceSession().makePersistentInTransaction(objectAdapter);

        return getDomainResourceHelper(objectAdapter).objectRepresentation(RepresentationService.Intent.JUST_CREATED);
    }


    // //////////////////////////////////////////////////////////
    // domain object
    // //////////////////////////////////////////////////////////

    @Override
    @GET
    @Path("/{domainType}/{instanceId}")
    @Consumes({ MediaType.WILDCARD })
    @Produces({
            MediaType.APPLICATION_JSON, RestfulMediaType.APPLICATION_JSON_OBJECT, RestfulMediaType.APPLICATION_JSON_ERROR,
            MediaType.APPLICATION_XML, RestfulMediaType.APPLICATION_XML_OBJECT, RestfulMediaType.APPLICATION_XML_ERROR
    })
    @PrettyPrinting
    public Response object(@PathParam("domainType") String domainType, @PathParam("instanceId") final String instanceId) {
        init(RepresentationType.DOMAIN_OBJECT, Where.OBJECT_FORMS);

        final ObjectAdapter objectAdapter = getObjectAdapterElseThrowNotFound(domainType, instanceId);

        return getDomainResourceHelper(objectAdapter).objectRepresentation();
    }

    private DomainResourceHelper getDomainResourceHelper(final ObjectAdapter objectAdapter) {
        return new DomainResourceHelper(getResourceContext(), objectAdapter);
    }


    @Override
    @PUT
    @Path("/{domainType}/{instanceId}")
    @Consumes({ MediaType.WILDCARD })
    @Produces({
            MediaType.APPLICATION_JSON, RestfulMediaType.APPLICATION_JSON_OBJECT, RestfulMediaType.APPLICATION_JSON_ERROR,
            MediaType.APPLICATION_XML, RestfulMediaType.APPLICATION_XML_OBJECT, RestfulMediaType.APPLICATION_XML_ERROR
    })
    @PrettyPrinting
    public Response object(@PathParam("domainType") String domainType, @PathParam("instanceId") final String instanceId, final InputStream object) {

        init(RepresentationType.DOMAIN_OBJECT, Where.OBJECT_FORMS);

        final String objectStr = Util.asStringUtf8(object);
        final JsonRepresentation argRepr = Util.readAsMap(objectStr);
        if (!argRepr.isMap()) {
            throw RestfulObjectsApplicationException.createWithMessage(HttpStatusCode.BAD_REQUEST, "Body is not a map; got %s", argRepr);
        }

        final ObjectAdapter objectAdapter = getObjectAdapterElseThrowNotFound(domainType, instanceId);
        final ObjectAdapterUpdateHelper updateHelper = new ObjectAdapterUpdateHelper(getResourceContext(), objectAdapter);

        if (!updateHelper.copyOverProperties(argRepr, ObjectAdapterUpdateHelper.Intent.UPDATE_EXISTING)) {
            throw RestfulObjectsApplicationException.createWithBody(HttpStatusCode.BAD_REQUEST, argRepr, "Illegal property value");
        }

        final Consent validity = objectAdapter.getSpecification().isValid(objectAdapter, InteractionInitiatedBy.USER);
        if (validity.isVetoed()) {
            throw RestfulObjectsApplicationException.createWithBody(HttpStatusCode.BAD_REQUEST, argRepr, validity.getReason());
        }

        return getDomainResourceHelper(objectAdapter).objectRepresentation();
    }

    @Override
    public Response deleteMethodNotSupported(@PathParam("domainType") String domainType, @PathParam("instanceId") String instanceId) {
        throw RestfulObjectsApplicationException.createWithMessage(HttpStatusCode.METHOD_NOT_ALLOWED, "Deleting objects is not supported.");
    }

    @Override
    public Response postMethodNotAllowed(@PathParam("domainType") String domainType, @PathParam("instanceId") String instanceId) {
        throw RestfulObjectsApplicationException.createWithMessage(HttpStatusCode.METHOD_NOT_ALLOWED, "Posting to object resource is not allowed.");
    }

    // //////////////////////////////////////////////////////////
    // domain object property
    // //////////////////////////////////////////////////////////

    @Override
    @GET
    @Path("/{domainType}/{instanceId}/properties/{propertyId}")
    @Consumes({ MediaType.WILDCARD })
    @Produces({
            MediaType.APPLICATION_JSON, RestfulMediaType.APPLICATION_JSON_OBJECT_PROPERTY, RestfulMediaType.APPLICATION_JSON_ERROR,
            MediaType.APPLICATION_XML, RestfulMediaType.APPLICATION_XML_OBJECT_PROPERTY, RestfulMediaType.APPLICATION_XML_ERROR
    })
    @PrettyPrinting
    public Response propertyDetails(@PathParam("domainType") String domainType, @PathParam("instanceId") final String instanceId, @PathParam("propertyId") final String propertyId) {
        init(RepresentationType.OBJECT_PROPERTY, Where.OBJECT_FORMS);

        final ObjectAdapter objectAdapter = getObjectAdapterElseThrowNotFound(domainType, instanceId);
        final DomainResourceHelper helper = getDomainResourceHelper(objectAdapter);

        return helper.propertyDetails(
                propertyId,
                MemberReprMode.READ
        );
    }

    @Override
    @PUT
    @Path("/{domainType}/{instanceId}/properties/{propertyId}")
    @Consumes({ MediaType.WILDCARD })
    @Produces({
            MediaType.APPLICATION_JSON, RestfulMediaType.APPLICATION_JSON_OBJECT_PROPERTY, RestfulMediaType.APPLICATION_JSON_ERROR,
            MediaType.APPLICATION_XML, RestfulMediaType.APPLICATION_XML_OBJECT_PROPERTY, RestfulMediaType.APPLICATION_XML_ERROR
    })
    public Response modifyProperty(@PathParam("domainType") String domainType, @PathParam("instanceId") final String instanceId, @PathParam("propertyId") final String propertyId, final InputStream body) {
        init(Where.OBJECT_FORMS);

        final ObjectAdapter objectAdapter = getObjectAdapterElseThrowNotFound(domainType, instanceId);
        final DomainResourceHelper helper = getDomainResourceHelper(objectAdapter);
        final ObjectAdapterAccessHelper accessHelper = new ObjectAdapterAccessHelper(getResourceContext(), objectAdapter);

        final OneToOneAssociation property = accessHelper.getPropertyThatIsVisibleForIntent(propertyId,
                ObjectAdapterAccessHelper.Intent.MUTATE);

        final ObjectSpecification propertySpec = property.getSpecification();
        final String bodyAsString = Util.asStringUtf8(body);

        final ObjectAdapter argAdapter = new JsonParserHelper(getResourceContext(), propertySpec).parseAsMapWithSingleValue(
                bodyAsString);

        final Consent consent = property.isAssociationValid(objectAdapter, argAdapter, InteractionInitiatedBy.USER);
        if (consent.isVetoed()) {
            throw RestfulObjectsApplicationException.createWithMessage(HttpStatusCode.UNAUTHORIZED, consent.getReason());
        }

        property.set(objectAdapter, argAdapter, InteractionInitiatedBy.USER);

        return helper.propertyDetails(
                propertyId,
                MemberReprMode.WRITE
        );
    }

    @Override
    @DELETE
    @Path("/{domainType}/{instanceId}/properties/{propertyId}")
    @Consumes({ MediaType.WILDCARD })
    @Produces({
            MediaType.APPLICATION_JSON, RestfulMediaType.APPLICATION_JSON_OBJECT_PROPERTY, RestfulMediaType.APPLICATION_JSON_ERROR,
            MediaType.APPLICATION_XML, RestfulMediaType.APPLICATION_XML_OBJECT_PROPERTY, RestfulMediaType.APPLICATION_XML_ERROR
    })
    public Response clearProperty(@PathParam("domainType") String domainType, @PathParam("instanceId") final String instanceId, @PathParam("propertyId") final String propertyId) {
        init(Where.OBJECT_FORMS);

        final ObjectAdapter objectAdapter = getObjectAdapterElseThrowNotFound(domainType, instanceId);
        final DomainResourceHelper helper = getDomainResourceHelper(objectAdapter);
        final ObjectAdapterAccessHelper accessHelper = new ObjectAdapterAccessHelper(getResourceContext(), objectAdapter);

        final OneToOneAssociation property = accessHelper.getPropertyThatIsVisibleForIntent(
                propertyId, ObjectAdapterAccessHelper.Intent.MUTATE);

        final Consent consent = property.isAssociationValid(objectAdapter, null, InteractionInitiatedBy.USER);
        if (consent.isVetoed()) {
            throw RestfulObjectsApplicationException.createWithMessage(HttpStatusCode.UNAUTHORIZED, consent.getReason());
        }

        property.set(objectAdapter, null, InteractionInitiatedBy.USER);

        return helper.propertyDetails(
                propertyId,
                MemberReprMode.WRITE
        );
    }

    @Override
    public Response postPropertyNotAllowed(@PathParam("domainType") String domainType, @PathParam("instanceId") String instanceId, @PathParam("propertyId") String propertyId) {
        throw RestfulObjectsApplicationException.createWithMessage(HttpStatusCode.METHOD_NOT_ALLOWED, "Posting to a property resource is not allowed.");
    }

    // //////////////////////////////////////////////////////////
    // domain object collection
    // //////////////////////////////////////////////////////////

    @Override
    @GET
    @Path("/{domainType}/{instanceId}/collections/{collectionId}")
    @Consumes({ MediaType.WILDCARD })
    @Produces({
            MediaType.APPLICATION_JSON, RestfulMediaType.APPLICATION_JSON_OBJECT_COLLECTION, RestfulMediaType.APPLICATION_JSON_ERROR,
            MediaType.APPLICATION_XML, RestfulMediaType.APPLICATION_XML_OBJECT_COLLECTION, RestfulMediaType.APPLICATION_XML_ERROR
    })
    @PrettyPrinting
    public Response accessCollection(@PathParam("domainType") String domainType, @PathParam("instanceId") final String instanceId, @PathParam("collectionId") final String collectionId) {
        init(RepresentationType.OBJECT_COLLECTION, Where.PARENTED_TABLES);

        final ObjectAdapter objectAdapter = getObjectAdapterElseThrowNotFound(domainType, instanceId);

        return getDomainResourceHelper(objectAdapter).collectionDetails(collectionId, MemberReprMode.READ);
    }

    @Override
    @PUT
    @Path("/{domainType}/{instanceId}/collections/{collectionId}")
    @Consumes({ MediaType.WILDCARD })
    @Produces({
            MediaType.APPLICATION_JSON, RestfulMediaType.APPLICATION_JSON_OBJECT_COLLECTION, RestfulMediaType.APPLICATION_JSON_ERROR,
            MediaType.APPLICATION_XML, RestfulMediaType.APPLICATION_XML_OBJECT_COLLECTION, RestfulMediaType.APPLICATION_XML_ERROR
    })
    public Response addToSet(@PathParam("domainType") String domainType, @PathParam("instanceId") final String instanceId, @PathParam("collectionId") final String collectionId, final InputStream body) {
        init(Where.PARENTED_TABLES);

        final ObjectAdapter objectAdapter = getObjectAdapterElseThrowNotFound(domainType, instanceId);
        final DomainResourceHelper helper = getDomainResourceHelper(objectAdapter);
        final ObjectAdapterAccessHelper accessHelper = new ObjectAdapterAccessHelper(getResourceContext(), objectAdapter);

        final OneToManyAssociation collection = accessHelper.getCollectionThatIsVisibleForIntent(
                collectionId, ObjectAdapterAccessHelper.Intent.MUTATE);

        if (!collection.getCollectionSemantics().isSet()) {
            throw RestfulObjectsApplicationException.createWithMessage(HttpStatusCode.BAD_REQUEST, "Collection '%s' does not have set semantics", collectionId);
        }

        final ObjectSpecification collectionSpec = collection.getSpecification();
        final String bodyAsString = Util.asStringUtf8(body);
        final ObjectAdapter argAdapter = new JsonParserHelper(getResourceContext(), collectionSpec).parseAsMapWithSingleValue(
                bodyAsString);

        final Consent consent = collection.isValidToAdd(objectAdapter, argAdapter, InteractionInitiatedBy.USER);
        if (consent.isVetoed()) {
            throw RestfulObjectsApplicationException.createWithMessage(HttpStatusCode.UNAUTHORIZED, consent.getReason());
        }

        collection.addElement(objectAdapter, argAdapter, InteractionInitiatedBy.USER);

        return helper.collectionDetails(collectionId, MemberReprMode.WRITE);
    }

    @Override
    @POST
    @Path("/{domainType}/{instanceId}/collections/{collectionId}")
    @Consumes({ MediaType.WILDCARD })
    @Produces({
            MediaType.APPLICATION_JSON, RestfulMediaType.APPLICATION_JSON_OBJECT_COLLECTION, RestfulMediaType.APPLICATION_JSON_ERROR,
            MediaType.APPLICATION_XML, RestfulMediaType.APPLICATION_XML_OBJECT_COLLECTION, RestfulMediaType.APPLICATION_XML_ERROR
    })
    public Response addToList(@PathParam("domainType") String domainType, @PathParam("instanceId") final String instanceId, @PathParam("collectionId") final String collectionId, final InputStream body) {
        init(Where.PARENTED_TABLES);

        final ObjectAdapter objectAdapter = getObjectAdapterElseThrowNotFound(domainType, instanceId);
        final DomainResourceHelper helper = getDomainResourceHelper(objectAdapter);
        final ObjectAdapterAccessHelper accessHelper = new ObjectAdapterAccessHelper(getResourceContext(), objectAdapter);

        final OneToManyAssociation collection = accessHelper.getCollectionThatIsVisibleForIntent(
                collectionId, ObjectAdapterAccessHelper.Intent.MUTATE);

        if (!collection.getCollectionSemantics().isListOrArray()) {
            throw RestfulObjectsApplicationException.createWithMessage(HttpStatusCode.METHOD_NOT_ALLOWED, "Collection '%s' does not have list or array semantics", collectionId);
        }

        final ObjectSpecification collectionSpec = collection.getSpecification();
        final String bodyAsString = Util.asStringUtf8(body);
        final ObjectAdapter argAdapter = new JsonParserHelper(getResourceContext(), collectionSpec).parseAsMapWithSingleValue(
                bodyAsString);

        final Consent consent = collection.isValidToAdd(objectAdapter, argAdapter, InteractionInitiatedBy.USER);
        if (consent.isVetoed()) {
            throw RestfulObjectsApplicationException.createWithMessage(HttpStatusCode.UNAUTHORIZED, consent.getReason());
        }

        collection.addElement(objectAdapter, argAdapter, InteractionInitiatedBy.USER);

        return helper.collectionDetails(collectionId, MemberReprMode.WRITE);
    }

    @Override
    @DELETE
    @Path("/{domainType}/{instanceId}/collections/{collectionId}")
    @Consumes({ MediaType.WILDCARD })
    @Produces({
            MediaType.APPLICATION_JSON, RestfulMediaType.APPLICATION_JSON_OBJECT_COLLECTION, RestfulMediaType.APPLICATION_JSON_ERROR,
            MediaType.APPLICATION_XML, RestfulMediaType.APPLICATION_XML_OBJECT_COLLECTION, RestfulMediaType.APPLICATION_XML_ERROR
    })
    public Response removeFromCollection(@PathParam("domainType") String domainType, @PathParam("instanceId") final String instanceId, @PathParam("collectionId") final String collectionId) {
        init(Where.PARENTED_TABLES);

        final ObjectAdapter objectAdapter = getObjectAdapterElseThrowNotFound(domainType, instanceId);
        final DomainResourceHelper helper = getDomainResourceHelper(objectAdapter);
        final ObjectAdapterAccessHelper accessHelper = new ObjectAdapterAccessHelper(getResourceContext(), objectAdapter);

        final OneToManyAssociation collection = accessHelper.getCollectionThatIsVisibleForIntent(
                collectionId, ObjectAdapterAccessHelper.Intent.MUTATE);

        final ObjectSpecification collectionSpec = collection.getSpecification();
        final ObjectAdapter argAdapter = new JsonParserHelper(getResourceContext(), collectionSpec).parseAsMapWithSingleValue(
                getResourceContext().getUrlUnencodedQueryString());

        final Consent consent = collection.isValidToRemove(objectAdapter, argAdapter, InteractionInitiatedBy.USER);
        if (consent.isVetoed()) {
            throw RestfulObjectsApplicationException.createWithMessage(HttpStatusCode.UNAUTHORIZED, consent.getReason());
        }

        collection.removeElement(objectAdapter, argAdapter, InteractionInitiatedBy.USER);

        return helper.collectionDetails(collectionId, MemberReprMode.WRITE);
    }

    // //////////////////////////////////////////////////////////
    // domain object action
    // //////////////////////////////////////////////////////////

    @Override
    @GET
    @Path("/{domainType}/{instanceId}/actions/{actionId}")
    @Consumes({ MediaType.WILDCARD })
    @Produces({
            MediaType.APPLICATION_JSON, RestfulMediaType.APPLICATION_JSON_OBJECT_ACTION, RestfulMediaType.APPLICATION_JSON_ERROR,
            MediaType.APPLICATION_XML, RestfulMediaType.APPLICATION_XML_OBJECT_ACTION, RestfulMediaType.APPLICATION_XML_ERROR
    })
    @PrettyPrinting
    public Response actionPrompt(@PathParam("domainType") String domainType, @PathParam("instanceId") final String instanceId, @PathParam("actionId") final String actionId) {
        init(RepresentationType.OBJECT_ACTION, Where.OBJECT_FORMS);

        final ObjectAdapter objectAdapter = getObjectAdapterElseThrowNotFound(domainType, instanceId);
        final DomainResourceHelper helper = getDomainResourceHelper(objectAdapter);

        return helper.actionPrompt(actionId);
    }

    @Override
    public Response deleteActionPromptNotAllowed(@PathParam("domainType") String domainType, @PathParam("instanceId") String instanceId, @PathParam("actionId") String actionId) {
        throw RestfulObjectsApplicationException.createWithMessage(HttpStatusCode.METHOD_NOT_ALLOWED, "Deleting action prompt resource is not allowed.");
    }

    @Override
    public Response postActionPromptNotAllowed(@PathParam("domainType") String domainType, @PathParam("instanceId") String instanceId, @PathParam("actionId") String actionId) {
        throw RestfulObjectsApplicationException.createWithMessage(HttpStatusCode.METHOD_NOT_ALLOWED, "Posting to an action prompt resource is not allowed.");
    }

    @Override
    public Response putActionPromptNotAllowed(@PathParam("domainType") String domainType, @PathParam("instanceId") String instanceId, @PathParam("actionId") String actionId) {
        throw RestfulObjectsApplicationException.createWithMessage(HttpStatusCode.METHOD_NOT_ALLOWED, "Putting to an action prompt resource is not allowed.");
    }

    // //////////////////////////////////////////////////////////
    // domain object action invoke
    // //////////////////////////////////////////////////////////

    @Override
    @GET
    @Path("/{domainType}/{instanceId}/actions/{actionId}/invoke")
    @Consumes({ MediaType.WILDCARD })
    @Produces({
            MediaType.APPLICATION_JSON, RestfulMediaType.APPLICATION_JSON_ACTION_RESULT, RestfulMediaType.APPLICATION_JSON_ERROR,
            MediaType.APPLICATION_XML, RestfulMediaType.APPLICATION_XML_ACTION_RESULT, RestfulMediaType.APPLICATION_XML_ERROR
    })
    @PrettyPrinting
    public Response invokeActionQueryOnly(
            final @PathParam("domainType") String domainType,
            final @PathParam("instanceId") String instanceId,
            final @PathParam("actionId") String actionId,
            final @QueryParam("x-isis-querystring") String xIsisUrlEncodedQueryString) {

        final String urlUnencodedQueryString = UrlEncodingUtils.urlDecodeNullSafe(xIsisUrlEncodedQueryString != null? xIsisUrlEncodedQueryString: httpServletRequest.getQueryString());
        init(RepresentationType.ACTION_RESULT, Where.STANDALONE_TABLES, urlUnencodedQueryString);

        final JsonRepresentation arguments = getResourceContext().getQueryStringAsJsonRepr();

        final ObjectAdapter objectAdapter = getObjectAdapterElseThrowNotFound(domainType, instanceId);
        final DomainResourceHelper helper = getDomainResourceHelper(objectAdapter);

        return helper.invokeActionQueryOnly(actionId, arguments);
    }

    @Override
    @PUT
    @Path("/{domainType}/{instanceId}/actions/{actionId}/invoke")
    @Consumes({ MediaType.WILDCARD })
    @Produces({
            MediaType.APPLICATION_JSON, RestfulMediaType.APPLICATION_JSON_ACTION_RESULT, RestfulMediaType.APPLICATION_JSON_ERROR,
            MediaType.APPLICATION_XML, RestfulMediaType.APPLICATION_XML_ACTION_RESULT, RestfulMediaType.APPLICATION_XML_ERROR
    })
    @PrettyPrinting
    public Response invokeActionIdempotent(
            final @PathParam("domainType") String domainType,
            final @PathParam("instanceId") String instanceId,
            final @PathParam("actionId") String actionId,
            final InputStream body) {

        init(RepresentationType.ACTION_RESULT, Where.STANDALONE_TABLES, body);

        final JsonRepresentation arguments = getResourceContext().getQueryStringAsJsonRepr();
        
        final ObjectAdapter objectAdapter = getObjectAdapterElseThrowNotFound(domainType, instanceId);
        final DomainResourceHelper helper = getDomainResourceHelper(objectAdapter);

        return helper.invokeActionIdempotent(actionId, arguments);
    }

    @Override
    @POST
    @Path("/{domainType}/{instanceId}/actions/{actionId}/invoke")
    @Consumes({ MediaType.WILDCARD })
    @Produces({
            MediaType.APPLICATION_JSON, RestfulMediaType.APPLICATION_JSON_ACTION_RESULT, RestfulMediaType.APPLICATION_JSON_ERROR,
            MediaType.APPLICATION_XML, RestfulMediaType.APPLICATION_XML_ACTION_RESULT, RestfulMediaType.APPLICATION_XML_ERROR
    })
    @PrettyPrinting
    public Response invokeAction(@PathParam("domainType") String domainType, @PathParam("instanceId") final String instanceId, @PathParam("actionId") final String actionId, final InputStream body) {
        init(RepresentationType.ACTION_RESULT, Where.STANDALONE_TABLES, body);

        final JsonRepresentation arguments = getResourceContext().getQueryStringAsJsonRepr();
        
        final ObjectAdapter objectAdapter = getObjectAdapterElseThrowNotFound(domainType, instanceId);
        final DomainResourceHelper helper = getDomainResourceHelper(objectAdapter);

        return helper.invokeAction(actionId, arguments);
    }

    @Override
    public Response deleteInvokeActionNotAllowed(@PathParam("domainType") String domainType, @PathParam("instanceId") String instanceId, @PathParam("actionId") String actionId) {
        throw RestfulObjectsApplicationException.createWithMessage(RestfulResponse.HttpStatusCode.METHOD_NOT_ALLOWED, "Deleting an action invocation resource is not allowed.");
    }

}
