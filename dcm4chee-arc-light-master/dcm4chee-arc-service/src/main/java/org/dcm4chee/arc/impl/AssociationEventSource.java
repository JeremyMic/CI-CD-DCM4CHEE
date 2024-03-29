/*
 * **** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.AssociationMonitor;
import org.dcm4che3.net.pdu.AAssociateRJ;
import org.dcm4chee.arc.AssociationEvent;
import org.dcm4chee.arc.metrics.MetricsService;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2018
 */
@ApplicationScoped
public class AssociationEventSource implements AssociationMonitor {

    @Inject
    private Event<AssociationEvent> event;

    @Inject
    private MetricsService metricsService;

    @Override
    public void onAssociationEstablished(Association as) {
        assocTo(as);
        as.addAssociationListener(this::assocTo);
        event.fire(AssociationEvent.onAssociationEstablished(as));
    }

    private void assocTo(Association as) {
        metricsService.accept("assoc-to-" + as.getCalledAET(),
                () -> as.getApplicationEntity().getDevice().getNumberOfAssociationsInitiatedTo(as.getCalledAET()));
    }

    @Override
    public void onAssociationFailed(Association as, Throwable e) {
        event.fire(AssociationEvent.onAssociationFailed(as, e));
    }

    @Override
    public void onAssociationRejected(Association as, AAssociateRJ e) {
        event.fire(AssociationEvent.onAssociationRejected(as, e));
    }

    @Override
    public void onAssociationAccepted(Association as) {
        assocFrom(as);
        as.addAssociationListener(this::assocFrom);
        event.fire(AssociationEvent.onAssociationAccepted(as));
    }

    private void assocFrom(Association as) {
        metricsService.accept("assoc-from-" + as.getCallingAET(),
                () -> as.getApplicationEntity().getDevice().getNumberOfAssociationsInitiatedBy(as.getCallingAET()));
    }
}
