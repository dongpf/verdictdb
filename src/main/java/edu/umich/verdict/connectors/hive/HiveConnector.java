package edu.umich.verdict.connectors.hive;

import edu.umich.verdict.Configuration;
import edu.umich.verdict.connectors.DbConnector;
import edu.umich.verdict.connectors.MetaDataManager;

import java.sql.SQLException;

public class HiveConnector extends DbConnector {
    private final String udfJar;

    public HiveConnector(Configuration conf) throws SQLException, ClassNotFoundException {
        super(conf);
        udfJar = conf.get("hive.udf-jar");
    }

    @Override
    protected String getConnectionString(String host, String port){
        return super.getConnectionString(host, port)+"/default";
    }

    @Override
    protected String getDriverClassPath() {
        return "com.cloudera.hive.jdbc41.HS2Driver";
    }

    @Override
    protected String getProtocolName() {
        return "hive2";
    }

    @Override
    protected String getDbmsName() {
        return "Hive";
    }

    @Override
    protected void connect(String connectionString, String user, String password) throws SQLException, ClassNotFoundException {
        super.connect(connectionString, user, password);

        String initStatements = "delete jar if exists " + udfJar + ";" +
                "add jar " + udfJar + ";\n" +
                "drop function if exists verdict.poisson; create function verdict.poisson as 'edu.umich.tajik.verdict.hive.udf.Poisson';\n" +
                "drop function if exists verdict.conf_int; create function verdict.conf_int as 'edu.umich.tajik.verdict.hive.udf.ConfidenceInterval';\n" +
                "drop function if exists verdict.my_sum; create function verdict.my_sum as 'edu.umich.tajik.verdict.hive.uda.Sum';\n" +
                "drop function if exists verdict.my_count; create function verdict.my_count as 'edu.umich.tajik.verdict.hive.uda.Count';\n" +
                "drop function if exists verdict.my_avg; create function verdict.my_avg as 'edu.umich.tajik.verdict.hive.uda.Avg'";

        for (String q : initStatements.split(";"))
            if (!q.trim().isEmpty())
                executeStatement(q);
    }
}