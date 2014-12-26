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

import org.apache.wiki.WikiEngine;
import org.apache.wiki.api.exceptions.NoRequiredPropertyException;

/*
 * History:
 *   2007-02-18 MT  Added static DRIVER_PROP_PREFIX.
 *   2007-02-16 MT  Added method parseAdditionalProperties() to support user specified
 *                  connection- and provider properties.
 *   2006-04-26 MT  Added connection = null to releaseConnection() to make sure
 *                  the connection is not accidentally closed again while being
 *                  re-used from a pool.
 */

/**
 * @author Milton Taylor
 * @author Mikkel Troest
 * @author glasius
 */
public abstract class ConnectionProvider
{

    protected static final String DRIVER_PROP_PREFIX = "driver";

    public abstract void initialize(WikiEngine engine, Properties wikiProps) throws NoRequiredPropertyException;

    public abstract Connection getConnection(WikiEngine engine) throws SQLException;

    public void releaseConnection(Connection connection)
    {
        try
        {
            if (connection != null)
            {
                connection.close();
                connection = null;
            }
        }
        catch (SQLException ex)
        {
            ex.printStackTrace();
        }
    }

    protected Properties parseAdditionalProperties(Properties config, String prefix)
    {

        int prefixSnip = prefix.length() + 1; /* add 1 for the separator, '.' */
        Properties props = new Properties();
        for (Enumeration<?> e = config.keys(); e.hasMoreElements();)
        {
            String rawProperty = (String) e.nextElement();
            if (rawProperty.startsWith(prefix))
            {
                String trimmedProperty = rawProperty.substring(prefixSnip);
                /*
                 * Hack. We'd rather get driverClassName/url/user/password
                 * through engine.getRequiredProp() driverClassName makes no
                 * sense as a property for driver- or provider-specific props
                 * anyway.
                 */
                if (!trimmedProperty.equalsIgnoreCase("driverClassName") & !trimmedProperty.equalsIgnoreCase("url")
                    & !trimmedProperty.equalsIgnoreCase("username") & !trimmedProperty.equalsIgnoreCase("password"))
                {
                    props.put(trimmedProperty, config.get(rawProperty));
                }
            }
        }
        return props;
    }

}
