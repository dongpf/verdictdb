package org.verdictdb.core.scramble;

import java.util.ArrayList;
import java.util.List;

import org.verdictdb.core.query.AliasedColumn;
import org.verdictdb.core.query.AsteriskColumn;
import org.verdictdb.core.query.BaseTable;
import org.verdictdb.core.query.ColumnOp;
import org.verdictdb.core.query.ConstantColumn;
import org.verdictdb.core.query.CreateTableAsSelectQuery;
import org.verdictdb.core.query.SelectItem;
import org.verdictdb.core.query.SelectQuery;

public class UniformScrambler extends Scrambler {

  public UniformScrambler(
      String originalSchemaName, String originalTableName,
      String scrambledSchemaName, String scrambledTableName,
      int aggregationBlockCount) {
    super(originalSchemaName, originalTableName, scrambledSchemaName, scrambledTableName, aggregationBlockCount);
  }

  public CreateTableAsSelectQuery scrambledTableCreationQuery() {
    SelectQuery selectQuery = scramblingQuery();
    CreateTableAsSelectQuery createQuery =
        new CreateTableAsSelectQuery(scrambledSchemaName, scrambledTableName, selectQuery);
    createQuery.addPartitionColumn(aggregationBlockColumn);
    return createQuery;
  }

  SelectQuery scramblingQuery() {
    // block agg index = cast(floor(rand() * aggBlockCount) as smallint)
    AliasedColumn aggBlockValue = new AliasedColumn(
        ColumnOp.cast(
          ColumnOp.floor(ColumnOp.multiply(
              ColumnOp.rand(),
              ConstantColumn.valueOf(aggregationBlockCount))),
          ConstantColumn.valueOf("smallint")),
        aggregationBlockColumn);

    // subsample value: random integer between 0 and 99 (inclusive)
    // = cast(floor(rand() * 100) as smallint)
    AliasedColumn subsampleValue = new AliasedColumn(
        ColumnOp.cast(
            ColumnOp.floor(ColumnOp.multiply(
                ColumnOp.rand(),
                ConstantColumn.valueOf(100))),
        ConstantColumn.valueOf("smallint")),
        subsampleColumn);
//    AliasedColumn subsampleValue = new AliasedColumn(
//            ColumnOp.floor(ColumnOp.multiply(
//                ColumnOp.rand(),
//                ConstantColumn.valueOf(100.0))),
//        subsampleColumn);

    AliasedColumn tierValue = new AliasedColumn(ConstantColumn.valueOf(1), tierColumn);

    List<SelectItem> newSelectList = new ArrayList<>();
    newSelectList.add(new AsteriskColumn());
    newSelectList.add(aggBlockValue);
    newSelectList.add(subsampleValue);
    newSelectList.add(tierValue);

    SelectQuery augmentedRelation = SelectQuery.getSelectQueryOp(
        newSelectList,
        new BaseTable(originalSchemaName, originalTableName));
    return augmentedRelation;
  }

}