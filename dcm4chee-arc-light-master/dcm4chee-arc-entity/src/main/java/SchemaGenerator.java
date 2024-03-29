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
 * Portions created by the Initial Developer are Copyright (C) 2013-2021
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

import jakarta.persistence.Persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Sep 2023
 */
public class SchemaGenerator {

    public static void main(String[] args) throws IOException {
        String createScript = args[0] + "/create-" + args[1] + ".sql";
        String dropScript = args[0] + "/drop-" + args[1] + ".sql";
        Files.createDirectories(Paths.get(args[0]));
        Files.deleteIfExists(Paths.get(createScript));
        Files.deleteIfExists(Paths.get(dropScript));
        Map<String,String> map = new HashMap<>();
        map.put("jakarta.persistence.jtaDataSource", null);
        map.put("jakarta.persistence.schema-generation.scripts.action", "drop-and-create");
        map.put("jakarta.persistence.schema-generation.scripts.create-target", createScript);
        map.put("jakarta.persistence.schema-generation.scripts.drop-target", dropScript);
        map.put("hibernate.temp.use_jdbc_metadata_defaults", "false");
        Persistence.generateSchema("dcm4chee-arc", map);
    }

}
