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
 * Portions created by the Initial Developer are Copyright (C) 2016-2019
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

package org.dcm4chee.arc.qmgt.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.conf.QueueDescriptor;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.qmgt.TaskManager;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Apr 2016
 */
@ApplicationScoped
public class PurgeTaskScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PurgeTaskScheduler.class);

    @Inject
    private TaskManager mgr;

    protected PurgeTaskScheduler() {
        super(Mode.scheduleWithFixedDelay);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev.getPurgeTaskPollingInterval();
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        for (QueueDescriptor desc : arcDev.getQueueDescriptors()) {
            delete(desc.getQueueName(), Task.Status.COMPLETED, desc.getPurgeTaskCompletedDelay());
            delete(desc.getQueueName(), Task.Status.FAILED, desc.getPurgeTaskFailedDelay());
            delete(desc.getQueueName(), Task.Status.WARNING, desc.getPurgeTaskWarningDelay());
            delete(desc.getQueueName(), Task.Status.CANCELED, desc.getPurgeTaskCanceledDelay());
        }
    }

    private void delete(String queueName, Task.Status status, Duration delay) {
        if (delay == null)
            return;

        Date before = new Date(System.currentTimeMillis() - delay.getSeconds() * 1000);
        mgr.deleteTasks(taskQueryParam(queueName, status, before), queueName);
     }

    private TaskQueryParam taskQueryParam(String queueName, Task.Status status, Date updatedBefore) {
        TaskQueryParam taskQueryParam = new TaskQueryParam();
        taskQueryParam.setDeviceName(device.getDeviceName());
        taskQueryParam.setQueueNames(Collections.singletonList(queueName));
        taskQueryParam.setStatus(status);
        taskQueryParam.setUpdatedBefore(updatedBefore);
        return taskQueryParam;
    }
}