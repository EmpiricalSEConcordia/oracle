/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2008, Red Hat Middleware LLC or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Middleware LLC.
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
 *
 */
package org.hibernate.hql.ast.tree;
import org.hibernate.HibernateLogger;
import org.hibernate.hql.antlr.HqlSqlTokenTypes;
import org.hibernate.hql.antlr.SqlTokenTypes;
import org.hibernate.hql.ast.util.ASTUtil;
import org.jboss.logging.Logger;
import antlr.collections.AST;

/**
 * Defines a top-level AST node representing an HQL update statement.
 *
 * @author Steve Ebersole
 */
public class UpdateStatement extends AbstractRestrictableStatement {

    private static final HibernateLogger LOG = Logger.getMessageLogger(HibernateLogger.class, UpdateStatement.class.getName());

	/**
	 * @see org.hibernate.hql.ast.tree.Statement#getStatementType()
	 */
	public int getStatementType() {
		return SqlTokenTypes.UPDATE;
	}

	/**
	 * @see org.hibernate.hql.ast.tree.Statement#needsExecutor()
	 */
	public boolean needsExecutor() {
		return true;
	}

	@Override
    protected int getWhereClauseParentTokenType() {
		return SqlTokenTypes.SET;
	}

	@Override
    protected HibernateLogger getLog() {
        return LOG;
	}

	public AST getSetClause() {
		return ASTUtil.findTypeInChildren( this, HqlSqlTokenTypes.SET );
	}
}
