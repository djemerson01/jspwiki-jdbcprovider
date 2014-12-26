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
import java.util.Enumeration;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.apache.commons.dbcp2.BasicDataSourceFactory;
import org.apache.wiki.WikiEngine;
import org.apache.wiki.api.exceptions.NoRequiredPropertyException;
import org.apache.wiki.util.TextUtil;

/*
 * History:
 *   2007-02-19 MT  Total rewrite. Now uses BasicDataSourceFactory.
 *   2007-02-18 MT  Added support for additional driver properties.
 *                  - moved username/password members to properties.
 * 	 2007-02-13 MT  Got rid of 'factory' property since it's not used anyway
 *                  Changed logging to log4j.Logger in stead of deprecateded log4j.Category
 */

/**
 * @author Mikkel Troest
 * @author glasius
 */
public class DBCPConnectionProvider extends ConnectionProvider
{
    protected static final Logger log = Logger.getLogger(DBCPConnectionProvider.class);

    private static final String PREFIX = "dbcp";

    private DataSource ds = null;

    /** Creates a new instance of DBCPConnectionProvider */
    public DBCPConnectionProvider()
    {
    }

    public void initialize(WikiEngine engine, final Properties config) throws NoRequiredPropertyException
    {
        log.debug("Initializing DBCPConnectionProvider");

        Properties connectionProperties = parseAdditionalProperties(config, DRIVER_PROP_PREFIX);

        Properties dbcpProps = parseAdditionalProperties(config, PREFIX);
        dbcpProps.put("driverClassName", TextUtil.getRequiredProperty(config, PREFIX + ".driverClassName"));
        dbcpProps.put("url", TextUtil.getRequiredProperty(config, PREFIX + ".url"));
        dbcpProps.put("username", TextUtil.getRequiredProperty(config, PREFIX + ".username"));
        dbcpProps.put("password", TextUtil.getRequiredProperty(config, PREFIX + ".password"));
        dbcpProps.put("connectionProperties", stringifyProps(connectionProperties, ";"));
        log.debug("driver: " + dbcpProps.getProperty("user") + ", url: " + dbcpProps.getProperty("url") + ", username: "
                  + dbcpProps.getProperty("user"));

        try
        {
            ds = BasicDataSourceFactory.createDataSource(dbcpProps);
        }
        catch (Exception e)
        {
            throw new NoRequiredPropertyException(e.getMessage(), null);
        }

    }

    public Connection getConnection(WikiEngine engine) throws SQLException
    {
        return ds.getConnection();
    }

    private String stringifyProps(Properties props, String separator)
    {
        String s;
        StringBuffer sb = new StringBuffer();
        Enumeration<?> e = props.propertyNames();
        while (e.hasMoreElements())
        {
            s = (String) e.nextElement();
            sb.append(s + "=" + props.getProperty(s));
            if (e.hasMoreElements())
                sb.append(separator);
        }
        return sb.toString();
    }

}
