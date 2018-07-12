package org.verdictdb.core.sqlobject;

import java.io.Serializable;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import com.google.common.base.Optional;

/**
 * Represents a relation (or a table) that can appear in the from clause.
 *
 * @author Yongjoo Park
 */
public abstract class AbstractRelation implements Serializable {

  private static final long serialVersionUID = 4819247286138983277L;
  
  Optional<String> aliasName = Optional.absent();

  public void setAliasName(String aliasName) {
    this.aliasName = Optional.of(aliasName);
  }

  public Optional<String> getAliasName() {
    return aliasName;
  }

  @Override
  public int hashCode() {
    return HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(Object obj) {
    return EqualsBuilder.reflectionEquals(this, obj);
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this);
  }

  public boolean isSupportedAggregate() {
    if (!(this instanceof SelectQuery)) {
      return false;
    }
    SelectQuery sel = (SelectQuery) this;
    List<SelectItem> selectList = sel.getSelectList();
    for (SelectItem item : selectList) {
      if (item instanceof AliasedColumn) {
        item = ((AliasedColumn) item).getColumn();
      }

      if (item instanceof ColumnOp) {
        ColumnOp col = (ColumnOp) item;
        if (col.isColumnOpAggregate()) {
          return true;
        }
      }
    }
    return false;
  }
}