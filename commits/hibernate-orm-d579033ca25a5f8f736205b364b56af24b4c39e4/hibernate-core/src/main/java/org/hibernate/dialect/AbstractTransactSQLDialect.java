/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2010, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.dialect;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Iterator;
import java.util.Map;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.function.CharIndexFunction;
import org.hibernate.dialect.function.NoArgSQLFunction;
import org.hibernate.dialect.function.SQLFunctionTemplate;
import org.hibernate.dialect.function.StandardSQLFunction;
import org.hibernate.dialect.function.VarArgsSQLFunction;
import org.hibernate.type.StandardBasicTypes;

/**
 * An abstract base class for Sybase and MS SQL Server dialects.
 *
 * @author Gavin King
 */
abstract class AbstractTransactSQLDialect extends Dialect {
	public AbstractTransactSQLDialect() {
		super();
        registerColumnType( Types.BINARY, "binary($l)" );
		registerColumnType( Types.BIT, "tinyint" ); //Sybase BIT type does not support null values
		registerColumnType( Types.BIGINT, "numeric(19,0)" );
		registerColumnType( Types.SMALLINT, "smallint" );
		registerColumnType( Types.TINYINT, "tinyint" );
		registerColumnType( Types.INTEGER, "int" );
		registerColumnType( Types.CHAR, "char(1)" );
		registerColumnType( Types.VARCHAR, "varchar($l)" );
		registerColumnType( Types.FLOAT, "float" );
		registerColumnType( Types.DOUBLE, "double precision" );
		registerColumnType( Types.DATE, "datetime" );
		registerColumnType( Types.TIME, "datetime" );
		registerColumnType( Types.TIMESTAMP, "datetime" );
		registerColumnType( Types.VARBINARY, "varbinary($l)" );
		registerColumnType( Types.NUMERIC, "numeric($p,$s)" );
		registerColumnType( Types.BLOB, "image" );
		registerColumnType( Types.CLOB, "text" );

		registerFunction( "ascii", new StandardSQLFunction("ascii", StandardBasicTypes.INTEGER) );
		registerFunction( "char", new StandardSQLFunction("char", StandardBasicTypes.CHARACTER) );
		registerFunction( "len", new StandardSQLFunction("len", StandardBasicTypes.LONG) );
		registerFunction( "lower", new StandardSQLFunction("lower") );
		registerFunction( "upper", new StandardSQLFunction("upper") );
		registerFunction( "str", new StandardSQLFunction("str", StandardBasicTypes.STRING) );
		registerFunction( "ltrim", new StandardSQLFunction("ltrim") );
		registerFunction( "rtrim", new StandardSQLFunction("rtrim") );
		registerFunction( "reverse", new StandardSQLFunction("reverse") );
		registerFunction( "space", new StandardSQLFunction("space", StandardBasicTypes.STRING) );

		registerFunction( "user", new NoArgSQLFunction("user", StandardBasicTypes.STRING) );

		registerFunction( "current_timestamp", new NoArgSQLFunction("getdate", StandardBasicTypes.TIMESTAMP) );
		registerFunction( "current_time", new NoArgSQLFunction("getdate", StandardBasicTypes.TIME) );
		registerFunction( "current_date", new NoArgSQLFunction("getdate", StandardBasicTypes.DATE) );

		registerFunction( "getdate", new NoArgSQLFunction("getdate", StandardBasicTypes.TIMESTAMP) );
		registerFunction( "getutcdate", new NoArgSQLFunction("getutcdate", StandardBasicTypes.TIMESTAMP) );
		registerFunction( "day", new StandardSQLFunction("day", StandardBasicTypes.INTEGER) );
		registerFunction( "month", new StandardSQLFunction("month", StandardBasicTypes.INTEGER) );
		registerFunction( "year", new StandardSQLFunction("year", StandardBasicTypes.INTEGER) );
		registerFunction( "datename", new StandardSQLFunction("datename", StandardBasicTypes.STRING) );

		registerFunction( "abs", new StandardSQLFunction("abs") );
		registerFunction( "sign", new StandardSQLFunction("sign", StandardBasicTypes.INTEGER) );

		registerFunction( "acos", new StandardSQLFunction("acos", StandardBasicTypes.DOUBLE) );
		registerFunction( "asin", new StandardSQLFunction("asin", StandardBasicTypes.DOUBLE) );
		registerFunction( "atan", new StandardSQLFunction("atan", StandardBasicTypes.DOUBLE) );
		registerFunction( "cos", new StandardSQLFunction("cos", StandardBasicTypes.DOUBLE) );
		registerFunction( "cot", new StandardSQLFunction("cot", StandardBasicTypes.DOUBLE) );
		registerFunction( "exp", new StandardSQLFunction("exp", StandardBasicTypes.DOUBLE) );
		registerFunction( "log", new StandardSQLFunction( "log", StandardBasicTypes.DOUBLE) );
		registerFunction( "log10", new StandardSQLFunction("log10", StandardBasicTypes.DOUBLE) );
		registerFunction( "sin", new StandardSQLFunction("sin", StandardBasicTypes.DOUBLE) );
		registerFunction( "sqrt", new StandardSQLFunction("sqrt", StandardBasicTypes.DOUBLE) );
		registerFunction( "tan", new StandardSQLFunction("tan", StandardBasicTypes.DOUBLE) );
		registerFunction( "pi", new NoArgSQLFunction("pi", StandardBasicTypes.DOUBLE) );
		registerFunction( "square", new StandardSQLFunction("square") );
		registerFunction( "rand", new StandardSQLFunction("rand", StandardBasicTypes.FLOAT) );

		registerFunction("radians", new StandardSQLFunction("radians", StandardBasicTypes.DOUBLE) );
		registerFunction("degrees", new StandardSQLFunction("degrees", StandardBasicTypes.DOUBLE) );

		registerFunction( "round", new StandardSQLFunction("round") );
		registerFunction( "ceiling", new StandardSQLFunction("ceiling") );
		registerFunction( "floor", new StandardSQLFunction("floor") );

		registerFunction( "isnull", new StandardSQLFunction("isnull") );

		registerFunction( "concat", new VarArgsSQLFunction( StandardBasicTypes.STRING, "(","+",")" ) );

		registerFunction( "length", new StandardSQLFunction( "len", StandardBasicTypes.INTEGER ) );
		registerFunction( "trim", new SQLFunctionTemplate( StandardBasicTypes.STRING, "ltrim(rtrim(?1))") );
		registerFunction( "locate", new CharIndexFunction() );

		getDefaultProperties().setProperty(Environment.STATEMENT_BATCH_SIZE, NO_BATCH);
	}

	public String getAddColumnString() {
		return "add";
	}
	public String getNullColumnString() {
		return " null";
	}
	public boolean qualifyIndexName() {
		return false;
	}

	public String getForUpdateString() {
		return "";
	}

	public boolean supportsIdentityColumns() {
		return true;
	}
	public String getIdentitySelectString() {
		return "select @@identity";
	}
	public String getIdentityColumnString() {
		return "identity not null"; //starts with 1, implicitly
	}

	public boolean supportsInsertSelectIdentity() {
		return true;
	}

	public String appendIdentitySelectToInsert(String insertSQL) {
		return insertSQL + "\nselect @@identity";
	}

	public String appendLockHint(LockMode mode, String tableName) {
		if ( mode.greaterThan( LockMode.READ ) ) {
			return tableName + " holdlock";
		}
		else {
			return tableName;
		}
	}

	public String applyLocksToSql(String sql, LockOptions aliasedLockOptions, Map keyColumnNames) {
		// TODO:  merge additional lockoptions support in Dialect.applyLocksToSql
		Iterator itr = aliasedLockOptions.getAliasLockIterator();
		StringBuffer buffer = new StringBuffer( sql );
		int correction = 0;
		while ( itr.hasNext() ) {
			final Map.Entry entry = ( Map.Entry ) itr.next();
			final LockMode lockMode = ( LockMode ) entry.getValue();
			if ( lockMode.greaterThan( LockMode.READ ) ) {
				final String alias = ( String ) entry.getKey();
				int start = -1, end = -1;
				if ( sql.endsWith( " " + alias ) ) {
					start = ( sql.length() - alias.length() ) + correction;
					end = start + alias.length();
				}
				else {
					int position = sql.indexOf( " " + alias + " " );
					if ( position <= -1 ) {
						position = sql.indexOf( " " + alias + "," );
					}
					if ( position > -1 ) {
						start = position + correction + 1;
						end = start + alias.length();
					}
				}

				if ( start > -1 ) {
					final String lockHint = appendLockHint( lockMode, alias );
					buffer.replace( start, end, lockHint );
					correction += ( lockHint.length() - alias.length() );
				}
			}
		}
		return buffer.toString();
	}

	public int registerResultSetOutParameter(CallableStatement statement, int col) throws SQLException {
		return col; // sql server just returns automatically
	}

	public ResultSet getResultSet(CallableStatement ps) throws SQLException {
		boolean isResultSet = ps.execute();
//		 This assumes you will want to ignore any update counts
		while ( !isResultSet && ps.getUpdateCount() != -1 ) {
			isResultSet = ps.getMoreResults();
		}
//		 You may still have other ResultSets or update counts left to process here
//		 but you can't do it now or the ResultSet you just got will be closed
		return ps.getResultSet();
	}

	public boolean supportsCurrentTimestampSelection() {
		return true;
	}

	public boolean isCurrentTimestampSelectStringCallable() {
		return false;
	}

	public String getCurrentTimestampSelectString() {
		return "select getdate()";
	}

	public boolean supportsTemporaryTables() {
		return true;
	}

	public String generateTemporaryTableName(String baseTableName) {
		return "#" + baseTableName;
	}

	public boolean dropTemporaryTableAfterUse() {
		return true;  // sql-server, at least needed this dropped after use; strange!
	}
	public String getSelectGUIDString() {
		return "select newid()";
	}

	// Overridden informational metadata ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	public boolean supportsEmptyInList() {
		return false;
	}

	public boolean supportsUnionAll() {
		return true;
	}

	public boolean supportsExistsInSelect() {
		return false;
	}

	public boolean doesReadCommittedCauseWritersToBlockReaders() {
		return true;
	}

	public boolean doesRepeatableReadCauseReadersToBlockWriters() {
		return true;
	}
	public boolean supportsTupleDistinctCounts() {
		return false;
	}
}
