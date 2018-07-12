package org.verdictdb.core.querying;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.verdictdb.core.execution.ExecutionInfoToken;
import org.verdictdb.core.sqlobject.BaseTable;
import org.verdictdb.core.sqlobject.SelectQuery;
import org.verdictdb.core.sqlobject.SqlConvertible;
import org.verdictdb.core.sqlobject.SubqueryColumn;
import org.verdictdb.exception.VerdictDBException;
import org.verdictdb.exception.VerdictDBValueException;

public abstract class QueryNodeWithPlaceHolders extends QueryNodeBase {
  
  List<BaseTable> placeholderTables = new ArrayList<>();

  // use this to compress the placeholderTable in filter
  List<SubqueryColumn> placeholderTablesinFilter = new ArrayList<>();

  public QueryNodeWithPlaceHolders(SelectQuery query) {
    super(query);
  }
  
  public Pair<BaseTable, SubscriptionTicket> createPlaceHolderTable(String aliasName) {
//      throws VerdictDBValueException {
    BaseTable table = new BaseTable("placeholderSchemaName", "placeholderTableName", aliasName);
    placeholderTables.add(table);
//    ExecutionTokenQueue listeningQueue = generateListeningQueue();
    SubscriptionTicket ticket = createSubscriptionTicket();
    return Pair.of(table, ticket);
  }
  
  @Override
  public SqlConvertible createQuery(List<ExecutionInfoToken> tokens) throws VerdictDBException {
    if (tokens == null) {
      return null;
    }
    if (tokens.size() < placeholderTables.size()) {
      throw new VerdictDBValueException("Not enough temp tables to plug into placeholder tables.");
    }

    for (int i = 0; i < placeholderTables.size(); i++) {
      BaseTable t = placeholderTables.get(i);
      ExecutionInfoToken r = tokens.get(i);
      String schemaName = (String) r.getValue("schemaName");
      String tableName = (String) r.getValue("tableName");
      t.setSchemaName(schemaName);
      t.setTableName(tableName);
//      System.out.println("!!placeholder replacement!!  \n" + 
//                         new ToStringBuilder(this, ToStringStyle.DEFAULT_STYLE) + "\n" +
//                         schemaName + " " + tableName);
    }
    
    return selectQuery;
  }

//  @Override
//  public ExecutionInfoToken executeNode(DbmsConnection conn, List<ExecutionInfoToken> downstreamResults) 
//      throws VerdictDBException {
//    if (downstreamResults==null) { return null; }
//    if (downstreamResults.size() < placeholderTables.size()) {
//      throw new VerdictDBValueException("Not enough temp tables to plug into placeholder tables.");
//    }
//    
//    for (int i = 0; i < placeholderTables.size(); i++) {
//      BaseTable t = placeholderTables.get(i);
//      ExecutionInfoToken r = downstreamResults.get(i);
//      String schemaName = (String) r.getValue("schemaName");
//      String tableName = (String) r.getValue("tableName");
//      t.setSchemaName(schemaName);
//      t.setTableName(tableName);
//    }
//    return null;
//  }

  public List<BaseTable> getPlaceholderTables() {
    return placeholderTables;
  }

  public List<SubqueryColumn> getPlaceholderTablesinFilter() {
    return placeholderTablesinFilter;
  }
  
  protected void copyFields(QueryNodeWithPlaceHolders from, QueryNodeWithPlaceHolders to) {
    super.copyFields(from, to);
    to.placeholderTables = from.placeholderTables;
  }
}