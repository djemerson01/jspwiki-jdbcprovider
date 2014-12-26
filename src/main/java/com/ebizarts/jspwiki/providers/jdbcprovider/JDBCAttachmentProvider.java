/*
    JSPWiki - a JSP-based WikiWiki clone.
 
    Copyright (C) 2004-2005 Xan Gregg (xan.gregg@forthgo.com)
    Copyright (C) 2006-2014 David Emerson (david@ebizarts.com)
 
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.wiki.WikiEngine;
import org.apache.wiki.WikiPage;
import org.apache.wiki.WikiProvider;
import org.apache.wiki.api.exceptions.NoRequiredPropertyException;
import org.apache.wiki.api.exceptions.ProviderException;
import org.apache.wiki.attachment.Attachment;
import org.apache.wiki.attachment.AttachmentManager;
import org.apache.wiki.providers.WikiAttachmentProvider;
import org.apache.wiki.search.QueryItem;
import org.apache.wiki.util.ClassUtil;
import org.apache.wiki.util.FileUtil;

/*
 * History:
 * 	 2007-02-13 MT Changed logging to log4j.Logger in stead of deprecateded log4j.Category
 *   2006-09-30 MCT Added missing check for version == WikiProvider.LATEST_VERSION
 *   				in getAttachmentInfo(). Slight refactoring of params to findLatestVersion().
 *   2006-08-25 SBG Touching up connection and prepared statement cleanup
 *   2006-05-30 MT  Added missing check for version == WikiProvider.LATEST_VERSION
 *                  in getAttachmentData(Attachment att)
 *   2006-04-26 MT  Changed comment for listAttachments() to reflect actual SQL
 *                  statements.
 *   2006-04-24 MT  listAttachments now gets latest attachments in stead of only
 *                  version 1. database/*.attachments.properties:
 *                  jspwiki-s.JDBCAttachmentProvider.getList changed accordingly.
 *   2006-02-21 SBG When migrating the attachment orignal date is preserved.
 *                  Database creation code example now to be found in database/
 *   2005-09-28 XG  Use jspwiki-s as property prefix for security.
 *   2005-09-07 XG  Always use java.util.Date for LastModifield field to friendlier comparisons.
 */

/**
 * Provides a database-based repository for Wiki attachments. MySQL commands to
 * create the tables are provided in the code comments.
 * <br>
 * Based on Thierry Lach's DatabaseProvider, which supported Wiki pages but not
 * attachments.
 *
 * @author Mikkel Troest
 * @author Thierry Lach
 * @author Xan Gregg
 * @author S�ren Berg Glasius
 * @author Milton Taylor
 * @see JDBCPageProvider
 */
public class JDBCAttachmentProvider extends JDBCBaseProvider implements WikiAttachmentProvider
{

    WikiEngine m_WikiEngine;

    protected static final Logger log = Logger.getLogger(JDBCAttachmentProvider.class);

    public String getProviderInfo()
    {
        return "JDBC attachment provider";
    }

    public void initialize(WikiEngine engine, Properties properties) throws NoRequiredPropertyException, IOException
    {
        debug("Initializing JDBCAttachmentProvider");
        super.initialize(engine, properties);
        m_WikiEngine = engine;
        int count = getAttachmentCount();
        log.debug("Attachment count at startup: " + count);
        if (getConfig().hasDesireToMigrate())
        {
            if (count == 0)
            {
                migrateAttachments(engine);
            }
            else
            {
                info("Attachment migration not possible, because the table is not empty.");
                info("   - either truncate table WIKI_ATT or");
                info("   - remove migration flag");
            }
        }

    }

    public int getAttachmentCount()
    {
        int count = 0;
        Connection connection = null;
        Statement stmt = null;
        ResultSet rs = null;
        try
        {
            connection = getConnection();
            String sql = getSQL("getCount");
            // SELECT COUNT(*) FROM WIKI_ATT
            stmt = connection.createStatement();
            rs = stmt.executeQuery(sql);
            rs.next();
            count = rs.getInt(1);
        }
        catch (SQLException se)
        {
            error("unable to get attachment count ", se);
        }
        finally
        {
            releaseConnection(rs, stmt, connection);
        }
        return count;

    }

    // apparently version number and size should not be relied upon at this
    // point
    public void putAttachmentData(Attachment att, InputStream dataStream) throws ProviderException, IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileUtil.copyContents(dataStream, baos);
        byte data[] = baos.toByteArray();
        int version = findLatestVersion(att.getParentName(), att.getFileName()) + 1;

        // att.setVersion(version);
        Connection connection = null;
        PreparedStatement pstmt = null;
        try
        {
            connection = getConnection();
            String sql = getSQL("insert");
            // INSERT INTO WIKI_ATT
            // (ATT_PAGENAME, ATT_FILENAME, ATT_VERSION, ATT_MODIFIED,
            // ATT_MODIFIED_BY, ATT_REVNOTE, ATT_DATA, ATT_LENGTH)
            // VALUES (?, ?, ?, ?, ?, ?,?,?)

            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, att.getParentName());
            pstmt.setString(2, att.getFileName());
            if (m_migrating)
            {
                pstmt.setInt(3, att.getVersion());
            }
            else
            {
                pstmt.setInt(3, version);
            }

            Timestamp d;
            if (m_migrating && att.getLastModified() != null)
            {
                d = new Timestamp(att.getLastModified().getTime());
            }
            else
            {
                d = new Timestamp(System.currentTimeMillis());
            }

            pstmt.setTimestamp(4, d);
            pstmt.setString(5, att.getAuthor());
            pstmt.setString(6, (String) att.getAttribute(WikiPage.CHANGENOTE));
            pstmt.setBytes(7, data);
            pstmt.setInt(8, data.length);
            pstmt.execute();
        }
        catch (SQLException se)
        {
            error("Saving attachment failed " + att, se);
        }
        finally
        {
            releaseConnection(pstmt, connection);
        }
    }

    public InputStream getAttachmentData(Attachment att) throws ProviderException, IOException
    {

        int version = att.getVersion();
        if (version == WikiProvider.LATEST_VERSION)
            version = findLatestVersion(att.getParentName(), att.getFileName());

        InputStream result = null;
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try
        {
            connection = getConnection();
            String sql = getSQL("getData");
            // SELECT ATT_DATA FROM WIKI_ATT WHERE ATT_PAGENAME = ? AND
            // ATT_FILENAME = ? AND ATT_VERSION = ?

            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, att.getParentName());
            pstmt.setString(2, att.getFileName());
            pstmt.setInt(3, version);
            rs = pstmt.executeQuery();

            if (rs.next())
            {
                byte[] bytes = rs.getBytes(1);
                result = new ByteArrayInputStream(bytes);
            }
            else
            {
                error("No attachments to read; '" + att + "'", new SQLException("empty attachment set"));
            }

        }
        catch (SQLException se)
        {
            error("Unable to read attachment '" + att + "'", se);
        }
        finally
        {
            releaseConnection(rs, pstmt, connection);
        }
        return result;
    }

    // latest versions only
    public Collection<Attachment> listAttachments(WikiPage page) throws ProviderException
    {

        Collection<Attachment> result = new ArrayList<Attachment>();
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try
        {
            connection = getConnection();
            String sql = getSQL("getList");
            // SELECT ATT_LENGTH, ATT_FILENAME, ATT_MODIFIED, ATT_MODIFIED_BY,
            // ATT_REVNOTE, ATT_VERSION FROM WIKI_ATT WHERE ATT_PAGENAME = ?
            // ORDER BY ATT_FILENAME, ATT_VERSION DESC;

            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, page.getName());
            rs = pstmt.executeQuery();

            String previousFileName = "";
            while (rs.next())
            {
                int size = rs.getInt(1);
                String fileName = rs.getString(2);
                if (fileName.equals(previousFileName))
                    continue; // only add latest version
                Attachment att = new Attachment(getEngine(), page.getName(), fileName);
                att.setSize(size);
                // use Java Date for friendlier comparisons with other dates
                att.setLastModified(new java.util.Date(rs.getTimestamp(3).getTime()));
                att.setAuthor(rs.getString(4));
                if (rs.getString(5) != null)
                    att.setAttribute(WikiPage.CHANGENOTE, rs.getString(5));
                att.setVersion(rs.getInt(6));
                result.add(att);
                previousFileName = fileName.toString();
            }

        }
        catch (SQLException se)
        {
            error("Unable to list attachments", se);
        }
        finally
        {
            releaseConnection(rs, pstmt, connection);
        }
        return result;
    }

    public Collection<?> findAttachments(QueryItem[] query)
    {

        return new ArrayList<Object>(); // fixme
    }

    public List<Attachment> listAllChanged(Date timestamp) throws ProviderException
    {
        List<Attachment> changedList = new ArrayList<Attachment>();

        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try
        {
            connection = getConnection();
            String sql = getSQL("getChanged");
            // SELECT ATT_PAGENAME, ATT_FILENAME, LENGTH(ATT_DATA),
            // ATT_MODIFIED, ATT_MODIFIED_BY, ATT_REVNOTE, ATT_VERSION
            // FROM WIKI_ATT WHERE ATT_MODIFIED > ? ORDER BY ATT_MODIFIED DESC

            pstmt = connection.prepareStatement(sql);
            pstmt.setTimestamp(1, new Timestamp(timestamp.getTime()));
            rs = pstmt.executeQuery();
            while (rs.next())
            {
                Attachment att = new Attachment(getEngine(), rs.getString(1), rs.getString(2));
                att.setSize(rs.getInt(3));
                // use Java Date for friendlier comparisons with other dates
                att.setLastModified(new java.util.Date(rs.getTimestamp(4).getTime()));
                att.setAuthor(rs.getString(5));
                if (rs.getString(6) != null)
                    att.setAttribute(WikiPage.CHANGENOTE, rs.getString(6));
                att.setVersion(rs.getInt(7));
                changedList.add(att);
            }
        }
        catch (SQLException se)
        {
            error("Error getting changed list, since " + timestamp, se);
        }
        finally
        {
            releaseConnection(rs, pstmt, connection);
        }

        return changedList;
    }

    public Attachment getAttachmentInfo(WikiPage page, String name, int version) throws ProviderException
    {

        if (version == LATEST_VERSION)
            version = findLatestVersion(page.getName(), name);

        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try
        {
            connection = getConnection();
            String sql = getSQL("getInfo"); // latest version is first
            // SELECT ATT_LENGTH, ATT_MODIFIED, ATT_MODIFIED_BY, ATT_REVNOTE
            // FROM WIKI_ATT WHERE ATT_PAGENAME = ? AND ATT_FILENAME = ? AND
            // ATT_VERSION = ?

            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, page.getName());
            pstmt.setString(2, name);
            pstmt.setInt(3, version);
            rs = pstmt.executeQuery();

            Attachment att = null;
            if (rs.next())
            {
                att = new Attachment(getEngine(), page.getName(), name);
                att.setSize(rs.getInt(1));
                // use Java Date for friendlier comparisons with other dates
                att.setLastModified(new java.util.Date(rs.getTimestamp(2).getTime()));
                att.setAuthor(rs.getString(3));
                att.setVersion(version);
                if (rs.getString(4) != null)
                    att.setAttribute(WikiPage.CHANGENOTE, rs.getString(4));
            }
            else
            {
                debug("No attachment info for " + page + "/" + name + ":" + version);
            }
            return att;
        }
        catch (SQLException se)
        {
            error("Unable to get attachment info for " + page + "/" + name + ":" + version, se);
            return null;
        }
        finally
        {
            releaseConnection(rs, pstmt, connection);
        }
    }

    /**
     * Goes through the repository and decides which version is the newest one
     * in that directory.
     *
     * @return Latest version number in the repository, or 0, if there is no
     *         page in the repository.
     */
    private int findLatestVersion(String PageName, String FileName)
    {
        int version = 0;
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try
        {
            connection = getConnection();
            String sql = getSQL("getLatestVersion");
            // SELECT ATT_VERSION FROM WIKI_ATT WHERE ATT_PAGENAME = ? AND
            // ATT_FILENAME = ? ORDER BY ATT_VERSION DESC LIMIT 1

            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, PageName);
            pstmt.setString(2, FileName);
            rs = pstmt.executeQuery();

            if (rs.next())
                version = rs.getInt(1);

        }
        catch (SQLException se)
        {
            error("Error trying to find latest attachment: " + PageName + "/" + FileName, se);
        }
        finally
        {
            releaseConnection(rs, pstmt, connection);
        }
        return version;
    }

    public List<Attachment> getVersionHistory(Attachment att)
    {
        List<Attachment> list = new ArrayList<Attachment>();
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try
        {
            connection = getConnection();
            String sql = getSQL("getVersions"); // latest version is first
            // SELECT ATT_LENGTH, ATT_MODIFIED, ATT_MODIFIED_BY, ATT_REVNOTE,
            // ATT_VERSION FROM WIKI_ATT WHERE ATT_PAGENAME = ? AND ATT_FILENAME
            // = ? ORDER BY ATT_VERSION DESC

            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, att.getParentName());
            pstmt.setString(2, att.getFileName());
            rs = pstmt.executeQuery();

            while (rs.next())
            {
                Attachment vAtt = new Attachment(getEngine(), att.getParentName(), att.getFileName());
                vAtt.setSize(rs.getInt(1));
                // use Java Date for friendlier comparisons with other dates
                vAtt.setLastModified(new java.util.Date(rs.getTimestamp(2).getTime()));
                vAtt.setAuthor(rs.getString(3));
                if (rs.getString(4) != null)
                    vAtt.setAttribute(WikiPage.CHANGENOTE, rs.getString(4));
                vAtt.setVersion(rs.getInt(5));
                list.add(vAtt);
            }

        }
        catch (SQLException se)
        {
            error("Unable to list attachment version history for " + att, se);
        }
        finally
        {
            releaseConnection(rs, pstmt, connection);
        }
        return list;
    }

    public void deleteVersion(Attachment att) throws ProviderException
    {
        PreparedStatement pstmt = null;
        Connection connection = null;
        try
        {
            connection = getConnection();
            String sql = getSQL("deleteVersion");
            // DELETE FROM WIKI_ATT WHERE ATT_PAGENAME = ? AND ATT_FILENAME = ?
            // AND ATT_VERSION = ?

            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, att.getParentName());
            pstmt.setString(2, att.getFileName());
            pstmt.setInt(3, att.getVersion());
            pstmt.execute();
        }
        catch (SQLException se)
        {
            error("Delete attachment version failed " + att, se);
        }
        finally
        {
            releaseConnection(pstmt, connection);
        }
    }

    public void deleteAttachment(Attachment att) throws ProviderException
    {
        PreparedStatement pstmt = null;
        Connection connection = null;
        try
        {
            connection = getConnection();
            String sql = getSQL("delete");
            // DELETE FROM WIKI_ATT WHERE ATT_PAGENAME = ? AND ATT_FILENAME = ?

            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, att.getParentName());
            pstmt.setString(2, att.getFileName());
            pstmt.execute();
            pstmt.close();
        }
        catch (SQLException se)
        {
            error("Delete attachment failed " + att, se);
        }
        finally
        {
            releaseConnection(pstmt, connection);
        }
    }

    /* (non-Javadoc)
     * @see org.apache.wiki.providers.WikiAttachmentProvider#moveAttachmentsForPage(java.lang.String, java.lang.String)
     */
    public void moveAttachmentsForPage(String oldParent, String newParent) throws ProviderException
    {
        Connection connection = null;
        PreparedStatement ps = null;
        try
        {
            connection = getConnection();
            String sql = getSQL("move");
            // UPDATE WIKI_ATT SET ATT_PAGE_NAME = ? WHERE ATT_PAGE_NAME = ?

            ps = connection.prepareStatement(sql);
            ps.setString(1, newParent);
            ps.setString(2, oldParent);
            ps.execute();
        }
        catch (SQLException se)
        {
            String message = "Moving attachment '" + oldParent + "' to '" + newParent + "' failed";
            error(message, se);
            throw new ProviderException(message + ": " + se.getMessage());
        }
        finally
        {
            releaseConnection(ps, connection);
        }
    }

    /**
     * Copies pages from one provider to this provider. The source, "import"
     * provider is specified by the properties file at the given path.
     */
    @SuppressWarnings("unchecked")
    private void migrateAttachments(WikiEngine engine) throws IOException
    {
        Properties importProps = new Properties();
        log.info("Migrating attachments from: " + getConfig().getMigrateFrom());
        importProps.load(new FileInputStream(getConfig().getMigrateFrom()));
        String classname = importProps.getProperty(AttachmentManager.PROP_PROVIDER);

        WikiAttachmentProvider importProvider;
        try
        {
            Class<?> providerclass = ClassUtil.findClass("com.ecyrd.jspwiki.providers", classname);
            importProvider = (WikiAttachmentProvider) providerclass.newInstance();
        }
        catch (Exception e)
        {
            log.error("Unable to locate/instantiate import provider class " + classname, e);
            return;
        }
        try
        {
            m_migrating = true;
            importProvider.initialize(engine, importProps);

            List<Attachment> attachments = importProvider.listAllChanged(new Date(0));
            for (Iterator<Attachment> i = attachments.iterator(); i.hasNext();)
            {
                Attachment att = i.next();
                InputStream data = importProvider.getAttachmentData(att);
                info("Migrating Attachment: " + att);
                putAttachmentData(att, data);
            }
        }
        catch (ProviderException e)
        {
            throw new IOException(e.getMessage());
        }
        catch (NoRequiredPropertyException e)
        {
            throw new IOException(e.getMessage());
        }
        finally
        {
            m_migrating = false;
        }
    }

    public Logger getLog()
    {
        return log;
    }

    public String getSQL(String key)
    {
        return super.getSQL("attachment." + key);
    }

}
