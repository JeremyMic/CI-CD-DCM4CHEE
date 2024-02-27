/*
 * *** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2017-2019
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.iocm.rs;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.data.*;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Priority;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.AllowDeletePatient;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.RSOperation;
import org.dcm4chee.arc.delete.DeletionService;
import org.dcm4chee.arc.entity.AttributesBlob;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.entity.PatientID;
import org.dcm4chee.arc.hl7.HL7Sender;
import org.dcm4chee.arc.hl7.HL7SenderUtils;
import org.dcm4chee.arc.id.IDService;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.patient.*;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.rs.client.RSForward;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.transform.TransformerConfigurationException;
import java.io.*;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Apr 2021
 */

@RequestScoped
@Path("aets/{AETitle}/rs")
@InvokeValidate(type = PamRS.class)
public class PamRS {
    private static final Logger LOG = LoggerFactory.getLogger(PamRS.class);
    private static final String SUPER_USER_ROLE = "super-user-role";

    @Inject
    private Device device;

    @Inject
    private IHL7ApplicationCache hl7AppCache;

    @Inject
    private IDService idService;

    @Inject
    private RSForward rsForward;

    @Inject
    private PatientService patientService;

    @Inject
    private DeletionService deletionService;

    @Inject
    private HL7Sender hl7Sender;

    @Inject
    private QueryService queryService;

    @Inject
    private CFindSCU cfindscu;

    @PathParam("AETitle")
    private String aet;

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    private Collection<IDWithIssuer> trustedPatientIDs(String multiplePatientIDs, ArchiveAEExtension arcAE) {
        String[] patientIDs = multiplePatientIDs.split("~");
        Set<IDWithIssuer> patientIdentifiers = new LinkedHashSet<>(patientIDs.length);
        for (String cx : patientIDs)
            patientIdentifiers.add(new IDWithIssuer(cx));
        return arcAE.getArchiveDeviceExtension().retainTrustedPatientIDs(patientIdentifiers);
    }

    @DELETE
    @Path("/patients/{PatientID}")
    public Response deletePatient(@PathParam("PatientID") String multiplePatientIDs) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        try {
            Collection<IDWithIssuer> trustedPatientIDs = trustedPatientIDs(multiplePatientIDs, arcAE);
            if (trustedPatientIDs.isEmpty())
                return errResponse("Missing patient identifier with trusted assigning authority in " + multiplePatientIDs,
                        Response.Status.BAD_REQUEST);

            Patient patient = patientService.findPatient(trustedPatientIDs);
            if (patient == null)
                return errResponse("Patient with patient identifiers " + trustedPatientIDs + " not found.",
                        Response.Status.NOT_FOUND);

            AllowDeletePatient allowDeletePatient = arcAE.allowDeletePatient();
            String patientDeleteForbidden = allowDeletePatient == AllowDeletePatient.NEVER
                    ? "Patient deletion as per configuration is never allowed."
                    : allowDeletePatient == AllowDeletePatient.WITHOUT_STUDIES && patient.getNumberOfStudies() > 0
                    ? "Patient with patient identifiers " + trustedPatientIDs + " has non empty studies."
                    : null;
            if (patientDeleteForbidden != null)
                return errResponse(patientDeleteForbidden, Response.Status.FORBIDDEN);

            PatientMgtContext ctx = patientService.createPatientMgtContextWEB(HttpServletRequestInfo.valueOf(request));
            ctx.setArchiveAEExtension(arcAE);
            ctx.setPatientIDs(trustedPatientIDs);
            ctx.setAttributes(patient.getAttributes());
            ctx.setEventActionCode(AuditMessages.EventActionCode.Delete);
            ctx.setPatient(patient);
            deletionService.deletePatient(ctx, arcAE);
            rsForward.forward(RSOperation.DeletePatient, arcAE, null, request);
            return Response.noContent().build();
        } catch (NonUniquePatientException e) {
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (PatientMergedException e) {
            return errResponse(e.getMessage(), Response.Status.FORBIDDEN);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("/patients")
    @Consumes({"application/dicom+json,application/json"})
    @Produces("application/json")
    public Response createPatient(InputStream in) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        try {
            PatientMgtContext ctx = patientMgtCtx(in);
            ctx.setArchiveAEExtension(arcAE);
            if (ctx.getPatientIDs().isEmpty()) {
                idService.newPatientID(ctx.getAttributes());
                ctx.setPatientIDs(IDWithIssuer.pidsOf(ctx.getAttributes()));
            }
            Collection<IDWithIssuer> patientIDs = ctx.getPatientIDs();
            ctx.setPatientIDs(arcAE.getArchiveDeviceExtension().retainTrustedPatientIDs(patientIDs));
            if (ctx.getPatientIDs().isEmpty())
                return errResponse(
                        "Missing patient identifier with trusted assigning authority in request payload " + patientIDs,
                        Response.Status.BAD_REQUEST);

            patientService.updatePatient(ctx);
            rsForward.forward(RSOperation.CreatePatient, arcAE, ctx.getAttributes(), request);
            notifyHL7Receivers("ADT^A28^ADT_A05", ctx);
            return Response.ok("{\"PatientIdentifiers\": \""
                                        + IDWithIssuer.pidsOf(ctx.getAttributes())
                                        + "\"}")
                    .build();
        } catch (NonUniquePatientException e) {
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (PatientMergedException e) {
            return errResponse(e.getMessage(), Response.Status.FORBIDDEN);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private PatientMgtContext patientMgtCtx(InputStream in) {
        PatientMgtContext ctx = patientMgtCtx();
        ctx.setAttributes(toAttributes(in));
        return ctx;
    }

    private PatientMgtContext patientMgtCtx() {
        PatientMgtContext ctx = patientService.createPatientMgtContextWEB(HttpServletRequestInfo.valueOf(request));
        ctx.setAttributeUpdatePolicy(Attributes.UpdatePolicy.REPLACE);
        return ctx;
    }

    private boolean isPatientMatch(
            Collection<IDWithIssuer> targetPatientIDs, Collection<IDWithIssuer> trustedPriorPatientIDs) {
        for (IDWithIssuer trustedPriorPatientID : trustedPriorPatientIDs)
            if (targetPatientIDs.contains(trustedPriorPatientID))
                return true;

        return false;
    }

    @PUT
    @Path("/patients/{priorPatientID}")
    @Consumes("application/dicom+json,application/json")
    public Response updatePatient(
            @PathParam("priorPatientID") String multiplePriorPatientIDs,
            @QueryParam("merge") @Pattern(regexp = "true|false") @DefaultValue("false") String merge,
            InputStream in) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        Collection<IDWithIssuer> trustedPriorPatientIDs = trustedPatientIDs(multiplePriorPatientIDs, arcAE);
        if (trustedPriorPatientIDs.isEmpty())
            return errResponse("Missing patient identifier with trusted assigning authority in " + multiplePriorPatientIDs,
                    Response.Status.BAD_REQUEST);

        PatientMgtContext ctx = patientMgtCtx(in);
        ctx.setArchiveAEExtension(arcAE);
        ctx.setPatientIDs(arcAE.getArchiveDeviceExtension().retainTrustedPatientIDs(ctx.getPatientIDs()));
        if (ctx.getPatientIDs().isEmpty())
            return errResponse(
                    "Missing patient identifier with trusted assigning authority in request payload " + ctx.getPatientIDs(),
                    Response.Status.BAD_REQUEST);

        boolean mergePatients = Boolean.parseBoolean(merge);
        boolean patientMatch = isPatientMatch(ctx.getPatientIDs(), trustedPriorPatientIDs);
        if (patientMatch && mergePatients)
            return errResponse("Circular Merge of Patients not allowed.", Response.Status.BAD_REQUEST);

        RSOperation rsOp = RSOperation.CreatePatient;
        String msgType = "ADT^A28^ADT_A05";
        try {
            if (patientMatch) {
                patientService.updatePatient(ctx);
                if (ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Update)) {
                    rsOp = RSOperation.UpdatePatient;
                    msgType = "ADT^A31^ADT_A05";
                }
            } else {
                ctx.setPreviousPatientIDs(trustedPriorPatientIDs);
                if (mergePatients) {
                    msgType = "ADT^A40^ADT_A39";
                    rsOp = RSOperation.MergePatient2;
                    patientService.mergePatient(ctx);
                } else {
                    msgType = "ADT^A47^ADT_A30";
                    rsOp = RSOperation.ChangePatientID2;
                    patientService.changePatientID(ctx);
                }
            }

            if (!ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Read)
                    || rsOp == RSOperation.MergePatient2) {
                rsForward.forward(rsOp, arcAE, ctx.getAttributes(), request);
                notifyHL7Receivers(msgType, ctx);
            }

            return Response.noContent().build();
        } catch (PatientAlreadyExistsException | NonUniquePatientException | PatientTrackingNotAllowedException
                | CircularPatientMergeException e) {
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (PatientMergedException e) {
            return errResponse(e.getMessage(), Response.Status.FORBIDDEN);
        } catch(Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("/patients/{patientID}/merge")
    @Consumes("application/json")
    public Response mergePatients(@PathParam("patientID") String multiplePatientIDs, InputStream in) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        Collection<IDWithIssuer> trustedPatientIDs = trustedPatientIDs(multiplePatientIDs, arcAE);
        if (trustedPatientIDs.isEmpty())
            return errResponse(
                    "Missing patient identifier with trusted assigning authority in " + multiplePatientIDs,
                    Response.Status.BAD_REQUEST);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) != -1)
                baos.write(buffer, 0, length);

            InputStream is1 = new ByteArrayInputStream(baos.toByteArray());
            List<Set<IDWithIssuer>> priorPatientIdentifiers = priorPatientIdentifiers(is1);
            if (priorPatientIdentifiers.isEmpty())
                return errResponse("Patients to be merged not sent in the request payload.",
                                Response.Status.BAD_REQUEST);

            boolean merged = false;
            for (Set<IDWithIssuer> priorPatientIdentifier : priorPatientIdentifiers) {
                Collection<IDWithIssuer> trustedPriorPatientIDs = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                                                                        .retainTrustedPatientIDs(priorPatientIdentifier);
                if (trustedPriorPatientIDs.isEmpty()) {
                    LOG.warn("Missing prior patient identifier with trusted assigning authority in {}",
                            trustedPriorPatientIDs);
                    continue;
                }

                try {
                    merged = mergePatient(trustedPatientIDs, trustedPriorPatientIDs, arcAE);
                } catch (Exception e) {
                    LOG.info("Failed to merge prior patient {} to target patient {}",
                            priorPatientIdentifier, trustedPatientIDs);
                }
            }
            if (merged) //forward RS only if at least one set of trusted prior patient identifiers in req payload was merged
                rsForward.forward(RSOperation.MergePatient, arcAE, baos.toByteArray(), null, request);
            return Response.noContent().build();
        } catch (JsonParsingException e) {
            return errResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.BAD_REQUEST);
        } catch (NonUniquePatientException | CircularPatientMergeException e) {
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (PatientMergedException e) {
            return errResponse(e.getMessage(), Response.Status.FORBIDDEN);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("/patients/{priorPatientID}/merge/{patientID}")
    public Response mergePatient(@PathParam("priorPatientID") String multiplePriorPatientIDs,
                             @PathParam("patientID") String multiplePatientIDs,
                             @QueryParam("verify") String findSCP) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        try {
            Collection<IDWithIssuer> trustedPriorPatientIDs = trustedPatientIDs(multiplePriorPatientIDs, arcAE);
            if (trustedPriorPatientIDs.isEmpty())
                return errResponse(
                        "Missing prior patient identifier with trusted assigning authority in " + multiplePriorPatientIDs,
                        Response.Status.BAD_REQUEST);

            Collection<IDWithIssuer> trustedPatientIDs = trustedPatientIDs(multiplePatientIDs, arcAE);
            if (trustedPatientIDs.isEmpty())
                return errResponse(
                        "Missing patient identifier with trusted assigning authority in " + multiplePatientIDs,
                        Response.Status.BAD_REQUEST);


            if (findSCP != null)
                verifyMergePatient(trustedPriorPatientIDs, trustedPatientIDs, findSCP, cfindscu, arcAE.getApplicationEntity());

            mergePatient(trustedPatientIDs, trustedPriorPatientIDs, arcAE);
            rsForward.forward(RSOperation.MergePatient, arcAE, null, request);
            return Response.noContent().build();
        } catch (NonUniquePatientException
                | CircularPatientMergeException
                | VerifyMergePatientException e) {
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (PatientMergedException e) {
            return errResponse(e.getMessage(), Response.Status.FORBIDDEN);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("/patients/{PatientID}/unmerge")
    public Response unmergePatient(@PathParam("PatientID") String multiplePriorPatientIDs) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        try {
            Collection<IDWithIssuer> trustedPriorPatientIDs = trustedPatientIDs(multiplePriorPatientIDs, arcAE);
            if (trustedPriorPatientIDs.isEmpty())
                return errResponse(
                        "Missing patient identifier with trusted assigning authority in " + multiplePriorPatientIDs,
                        Response.Status.BAD_REQUEST);

            PatientMgtContext patMgtCtx = patientService.createPatientMgtContextWEB(HttpServletRequestInfo.valueOf(request));
            patMgtCtx.setArchiveAEExtension(arcAE);
            patMgtCtx.setPatientIDs(trustedPriorPatientIDs);
            if (!patientService.unmergePatient(patMgtCtx))
                return errResponse("Patient with patient identifiers " + trustedPriorPatientIDs + " not found.",
                                    Response.Status.NOT_FOUND);

            rsForward.forward(RSOperation.UnmergePatient, arcAE, null, request);
            return Response.noContent().build();
        } catch (NonUniquePatientException | PatientUnmergedException e) {
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("/patients/issuer/{issuer}")
    public Response supplementIssuer(
            @PathParam("issuer") AttributesFormat issuer,
            @QueryParam("test") @Pattern(regexp = "true|false") @DefaultValue("false") String test) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        Set<IDWithIssuer> success = new HashSet<>();
        Map<IDWithIssuer, Long> ambiguous = new HashMap<>();
        Map<String, String> failures = new HashMap<>();
        try {
            QueryAttributes queryAttrs = new QueryAttributes(uriInfo, null);
            Attributes queryKeys = queryAttrs.getQueryKeys();
            if (queryKeys.getString(Tag.IssuerOfPatientID) != null
                    || queryKeys.getNestedDataset(Tag.IssuerOfPatientIDQualifiersSequence) != null)
                return errResponse(
                        "Issuer of Patient ID or Issuer of Patient ID Qualifiers Sequence not allowed in query filters",
                        Response.Status.BAD_REQUEST);

            CriteriaQuery<PatientID> query = queryService.createPatientIDWithUnknownIssuerQuery(
                    queryParam(arcAE.getApplicationEntity(), true), queryKeys);
            String toManyDuplicates = null;
            int supplementIssuerFetchSize = arcAE.getArchiveDeviceExtension().getSupplementIssuerFetchSize();
            boolean testIssuer = Boolean.parseBoolean(test);
            if (testIssuer) {
                patientService.testSupplementIssuers(query, supplementIssuerFetchSize, success, ambiguous, issuer);
            } else {
                Set<Long> failedPks = new HashSet<>();
                boolean remaining;
                int carry = 0;
                do {
                    int limit = supplementIssuerFetchSize + failedPks.size() + carry;
                    List<PatientID> matches = patientService.queryWithOffsetAndLimit(query, 0, limit);
                    remaining = matches.size() == limit;
                    matches.removeIf(p -> failedPks.contains(p.getPk()));
                    if (matches.isEmpty())
                        break;

                    carry = 0;
                    if (remaining) {
                        try {
                            ListIterator<PatientID> itr = matches.listIterator(matches.size());
                            toManyDuplicates = itr.previous().getID();
                            do {
                                itr.remove();
                                carry++;
                            } while (toManyDuplicates.equals(itr.previous().getID()));
                            toManyDuplicates = null;
                        } catch (NoSuchElementException e) {
                            break;
                        }
                    }
                    matches.stream()
                            .collect(Collectors.groupingBy(
                                    pid -> new IDWithIssuer(pid.getID(),issuer.format(pid.getPatient().getAttributes()))))
                            .forEach((idWithIssuer, pids) -> {
                                if (pids.size() > 1) {
                                    ambiguous.put(idWithIssuer, Long.valueOf(pids.size()));
                                    pids.stream().map(PatientID::getPk).forEach(failedPks::add);
                                } else {
                                    PatientID pid = pids.get(0);
                                    try {
                                        if (patientService.supplementIssuer(
                                                patientMgtCtx(), pid, idWithIssuer, ambiguous)) {
                                            success.add(idWithIssuer);
                                        } else {
                                            failedPks.add(pid.getPk());
                                        }
                                    } catch (Exception e) {
                                        failures.put(idWithIssuer.toString(), e.getMessage());
                                        failedPks.add(pid.getPk());
                                    }
                                }
                            });
                } while (remaining && failedPks.size() < supplementIssuerFetchSize);
                if (!success.isEmpty())
                    rsForward.forward(RSOperation.SupplementIssuer, arcAE, null, request);
            }
            return supplementIssuerResponse(success, ambiguous, failures, toManyDuplicates).build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private static Response.ResponseBuilder supplementIssuerResponse(
            Set<IDWithIssuer> success,
            Map<IDWithIssuer, Long> ambiguous,
            Map<String, String> failures,
            String toManyDuplicates) {
        boolean ok = ambiguous.isEmpty() && failures.isEmpty() && toManyDuplicates == null;
        return (ok && success.isEmpty())
                ? Response.status(Response.Status.NO_CONTENT)
                : Response.status(ok
                ? Response.Status.OK
                : success.isEmpty()
                ? Response.Status.CONFLICT
                : Response.Status.ACCEPTED)
                .entity((StreamingOutput) out -> supplementIssuerResponsePayload(
                        success, ambiguous, failures, toManyDuplicates, out));
    }

    private static void supplementIssuerResponsePayload(
            Set<IDWithIssuer> success,
            Map<IDWithIssuer, Long> ambiguous,
            Map<String, String> failures,
            String toManyDuplicates,
            OutputStream out) {
        JsonGenerator gen = Json.createGenerator(out);
        gen.writeStartObject();
        if (!success.isEmpty()) {
            gen.writeStartArray("pids");
            success.stream().map(Object::toString).forEach(gen::write);
            gen.writeEnd();
        }
        if (!ambiguous.isEmpty()) {
            gen.writeStartArray("ambiguous");
            ambiguous.forEach((idWithIssuer, count) -> {
                gen.writeStartObject();
                gen.write("pid", idWithIssuer.toString());
                gen.write("count", count);
                gen.writeEnd();
            });
            gen.writeEnd();
        }
        if (!failures.isEmpty()) {
            gen.writeStartArray("failures");
            failures.forEach((pid, errorMsg) -> {
                gen.writeStartObject();
                gen.write("pid", pid);
                gen.write("errorMessage", errorMsg);
                gen.writeEnd();
            });
            gen.writeEnd();
        }
        if (toManyDuplicates != null) {
            gen.write("tooManyDuplicates", toManyDuplicates);
        }
        gen.writeEnd();
        gen.flush();
    }

    private Attributes toAttributes(InputStream in) {
        try {
            return new JSONReader(Json.createParser(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .readDataset(null);
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.BAD_REQUEST));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private org.dcm4chee.arc.query.util.QueryParam queryParam(ApplicationEntity ae, boolean withoutIssuer) {
        org.dcm4chee.arc.query.util.QueryParam queryParam = new org.dcm4chee.arc.query.util.QueryParam(ae);
        queryParam.setFuzzySemanticMatching(Boolean.parseBoolean(fuzzymatching));
        queryParam.setWithoutIssuer(withoutIssuer);
        return queryParam;
    }

    private void verifyMergePatient(
            Collection<IDWithIssuer> trustedPriorPatientIDs, Collection<IDWithIssuer> trustedPatientIDs,
            String findSCP, CFindSCU cfindscu, ApplicationEntity localAE) throws Exception {
        try {
            List<Attributes> studiesOfPriorPatient = cfindscu.findStudiesOfPatient(
                    localAE, findSCP, Priority.NORMAL, trustedPriorPatientIDs.iterator().next());
            if (!studiesOfPriorPatient.isEmpty())
                throw new VerifyMergePatientException("Found " + studiesOfPriorPatient.size()
                        + " studies of prior Patient[id=" + trustedPriorPatientIDs + "] at " + findSCP);

            Patient priorPatient = patientService.findPatient(trustedPriorPatientIDs);
            IDWithIssuer patientID = trustedPatientIDs.iterator().next();
            if (priorPatient != null) {
                for (String studyIUID : patientService.studyInstanceUIDsOf(priorPatient)) {
                    studiesOfPriorPatient = cfindscu.findStudy(localAE, findSCP, Priority.NORMAL, studyIUID,
                                                                Tag.PatientID,
                                                                Tag.IssuerOfPatientID,
                                                                Tag.IssuerOfPatientIDQualifiersSequence);
                    if (!studiesOfPriorPatient.isEmpty()) {
                        IDWithIssuer findPatientID = IDWithIssuer.pidOf(studiesOfPriorPatient.get(0));
                        if (!findPatientID.matches(patientID, true, true)) {
                            throw new VerifyMergePatientException("Found Study[uid=" + studyIUID
                                    + "] of different Patient[id=" + findPatientID
                                    + "] than target Patient[id=" + patientID
                                    + "] at " + findSCP);
                        }
                    }
                }
            }
        } catch (ConfigurationNotFoundException e) {
            throw new WebApplicationException(
                    errResponse("No such Application Entity: " + findSCP, Response.Status.NOT_FOUND));
        } catch (ConnectException | DicomServiceException e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.BAD_GATEWAY));
        }
    }

    private static class VerifyMergePatientException extends Exception {
        public VerifyMergePatientException(String message) {
            super(message);
        }
    }

    private boolean mergePatient(
            Collection<IDWithIssuer> trustedPatientIDs, Collection<IDWithIssuer> trustedPriorPatientIDs,
            ArchiveAEExtension arcAE) {
        PatientMgtContext patMgtCtx = patientService.createPatientMgtContextWEB(HttpServletRequestInfo.valueOf(request));
        patMgtCtx.setArchiveAEExtension(arcAE);
        patMgtCtx.setAttributes(exportPatientIDsWithIssuer(new Attributes(), trustedPatientIDs));
        patMgtCtx.setPatientIDs(trustedPatientIDs);
        patMgtCtx.setPreviousAttributes(exportPatientIDsWithIssuer(new Attributes(), trustedPriorPatientIDs));
        patMgtCtx.setPreviousPatientIDs(trustedPriorPatientIDs);
        LOG.info("Merge prior patient identifier {} with target patient identifier {}",
                trustedPriorPatientIDs, trustedPatientIDs);
        patientService.mergePatient(patMgtCtx);
        notifyHL7Receivers("ADT^A40^ADT_A39", patMgtCtx);
        return true;
    }

    @POST
    @Path("/patients/{priorPatientID}/changeid/{patientID}")
    public Response changePatientID(@PathParam("priorPatientID") String multiplePriorPatientIDs,
                                @PathParam("patientID") String multiplePatientIDs) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        try {
            Collection<IDWithIssuer> trustedPriorPatientIDs = trustedPatientIDs(multiplePriorPatientIDs, arcAE);
            if (trustedPriorPatientIDs.isEmpty())
                return errResponse(
                        "Missing prior patient identifier with trusted assigning authority in " + multiplePriorPatientIDs,
                        Response.Status.BAD_REQUEST);

            Collection<IDWithIssuer> trustedPatientIDs = trustedPatientIDs(multiplePatientIDs, arcAE);
            if (trustedPatientIDs.isEmpty())
                return errResponse(
                        "Missing patient identifier with trusted assigning authority in " + multiplePatientIDs,
                        Response.Status.BAD_REQUEST);

            Patient prevPatient = patientService.findPatient(trustedPriorPatientIDs);
            PatientMgtContext ctx = patientService.createPatientMgtContextWEB(HttpServletRequestInfo.valueOf(request));
            ctx.setArchiveAEExtension(arcAE);
            ctx.setAttributeUpdatePolicy(Attributes.UpdatePolicy.REPLACE);
            ctx.setPreviousAttributes(prevPatient.getAttributes());
            ctx.setAttributes(exportPatientIDsWithIssuer(new Attributes(prevPatient.getAttributes()), trustedPatientIDs));
            patientService.changePatientID(ctx);
            notifyHL7Receivers("ADT^A47^ADT_A30", ctx);
            rsForward.forward(RSOperation.ChangePatientID, arcAE, null, request);
            return Response.noContent().build();
        } catch (PatientAlreadyExistsException | NonUniquePatientException | PatientTrackingNotAllowedException
                | CircularPatientMergeException e) {
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (PatientMergedException e) {
            return errResponse(e.getMessage(), Response.Status.FORBIDDEN);
        } catch(Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Attributes exportPatientIDsWithIssuer(Attributes attrs, Collection<IDWithIssuer> idWithIssuers) {
        attrs.setNull(Tag.PatientID, VR.LO);
        attrs.setNull(Tag.IssuerOfPatientID, VR.LO);
        attrs.setNull(Tag.IssuerOfPatientIDQualifiersSequence, VR.SQ);
        attrs.setNull(Tag.OtherPatientIDsSequence, VR.SQ);
        Iterator<IDWithIssuer> iter = idWithIssuers.iterator();
        attrs = iter.next().exportPatientIDWithIssuer(attrs);
        Sequence otherPatientIDsSequence = attrs.ensureSequence(
                Tag.OtherPatientIDsSequence,
                idWithIssuers.size() - 1);
        while (iter.hasNext())
            otherPatientIDsSequence.add(iter.next().exportPatientIDWithIssuer(null));
        return attrs;
    }

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        return ae == null || !ae.isInstalled() ? null : ae.getAEExtension(ArchiveAEExtension.class);
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                this,
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    public void validate() {
        logRequest();
        String[] uriPath = StringUtils.split(uriInfo.getPath(), '/');
        if ("issuer".equals(uriPath[uriPath.length - 2]))
            new QueryAttributes(uriInfo, null);
    }

    private List<Set<IDWithIssuer>> priorPatientIdentifiers(InputStream in) {
        JsonParser parser = Json.createParser(new InputStreamReader(in, StandardCharsets.UTF_8));
        expect(parser, JsonParser.Event.START_ARRAY);
        List<Set<IDWithIssuer>> priorPatientIdentifiers = new ArrayList<>();
        while (parser.next() == JsonParser.Event.START_OBJECT) {
            Attributes priorPatientIdentifier = new Attributes(4);
            while (parser.next() == JsonParser.Event.KEY_NAME) {
                switch (parser.getString()) {
                    case "PatientID":
                        expect(parser, JsonParser.Event.VALUE_STRING);
                        priorPatientIdentifier.setString(Tag.PatientID, VR.LO, parser.getString());
                        break;
                    case "IssuerOfPatientID":
                        expect(parser, JsonParser.Event.VALUE_STRING);
                        priorPatientIdentifier.setString(Tag.IssuerOfPatientID, VR.LO, parser.getString());
                        break;
                    case "IssuerOfPatientIDQualifiers":
                        expect(parser, JsonParser.Event.START_OBJECT);
                        priorPatientIdentifier.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 2)
                                .add(parseIssuerOfPIDQualifier(parser));
                        break;
                    case "OtherPatientIDsSequence":
                       parseOtherPriorPatientIDsSequence(parser,
                               priorPatientIdentifier.newSequence(Tag.OtherPatientIDsSequence, 10));
                        break;
                    default:
                        throw new WebApplicationException(
                                errResponse("Unexpected Key name", Response.Status.BAD_REQUEST));
                }
            }
            priorPatientIdentifiers.add(IDWithIssuer.pidsOf(priorPatientIdentifier));
        }
        return priorPatientIdentifiers;
    }

    private void parseOtherPriorPatientIDsSequence(JsonParser parser, Sequence seq) {
        expect(parser, JsonParser.Event.START_ARRAY);
        while (parser.next() == JsonParser.Event.START_OBJECT)
            seq.add(parsePriorOtherPatientID(parser));
    }

    private Attributes parsePriorOtherPatientID(JsonParser parser) {
        Attributes otherPriorPatientIdentifier = new Attributes(3);
        while (parser.next() == JsonParser.Event.KEY_NAME) {
            switch (parser.getString()) {
                case "PatientID":
                    expect(parser, JsonParser.Event.VALUE_STRING);
                    otherPriorPatientIdentifier.setString(Tag.PatientID, VR.LO, parser.getString());
                    break;
                case "IssuerOfPatientID":
                    expect(parser, JsonParser.Event.VALUE_STRING);
                    otherPriorPatientIdentifier.setString(Tag.IssuerOfPatientID, VR.LO, parser.getString());
                    break;
                case "IssuerOfPatientIDQualifiers":
                    expect(parser, JsonParser.Event.START_OBJECT);
                    otherPriorPatientIdentifier.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 2)
                            .add(parseIssuerOfPIDQualifier(parser));
                    break;
                default:
                    throw new WebApplicationException(
                            errResponse("Unexpected Key name", Response.Status.BAD_REQUEST));
            }
        }
        return otherPriorPatientIdentifier;
    }

    private Attributes parseIssuerOfPIDQualifier(JsonParser parser) {
        Attributes attr = new Attributes(2);
        while (parser.next() == JsonParser.Event.KEY_NAME) {
            switch (parser.getString()) {
                case "UniversalEntityID":
                    expect(parser, JsonParser.Event.VALUE_STRING);
                    attr.setString(Tag.UniversalEntityID, VR.UT, parser.getString());
                    break;
                case "UniversalEntityIDType":
                    expect(parser, JsonParser.Event.VALUE_STRING);
                    attr.setString(Tag.UniversalEntityIDType, VR.CS, parser.getString());
                    break;
                default:
                    throw new WebApplicationException(
                            errResponse("Unexpected Key name", Response.Status.BAD_REQUEST));
            }
        }
        return attr;
    }

    private void expect(JsonParser parser, JsonParser.Event expected) {
        JsonParser.Event next = parser.next();
        if (next != expected)
            throw new WebApplicationException(
                    errResponse("Unexpected " + next, Response.Status.BAD_REQUEST));
    }

    private Response errResponse(String msg, Response.Status status) {
        return errResponseAsTextPlain("{\"errorMessage\":\"" + msg + "\"}", status);
    }

    private Response errResponseAsTextPlain(String errorMsg, Response.Status status) {
        LOG.info("Response {} caused by {}", status, errorMsg);
        return Response.status(status)
                .entity(errorMsg)
                .type("text/plain")
                .build();
    }

    private String exceptionAsString(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    public void notifyHL7Receivers(String msgType, PatientMgtContext ctx) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        String sendingAppFacility = arcDev.getHL7ADTSendingApplication();
        if (sendingAppFacility == null)
            return;

        HL7Application sender = device.getDeviceExtension(HL7DeviceExtension.class).getHL7Application(sendingAppFacility);
        if (sender == null) {
            LOG.info("Sending HL7 Application not configured : {}", sendingAppFacility);
            return;
        }

        for (String receivingAppFacility : arcDev.getHL7ADTReceivingApplication()) {
            try {
                HL7Application receiver = hl7AppCache.findHL7Application(receivingAppFacility);
                byte[] data = HL7SenderUtils.data(sender, sendingAppFacility, receiver,
                                                ctx.getAttributes(), ctx.getPreviousAttributes(),
                                                msgType, arcDev.getOutgoingPatientUpdateTemplateURI(),
                                                null, null);
                hl7Sender.scheduleMessage(ctx.getHttpServletRequestInfo(), data);
            } catch (ConfigurationException e) {
                LOG.info("Unknown HL7 receiving application and facility {} to send message type {}",
                        receivingAppFacility, msgType);
            } catch (TransformerConfigurationException | UnsupportedEncodingException | SAXException e) {
                LOG.info("Failed in stylesheet {} transformation for message type {} \n",
                        arcDev.getOutgoingPatientUpdateTemplateURI(), msgType, e);
            } catch (Exception e) {
                LOG.info("Failed to notify HL7 receiver {} for message type {} \n", receivingAppFacility, msgType, e);
            }
        }
    }

    @POST
    @Path("/patients/charset/{charset}")
    public Response updateCharset(
            @Pattern(regexp = "ISO_IR 100|ISO_IR 101|ISO_IR 109|ISO_IR 110|ISO_IR 144|ISO_IR 127|ISO_IR 126|ISO_IR 138|ISO_IR 148|ISO_IR 13|ISO_IR 166|ISO_IR 192|GB18030|GBK")
            @PathParam("charset") String charset,
            @QueryParam("test") @Pattern(regexp = "true|false") @DefaultValue("false") String test) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        boolean update = !Boolean.parseBoolean(test);
        int updated = 0;
        List<IDWithIssuer> failures = new ArrayList<>();
        try {
            QueryAttributes queryAttrs = new QueryAttributes(uriInfo, null);
            Attributes queryKeys = queryAttrs.getQueryKeys();
            CriteriaQuery<AttributesBlob> query = queryService.createPatientAttributesQuery(
                    queryParam(arcAE.getApplicationEntity(), false), queryKeys);
            int limit = arcAE.getArchiveDeviceExtension().getUpdateCharsetFetchSize();
            int offset = 0;
            List<AttributesBlob> blobs = patientService.queryWithOffsetAndLimit(query, offset, limit);
            if (blobs.isEmpty()) {
                return Response.status(Response.Status.NO_CONTENT).build();
            }
            for (;;) {
                for (AttributesBlob blob : blobs) {
                    Attributes attrs = blob.getAttributes();
                    if (charset.equals(attrs.getString(Tag.SpecificCharacterSet))) continue;
                    attrs.setSpecificCharacterSet(charset);
                    blob.setAttributes(attrs);
                    if (attrs.equals(AttributesBlob.decodeAttributes(blob.getEncodedAttributes(), null))) {
                        if (update) patientService.merge(blob);
                        updated++;
                    } else {
                        failures.add(IDWithIssuer.pidOf(attrs));
                    }
                }
                if (blobs.size() < limit) break;
                offset += blobs.size();
            }
            if (updated > 0)
                rsForward.forward(RSOperation.UpdateCharset, arcAE, null, request);
            return updateCharsetResponse(updated, failures).build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private static Response.ResponseBuilder updateCharsetResponse(int updated, List<IDWithIssuer> failures) {
        return Response.status(failures.isEmpty()
                        ? Response.Status.OK
                        : updated == 0 ? Response.Status.CONFLICT : Response.Status.ACCEPTED)
                .entity((StreamingOutput) out -> updateCharsetResponsePayload(updated, failures, out));
    }

    private static void updateCharsetResponsePayload(int updated, List<IDWithIssuer> failures, OutputStream out) {
        JsonGenerator gen = Json.createGenerator(out);
        gen.writeStartObject();
        gen.write("updated", updated);
        if (!failures.isEmpty()) {
            gen.writeStartArray("failures");
            failures.forEach(s -> gen.write(s != null ? s.toString() : "w/o Patient ID"));
            gen.writeEnd();
        }
        gen.writeEnd();
        gen.flush();
    }

    private void validateAcceptedUserRoles(ArchiveAEExtension arcAE) {
        KeycloakContext keycloakContext = KeycloakContext.valueOf(request);
        if (keycloakContext.isSecured() && !keycloakContext.isUserInRole(System.getProperty(SUPER_USER_ROLE))) {
            if (!arcAE.isAcceptedUserRole(keycloakContext.getRoles()))
                throw new WebApplicationException(
                        "Application Entity " + arcAE.getApplicationEntity().getAETitle() + " does not list role of accessing user",
                        Response.Status.FORBIDDEN);
        }
    }

    private void validateWebAppServiceClass() {
        device.getWebApplications().stream()
                .filter(webApp -> request.getRequestURI().startsWith(webApp.getServicePath())
                        && Arrays.asList(webApp.getServiceClasses())
                        .contains(WebApplication.ServiceClass.DCM4CHEE_ARC_AET))
                .findFirst()
                .orElseThrow(() -> new WebApplicationException(errResponse(
                        "No Web Application with DCM4CHEE_ARC_AET service class found for Application Entity: " + aet,
                        Response.Status.NOT_FOUND)));
    }
}
