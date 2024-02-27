/*
 * ** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2021
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
 * ** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.pdq;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2021
 */
public class PDQServiceContext {

    public enum SearchMethod {
        ComparePatientDemographics,
        UpdatePatientDemographics,
        QueryPatientDemographics,
        PatientVerificationScheduler
    }

    private final IDWithIssuer patientID;
    private HttpServletRequestInfo httpServletRequestInfo;
    private SearchMethod searchMethod;
    private UnparsedHL7Message hl7Msg;
    private UnparsedHL7Message rsp;
    private String sendingAppFacility;
    private String receivingAppFacility;
    private Attributes patientAttrs;
    private String fhirWebAppName;
    private String fhirQueryParams;
    private Exception exception;

    public PDQServiceContext(IDWithIssuer patientID) {
        this.patientID = patientID;
    }

    public IDWithIssuer getPatientID() {
        return patientID;
    }

    public HttpServletRequestInfo getHttpServletRequestInfo() {
        return httpServletRequestInfo;
    }

    public void setHttpServletRequestInfo(HttpServletRequestInfo httpServletRequestInfo) {
        this.httpServletRequestInfo = httpServletRequestInfo;
    }

    public String getSearchMethod() {
        return searchMethod.name();
    }

    public void setSearchMethod(SearchMethod searchMethod) {
        this.searchMethod = searchMethod;
    }

    public UnparsedHL7Message getHl7Msg() {
        return hl7Msg;
    }

    public void setHl7Msg(UnparsedHL7Message hl7Msg) {
        this.hl7Msg = hl7Msg;
    }

    public UnparsedHL7Message getRsp() {
        return rsp;
    }

    public void setRsp(UnparsedHL7Message rsp) {
        this.rsp = rsp;
    }

    public String getSendingAppFacility() {
        return sendingAppFacility;
    }

    public void setSendingAppFacility(String sendingAppFacility) {
        this.sendingAppFacility = sendingAppFacility;
    }

    public String getReceivingAppFacility() {
        return receivingAppFacility;
    }

    public void setReceivingAppFacility(String receivingAppFacility) {
        this.receivingAppFacility = receivingAppFacility;
    }

    public String getFhirWebAppName() {
        return fhirWebAppName;
    }

    public void setFhirWebAppName(String fhirWebAppName) {
        this.fhirWebAppName = fhirWebAppName;
    }

    public String getFhirQueryParams() {
        return fhirQueryParams;
    }

    public void setFhirQueryParams(String fhirQueryParams) {
        this.fhirQueryParams = fhirQueryParams;
    }

    public Attributes getPatientAttrs() {
        return patientAttrs;
    }

    public void setPatientAttrs(Attributes patientAttrs) {
        this.patientAttrs = patientAttrs;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PDQServiceContext["
                + "patientID=" + patientID
                + ", searchMethod='" + searchMethod);
        if (httpServletRequestInfo != null)
            sb.append(", httpServletRequestInfo=").append(httpServletRequestInfo);
        if (sendingAppFacility != null && receivingAppFacility != null) {
            sb.append(", sender=").append(sendingAppFacility);
            sb.append(", receiver=").append(receivingAppFacility);
        }
        if (fhirWebAppName != null)
            sb.append(", fhirWebAppName=").append(fhirWebAppName);
        sb.append("]");
        return sb.toString();
    }
}
