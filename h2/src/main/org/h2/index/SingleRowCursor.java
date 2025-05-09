/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.index;

import org.h2.message.DbException;
import org.h2.result.Row;
import org.h2.result.SearchRow;

/**
 * A cursor with at most one row.
 */
public class SingleRowCursor implements Cursor {

    /**
     * An empty cursor.
     */
    public static final SingleRowCursor EMPTY = new SingleRowCursor(null);

    private Row row;
    private boolean end;

    /**
     * Create a new cursor.
     *
     * @param row - the single row (if null then cursor is empty)
     */
    public SingleRowCursor(Row row) {
        this.row = row;
    }

    @Override
    public Row get() {
        return row;
    }

    @Override
    public SearchRow getSearchRow() {
        return row;
    }

    @Override
    public boolean next() {
        if (row == null || end) {
            row = null;
            return false;
        }
        end = true;
        return true;
    }

    @Override
    public boolean previous() {
        throw DbException.getInternalError(toString());
    }

}
