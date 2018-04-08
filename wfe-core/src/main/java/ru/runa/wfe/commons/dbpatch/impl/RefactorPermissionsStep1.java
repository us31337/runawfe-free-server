/*
 * This file is part of the RUNA WFE project.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; version 2.1
 * of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 */
package ru.runa.wfe.commons.dbpatch.impl;

import java.util.ArrayList;
import java.util.List;
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import ru.runa.wfe.commons.dbpatch.DBPatch;

/**
 * This refactors permission_mapping table structure, to store object_type and permission as strings.
 * Since there is unique index with random constraint name, have to rename old table, create and fill new one,
 * then drop renamed old table.
 */
public class RefactorPermissionsStep1 extends DBPatch {

    // Since SecuredObjectType and Permission will evolve during refactoring, I cannot use them here.
    // So I have to go low-level to ordinal, name and maskPower.

    private static class TMatch {
        int ordinal;
        String name;

        TMatch(int ordinal, String name) {
            this.ordinal = ordinal;
            this.name = name;
        }
    }

    private static class PMatch {
        String type;
        long maskPower;
        String name;

        PMatch(String type, long maskPower, String name) {
            this.type = type;
            this.maskPower = maskPower;
            this.name = name;
        }
    }

    private static TMatch[] tMatches = {
            new TMatch(1, "SYSTEM"),
            new TMatch(2, "BOTSTATION"),
            new TMatch(3, "ACTOR"),
            new TMatch(4, "GROUP"),
            new TMatch(5, "RELATION"),
            new TMatch(6, "RELATIONGROUP"),
            new TMatch(7, "RELATIONPAIR"),
            new TMatch(8, "DEFINITION"),
            new TMatch(9, "PROCESS"),
            new TMatch(10, "REPORT")
    };

    // For first step, Permission instance names are choosen to match old localization keys.
    private static PMatch[] pMatches = {
            new PMatch("ACTOR", 0 /*Permission.READ*/, "READ"),
            new PMatch("ACTOR", 1 /*Permission.UPDATE_PERMISSIONS*/, "UPDATE_PERMISSIONS"),
            new PMatch("ACTOR", 2 /*ExecutorPermission.UPDATE*/, "UPDATE_EXECUTOR"),
            new PMatch("ACTOR", 3 /*ActorPermission.UPDATE_STATUS*/, "UPDATE_ACTOR_STATUS"),
            new PMatch("ACTOR", 4 /*ActorPermission.VIEW_TASKS*/, "VIEW_ACTOR_TASKS"),

            new PMatch("GROUP", 0 /*Permission.READ*/, "READ"),
            new PMatch("GROUP", 1 /*Permission.UPDATE_PERMISSIONS*/, "UPDATE_PERMISSIONS"),
            new PMatch("GROUP", 2 /*ExecutorPermission.UPDATE*/, "UPDATE_EXECUTOR"),
            new PMatch("GROUP", 3 /*GroupPermission.LIST_GROUP*/, "LIST_GROUP"),
            new PMatch("GROUP", 4 /*GroupPermission.ADD_TO_GROUP*/, "ADD_TO_GROUP"),
            new PMatch("GROUP", 5 /*GroupPermission.REMOVE_FROM_GROUP*/, "REMOVE_FROM_GROUP"),
            new PMatch("GROUP", 6 /*GroupPermission.VIEW_TASKS*/, "VIEW_GROUP_TASKS"),

            new PMatch("BOTSTATION", 0 /*Permission.READ*/, "READ"),
            new PMatch("BOTSTATION", 1 /*Permission.UPDATE_PERMISSIONS*/, "UPDATE_PERMISSIONS"),
            new PMatch("BOTSTATION", 4 /*BotStationPermission.BOT_STATION_CONFIGURE*/, "BOT_STATION_CONFIGURE"),

            new PMatch("DEFINITION", 0 /*Permission.READ*/, "READ"),
            new PMatch("DEFINITION", 1 /*Permission.UPDATE_PERMISSIONS*/, "UPDATE_PERMISSIONS"),
            new PMatch("DEFINITION", 2 /*DefinitionPermission.REDEPLOY_DEFINITION*/, "REDEPLOY_DEFINITION"),
            new PMatch("DEFINITION", 3 /*DefinitionPermission.UNDEPLOY_DEFINITION*/, "UNDEPLOY_DEFINITION"),
            new PMatch("DEFINITION", 4 /*DefinitionPermission.START_PROCESS*/, "START_PROCESS"),
            new PMatch("DEFINITION", 5 /*DefinitionPermission.READ_STARTED_PROCESS*/, "READ_PROCESS"),
            new PMatch("DEFINITION", 6 /*DefinitionPermission.CANCEL_STARTED_PROCESS*/, "CANCEL_PROCESS"),

            new PMatch("PROCESS", 0 /*Permission.READ*/, "READ"),
            new PMatch("PROCESS", 1 /*Permission.UPDATE_PERMISSIONS*/, "UPDATE_PERMISSIONS"),
            new PMatch("PROCESS", 2 /*ProcessPermission.CANCEL_PROCESS*/, "CANCEL_PROCESS"),

            new PMatch("RELATION", 0 /*Permission.READ*/, "READ"),
            new PMatch("RELATION", 1 /*Permission.UPDATE_PERMISSIONS*/, "UPDATE_PERMISSIONS"),
            new PMatch("RELATION", 2 /*RelationPermission.UPDATE*/, "UPDATE_RELATION"),

            new PMatch("REPORT", 0 /*Permission.READ*/, "READ"),
            new PMatch("REPORT", 1 /*Permission.UPDATE_PERMISSIONS*/, "UPDATE_PERMISSIONS"),
            new PMatch("REPORT", 2 /*ReportPermission.DEPLOY*/, "DEPLOY_REPORT"),

            new PMatch("SYSTEM", 0 /*Permission.READ*/, "READ"),
            new PMatch("SYSTEM", 1 /*Permission.UPDATE_PERMISSIONS*/, "UPDATE_PERMISSIONS"),
            new PMatch("SYSTEM", 2 /*SystemPermission.LOGIN_TO_SYSTEM*/, "LOGIN_TO_SYSTEM"),
            new PMatch("SYSTEM", 3 /*SystemPermission.CREATE_EXECUTOR*/, "CREATE_EXECUTOR"),
            new PMatch("SYSTEM", 5 /*SystemPermission.CHANGE_SELF_PASSWORD*/, "CHANGE_SELF_PASSWORD"),
            new PMatch("SYSTEM", 7 /*SystemPermission.VIEW_LOGS*/, "VIEW_LOGS"),
            new PMatch("SYSTEM", 4 /*WorkflowSystemPermission.DEPLOY_DEFINITION*/, "DEPLOY_DEFINITION"),
    };

    @Override
    protected List<String> getDDLQueriesBefore() {
        return new ArrayList<String>() {{
            // Index is used by FK, have to drop FK first.
            add(getDDLRemoveForeignKey("permission_mapping", "fk_permission_executor"));
            // Index names are global, have to drop old one before creating new one in getDDLQueriesAfter().
            add(getDDLRemoveIndex("permission_mapping", "ix_permission_by_executor"));

            add(getDDLRenameTable("permission_mapping", "permission_mapping__old"));
            add(getDDLCreateColumn("permission_mapping__old", new VarcharColumnDef("object_type", 32,true)));
            add(getDDLCreateColumn("permission_mapping__old", new VarcharColumnDef("permission", 32,true)));

            add(getDDLCreateTable(
                    "permission_mapping",
                    new ArrayList<ColumnDef>() {{
                        add(new BigintColumnDef("id", false).setPrimaryKey());
                        add(new BigintColumnDef("executor_id", false));
                        add(new VarcharColumnDef("object_type", 32,false));
                        add(new BigintColumnDef("object_id", false));
                        add(new VarcharColumnDef("permission", 32, false));
                    }},
                    "(object_id, object_type, permission, executor_id)"
            ));
        }};
    }

    @Override
    public void executeDML(Session session) throws Exception {

        // Fill `object_type` column in `permission_mapping__old`.
        {
            SQLQuery q = session.createSQLQuery("update permission_mapping__old set object_type=:s where type_id=:i");
            for (TMatch m : tMatches) {
                q.setParameter("s", m.name);
                q.setParameter("i", m.ordinal);
                q.executeUpdate();
            }

            Object i = session
                    .createSQLQuery(dialect.getLimitString(
                            "select distinct type_id from permission_mapping__old where object_type is null order by type_id",
                            0, 1
                    ))
                    .setInteger(0, 1)  // limit
                    .uniqueResult();
            if (i != null) {
                throw new Exception("Failed to convert type_id = " + i);
            }
        }

        // Fill `permission` column in `permission_mapping__old`.
        {
            SQLQuery q = session.createSQLQuery("update permission_mapping__old set permission=:p where object_type=:t and mask=:m");
            for (PMatch m : pMatches) {
                q.setParameter("p", m.name);
                q.setParameter("t", m.type);
                q.setParameter("m", 1 << m.maskPower);
                q.executeUpdate();
            }

            Object i = session
                    .createSQLQuery(dialect.getLimitString(
                            "select distinct mask from permission_mapping__old where permission is null order by mask",
                            0, 1
                    ))
                    .setInteger(0, 1)  // limit
                    .uniqueResult();
            if (i != null) {
                throw new Exception("Failed to convert mask = " + i);
            }
        }

        // Copy data to new table.
        session.createSQLQuery(
                "insert into permission_mapping (executor_id, object_type, object_id, permission) " +
                "select executor_id, object_type, identifiable_id, permission " +
                "from permission_mapping__old"
        ).executeUpdate();
    }

    @Override
    protected List<String> getDDLQueriesAfter() {
        return new ArrayList<String>() {{
            add(getDDLRemoveTable("permission_mapping__old"));
            add(getDDLCreateIndex("permission_mapping", "ix_permission_by_executor", "executor_id", "object_type", "permission", "object_id"));
            add(getDDLCreateForeignKey("permission_mapping", "fk_permission_executor", "executor_id", "executor", "id"));
        }};
    }
}