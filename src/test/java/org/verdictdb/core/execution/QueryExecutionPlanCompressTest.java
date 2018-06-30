package org.verdictdb.core.execution;

import org.junit.BeforeClass;
import org.junit.Test;
import org.verdictdb.connection.DbmsConnection;
import org.verdictdb.connection.JdbcConnection;
import org.verdictdb.core.execution.ola.AggCombinerExecutionNode;
import org.verdictdb.core.execution.ola.AsyncAggExecutionNode;
import org.verdictdb.core.query.*;
import org.verdictdb.core.sql.NonValidatingSQLParser;
import org.verdictdb.exception.VerdictDBDbmsException;
import org.verdictdb.exception.VerdictDBException;
import org.verdictdb.sql.syntax.H2Syntax;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QueryExecutionPlanCompressTest {

  static String originalSchema = "originalschema";

  static String originalTable = "originaltable";

  static String newSchema = "newschema";

  static String newTable  = "newtable";

  static int aggblockCount = 2;

  static DbmsConnection conn;

  @BeforeClass
  public static void setupDbConnAndScrambledTable() throws SQLException, VerdictDBException {
    final String DB_CONNECTION = "jdbc:h2:mem:createasselecttest;DB_CLOSE_DELAY=-1";
    final String DB_USER = "";
    final String DB_PASSWORD = "";
    conn = new JdbcConnection(DriverManager.getConnection(DB_CONNECTION, DB_USER, DB_PASSWORD), new H2Syntax());
    conn.executeUpdate(String.format("CREATE SCHEMA IF NOT EXISTS\"%s\"", originalSchema));
    conn.executeUpdate(String.format("CREATE SCHEMA IF NOT EXISTS\"%s\"", newSchema));
    populateData(conn, originalSchema, originalTable);
  }

  static void populateData(DbmsConnection conn, String schemaName, String tableName) throws VerdictDBDbmsException {
    conn.executeUpdate(String.format("CREATE TABLE \"%s\".\"%s\"(\"id\" int, \"value\" double)", schemaName, tableName));
    for (int i = 0; i < 10; i++) {
      conn.executeUpdate(String.format("INSERT INTO \"%s\".\"%s\"(\"id\", \"value\") VALUES(%s, %f)",
          schemaName, tableName, i, (double) i+1));
    }
  }

  @Test
  public void simpleAggregateTest() throws VerdictDBException {
    String sql = "select avg(t.value) as a from originalschema.originaltable as t;";
    NonValidatingSQLParser sqlToRelation = new NonValidatingSQLParser();
    SelectQuery selectQuery = (SelectQuery) sqlToRelation.toRelation(sql);
    QueryExecutionPlan queryExecutionPlan = new QueryExecutionPlan(newSchema, null, selectQuery);
    QueryExecutionNode copy = queryExecutionPlan.root.deepcopy();
    queryExecutionPlan.compress();
    assertEquals(0, queryExecutionPlan.root.dependents.size());
    assertEquals(selectQuery, queryExecutionPlan.root.selectQuery.getFromList().get(0));
    assertEquals(copy.dependents.get(0).selectQuery, queryExecutionPlan.root.selectQuery.getFromList().get(0));

    // queryExecutionPlan.root.execute(conn);
  }

  @Test
  public void NestedAggregateFromTest() throws VerdictDBException {
    String sql = "select avg(t.value) from (select o.value from originalschema.originaltable as o where o.value>5) as t;";
    NonValidatingSQLParser sqlToRelation = new NonValidatingSQLParser();
    SelectQuery selectQuery = (SelectQuery) sqlToRelation.toRelation(sql);
    QueryExecutionPlan queryExecutionPlan = new QueryExecutionPlan(newSchema, null, selectQuery);
    QueryExecutionNode copy = queryExecutionPlan.root.dependents.get(0).dependents.get(0).deepcopy();
    queryExecutionPlan.compress();
    assertEquals(0, queryExecutionPlan.root.dependents.size());
    assertEquals(selectQuery, queryExecutionPlan.root.selectQuery.getFromList().get(0));

    assertEquals(copy.selectQuery,  ((SelectQuery)queryExecutionPlan.root.selectQuery.getFromList().get(0)).getFromList().get(0));
    // queryExecutionPlan.root.execute(conn);
  }

  @Test
  public void NestedAggregateFilterTest() throws VerdictDBException {
    String sql = "select avg(t.value) as a from originalschema.originaltable as t where t.value > " +
        "(select avg(o.value) as avg_value from originalschema.originaltable as o);";
    NonValidatingSQLParser sqlToRelation = new NonValidatingSQLParser();
    SelectQuery selectQuery = (SelectQuery) sqlToRelation.toRelation(sql);
    QueryExecutionPlan queryExecutionPlan = new QueryExecutionPlan(newSchema, null, selectQuery);
    QueryExecutionNode copy = queryExecutionPlan.root.dependents.get(0).dependents.get(0).deepcopy();
    queryExecutionPlan.compress();
    assertEquals(0, queryExecutionPlan.root.dependents.size());
    assertEquals(selectQuery, queryExecutionPlan.root.selectQuery.getFromList().get(0));

    assertEquals(copy.selectQuery,
        ((SubqueryColumn)((ColumnOp)((SelectQuery)queryExecutionPlan.root.selectQuery.getFromList().get(0)).getFilter().get()).getOperand(1)).getSubquery());
    // queryExecutionPlan.root.execute(conn);
  }

  @Test
  public void SimpleAggregateWithScrambleTableTest() throws VerdictDBException {
    String sql = "select avg(t.value) as a from originalschema.originaltable as t;";
    NonValidatingSQLParser sqlToRelation = new NonValidatingSQLParser();
    SelectQuery selectQuery = (SelectQuery) sqlToRelation.toRelation(sql);
    QueryExecutionPlan queryExecutionPlan = new QueryExecutionPlan(newSchema, null, selectQuery);

    BaseTable base = new BaseTable(originalSchema, originalTable, "t");
    SelectQuery leftQuery = SelectQuery.create(new AliasedColumn(ColumnOp.count(), "mycount"), base);
    leftQuery.addFilterByAnd(ColumnOp.lessequal(new BaseColumn("t", "value"), ConstantColumn.valueOf(5.0)));
    SelectQuery rightQuery = SelectQuery.create(new AliasedColumn(ColumnOp.count(), "mycount"), base);
    rightQuery.addFilterByAnd(ColumnOp.greater(new BaseColumn("t", "value"), ConstantColumn.valueOf(5.0)));
    AggExecutionNode leftNode = AggExecutionNode.create(null, leftQuery);
    AggExecutionNode rightNode = AggExecutionNode.create(null, rightQuery);
    ExecutionTokenQueue queue = new ExecutionTokenQueue();
    AggCombinerExecutionNode combiner = AggCombinerExecutionNode.create(queryExecutionPlan, leftNode, rightNode);
    combiner.addBroadcastingQueue(queue);
    AsyncAggExecutionNode asyncAggExecutionNode =
        AsyncAggExecutionNode.create(queryExecutionPlan, Arrays.<QueryExecutionNode>asList(leftNode, rightNode),
            Arrays.<QueryExecutionNode>asList(combiner));
    queryExecutionPlan.root.getDependents().remove(0);
    queryExecutionPlan.root.getListeningQueues().remove(0);
    ExecutionTokenQueue q = new ExecutionTokenQueue();
    queryExecutionPlan.root.getListeningQueues().add(q);
    asyncAggExecutionNode.addBroadcastingQueue(q);
    queryExecutionPlan.root.addDependency(asyncAggExecutionNode);

    QueryExecutionNode copy = queryExecutionPlan.root.deepcopy();
    queryExecutionPlan.compress();

    assertEquals(asyncAggExecutionNode, queryExecutionPlan.root.dependents.get(0));
    assertEquals(copy.selectQuery, queryExecutionPlan.root.selectQuery);
  }

  @Test
  public void NestedAggregateWithScrambleTableTest() throws VerdictDBException {
    String sql = "select avg(t.value) as a from (select o.value from originalschema.originaltable as o where o.value>5) as t;";
    NonValidatingSQLParser sqlToRelation = new NonValidatingSQLParser();
    SelectQuery selectQuery = (SelectQuery) sqlToRelation.toRelation(sql);
    QueryExecutionPlan queryExecutionPlan = new QueryExecutionPlan(newSchema, null, selectQuery);
    BaseTable base = new BaseTable(originalSchema, originalTable, "t");
    SelectQuery leftQuery = SelectQuery.create(new AliasedColumn(ColumnOp.count(), "mycount"), base);
    leftQuery.addFilterByAnd(ColumnOp.lessequal(new BaseColumn("t", "value"), ConstantColumn.valueOf(5.0)));
    SelectQuery rightQuery = SelectQuery.create(new AliasedColumn(ColumnOp.count(), "mycount"), base);
    rightQuery.addFilterByAnd(ColumnOp.greater(new BaseColumn("t", "value"), ConstantColumn.valueOf(5.0)));
    AggExecutionNode leftNode = AggExecutionNode.create(null, leftQuery);
    AggExecutionNode rightNode = AggExecutionNode.create(null, rightQuery);
    ExecutionTokenQueue queue = new ExecutionTokenQueue();
    AggCombinerExecutionNode combiner = AggCombinerExecutionNode.create(queryExecutionPlan, leftNode, rightNode);
    combiner.addBroadcastingQueue(queue);
    AsyncAggExecutionNode asyncAggExecutionNode =
        AsyncAggExecutionNode.create(null, Arrays.<QueryExecutionNode>asList(leftNode, rightNode),
            Arrays.<QueryExecutionNode>asList(combiner));
    queryExecutionPlan.root.dependents.get(0).getDependents().remove(0);
    queryExecutionPlan.root.dependents.get(0).getListeningQueues().remove(0);
    ExecutionTokenQueue q = new ExecutionTokenQueue();
    queryExecutionPlan.root.dependents.get(0).getListeningQueues().add(q);
    asyncAggExecutionNode.addBroadcastingQueue(q);
    queryExecutionPlan.root.dependents.get(0).addDependency(asyncAggExecutionNode);
    QueryExecutionNode copy = queryExecutionPlan.root.getDependent(0).deepcopy();
    queryExecutionPlan.compress();

    SelectQuery compressed = SelectQuery.create(
        Arrays.<SelectItem>asList(
          new AliasedColumn(new ColumnOp("avg", new BaseColumn("t", "value")), "a")
        ), new BaseTable("placeholderSchemaName", "placeholderTableName", "t"));
    compressed.setAliasName("t");
    assertEquals(queryExecutionPlan.root.selectQuery.getFromList().get(0), compressed);
    assertEquals(queryExecutionPlan.root.dependents.get(0), asyncAggExecutionNode);

    assertEquals(copy.dependents.get(0), queryExecutionPlan.root.dependents.get(0));
  }

  @Test
  public void NestedAggregateWithScrambleTableHavingCommonChildrenTest() throws VerdictDBException {
    String sql = "select avg(t.value) as a from (select o.value from originalschema.originaltable as o where o.value>5) as t;";
    NonValidatingSQLParser sqlToRelation = new NonValidatingSQLParser();
    SelectQuery selectQuery = (SelectQuery) sqlToRelation.toRelation(sql);
    QueryExecutionPlan queryExecutionPlan = new QueryExecutionPlan(newSchema, null, selectQuery);
    BaseTable base = new BaseTable(originalSchema, originalTable, "t");
    SelectQuery leftQuery = SelectQuery.create(new AliasedColumn(ColumnOp.count(), "mycount"), base);
    leftQuery.addFilterByAnd(ColumnOp.lessequal(new BaseColumn("t", "value"), ConstantColumn.valueOf(5.0)));
    SelectQuery rightQuery = SelectQuery.create(new AliasedColumn(ColumnOp.count(), "mycount"), base);
    rightQuery.addFilterByAnd(ColumnOp.greater(new BaseColumn("t", "value"), ConstantColumn.valueOf(5.0)));
    AggExecutionNode leftNode = AggExecutionNode.create(null, leftQuery);
    AggExecutionNode rightNode = AggExecutionNode.create(null, rightQuery);
    ExecutionTokenQueue queue = new ExecutionTokenQueue();
    AggCombinerExecutionNode combiner = AggCombinerExecutionNode.create(queryExecutionPlan, leftNode, rightNode);
    combiner.addBroadcastingQueue(queue);
    AsyncAggExecutionNode asyncAggExecutionNode =
        AsyncAggExecutionNode.create(null, Arrays.<QueryExecutionNode>asList(leftNode, rightNode),
            Arrays.<QueryExecutionNode>asList(combiner));
    queryExecutionPlan.root.dependents.get(0).getDependents().remove(0);
    queryExecutionPlan.root.dependents.get(0).getListeningQueues().remove(0);
    ExecutionTokenQueue q = new ExecutionTokenQueue();
    queryExecutionPlan.root.dependents.get(0).getListeningQueues().add(q);
    asyncAggExecutionNode.addBroadcastingQueue(q);
    queryExecutionPlan.root.dependents.get(0).addDependency(asyncAggExecutionNode);

    SelectQuery commonQuery = SelectQuery.create(new AliasedColumn(ColumnOp.count(), "mycount"), base);
    rightQuery.addFilterByAnd(ColumnOp.greater(new BaseColumn("t", "value"), ConstantColumn.valueOf(5.0)));
    AggExecutionNode common = AggExecutionNode.create(null, commonQuery);
    leftQuery.addFilterByAnd(ColumnOp.lessequal(new BaseColumn("t", "value"), ConstantColumn.valueOf(5.0)));
    leftNode.addDependency(common);
    common.addBroadcastingQueue(leftNode.generateListeningQueue());
    rightNode.addDependency(common);
    common.addBroadcastingQueue(rightNode.generateListeningQueue());
    QueryExecutionNode copy = queryExecutionPlan.root.getDependent(0).deepcopy();
    queryExecutionPlan.compress();

    SelectQuery compressed = SelectQuery.create(
        Arrays.<SelectItem>asList(
            new AliasedColumn(new ColumnOp("avg", new BaseColumn("t", "value")), "a")
        ), new BaseTable("placeholderSchemaName", "placeholderTableName", "t"));
    compressed.setAliasName("t");
    assertEquals(queryExecutionPlan.root.selectQuery.getFromList().get(0), compressed);
    assertEquals(queryExecutionPlan.root.dependents.get(0), asyncAggExecutionNode);

    assertEquals(copy.dependents.get(0), queryExecutionPlan.root.dependents.get(0));
  }

}
