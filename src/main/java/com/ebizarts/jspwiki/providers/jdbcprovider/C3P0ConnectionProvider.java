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

//import javax.sql.DataSource;



import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.apache.wiki.InternalWikiException;
import org.apache.wiki.WikiEngine;
import org.apache.wiki.api.exceptions.NoRequiredPropertyException;
import org.apache.wiki.util.TextUtil;

import com.mchange.v2.c3p0.DataSources;

/*
 * History:
 * 2007-02-16 MT  Added support for additional driver- and c3p0 properties 
 * 2007-02-16 MT  Initial version
 */

/**
 * @author Mikkel Troest
 */
public class C3P0ConnectionProvider extends ConnectionProvider
{
    protected static final Logger log = Logger.getLogger(C3P0ConnectionProvider.class);

    private static final String PREFIX = "c3p0";
    private String driver;
    private String url;
    private Properties connectionProperties;
    private Properties c3p0Properties;
    private DataSource ds = null;

    /** Creates a new instance of C3P0ConnectionProvider */
    public C3P0ConnectionProvider()
    {
    }

    public void initialize(WikiEngine engine, Properties config) throws NoRequiredPropertyException
    {
        log.debug("Initializing C3P0ConnectionProvider");

        driver = TextUtil.getRequiredProperty(config, PREFIX + ".driverClassName");
        url = TextUtil.getRequiredProperty(config, PREFIX + ".url");

        connectionProperties = parseAdditionalProperties(config, DRIVER_PROP_PREFIX);

        connectionProperties.put("user", TextUtil.getRequiredProperty(config, PREFIX + ".username"));
        connectionProperties.put("password", TextUtil.getRequiredProperty(config, PREFIX + ".password"));
        log.debug("driver: " + driver + ", url: " + url + ", username: " + connectionProperties.getProperty("user"));

        c3p0Properties = parseAdditionalProperties(config, PREFIX);

        try
        {
            Class.forName(driver);

            ds = DataSources.pooledDataSource(DataSources.unpooledDataSource(url, connectionProperties), c3p0Properties);

        }
        catch (SQLException ex)
        {
            log.error("Failed to create ConnectionPool", ex);
            throw new InternalWikiException("SQLException during connection pool creation: " + ex.getMessage());
        }
        catch (ClassNotFoundException ex)
        {
            log.error("Failed to create ConnectionPool", ex);
            throw new InternalWikiException("ClassNotFoundException during connection pool creation: " + ex.getMessage());
        }

    }

    public Connection getConnection(WikiEngine engine) throws SQLException
    {
        return ds.getConnection();
    }

}
