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
 * Portions created by the Initial Developer are Copyright (C) 2013
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

package org.dcm4chee.arc.wado;

import jakarta.ws.rs.core.StreamingOutput;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.StreamUtils;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.InstanceLocations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Apr 2016
 */
public class CompressedPixelDataOutput implements StreamingOutput {

    private static final Logger LOG = LoggerFactory.getLogger(CompressedPixelDataOutput.class);

    private final RetrieveContext ctx;
    private final InstanceLocations inst;

    public CompressedPixelDataOutput(RetrieveContext ctx, InstanceLocations inst) {
        this.ctx = ctx;
        this.inst = inst;
    }

    @Override
    public void write(OutputStream out) throws IOException {
        RetrieveService service = ctx.getRetrieveService();
        try (DicomInputStream dis = service.openDicomInputStream(ctx, inst)) {
            dis.readDataset(-1, Tag.PixelData);
            if (dis.tag() != Tag.PixelData || dis.length() != -1 || !dis.readItemHeader())
                throw new IOException("No or incorrect encapsulated compressed pixel data in requested object");
            dis.skipFully(dis.length());
            LOG.debug("Start writing compressed pixel data of {}", inst);
            while (dis.readItemHeader())
                StreamUtils.copy(dis, out, dis.length());
            LOG.debug("Finished writing compressed pixel data of {}", inst);
        }
    }
}
