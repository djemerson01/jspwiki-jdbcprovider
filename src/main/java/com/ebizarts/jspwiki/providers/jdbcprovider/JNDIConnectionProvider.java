/*
    JDBCProvider - an RDBMS backed page- and attachment provider for
    JSPWiki.
 
    Copyright (C) 2006-2007 The JDBCProvider development team.
    Copyright (C) 2008-2014 David Emerson (david@ebizarts.com)
    
    The JDBCProvider developer team members are:
      Xan Gregg
      Soeren Berg Glasius
      Mikkel Troest
      Milt Taylor
 
    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 2.1 of the License, or
    (at your option) any later version.
 
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.
 
    You should have received a copy of the GNU Lesser General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.ebizarts.jspwiki.providers.jdbcprovider;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.apache.wiki.InternalWikiException;
import org.apache.wiki.WikiEngine;
import org.apache.wiki.api.exceptions.NoRequiredPropertyException;
import org.apache.wiki.util.TextUtil;

/*
 * History:
 *   2006-04-26 MT  Fixed initialize() to use jndi.datasource in stead of hard-
 *                  coded test data source
 */

/**
 * @author Mikkel Troest
 * @author S�ren Berg Glasius
 */
public class JNDIConnectionProvider extends ConnectionProvider
{

    private String jndiDatasource;

    private DataSource ds;

    /** Creates a new instance of JNDIConnectionProvider */
    public JNDIConnectionProvider()
    {
    }

    public void initialize(WikiEngine engine, final Properties config) throws NoRequiredPropertyException
    {
        jndiDatasource = TextUtil.getRequiredProperty(config, "jndi.datasource");
        try
        {
            Context ctx = new InitialContext();
            ds = (DataSource) ctx.lookup("java:comp/env/" + jndiDatasource);
        }
        catch (NamingException ex)
        {
            throw new InternalWikiException("NamingException caught: " + ex.getMessage());
        }
    }

    public Connection getConnection(WikiEngine engine) throws SQLException
    {
        return ds.getConnection();
    }

}
