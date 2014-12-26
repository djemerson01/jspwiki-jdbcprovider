/* 
    JSPWiki - a JSP-based WikiWiki clone.

    Copyright (C) 2004-2005 Xan Gregg (xan.gregg@forthgo.com)
    Copyright (C) 2014 David Emerson (david@ebizarts.com)

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

import java.io.FileInputStream;
import java.io.IOException;
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
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.apache.wiki.PageManager;
import org.apache.wiki.WikiEngine;
import org.apache.wiki.WikiPage;
import org.apache.wiki.api.exceptions.NoRequiredPropertyException;
import org.apache.wiki.api.exceptions.ProviderException;
import org.apache.wiki.providers.VersioningProvider;
import org.apache.wiki.providers.WikiPageProvider;
import org.apache.wiki.search.QueryItem;
import org.apache.wiki.search.SearchMatcher;
import org.apache.wiki.search.SearchResult;
import org.apache.wiki.search.SearchResultComparator;
import org.apache.wiki.util.ClassUtil;

/*
 * History:
 *   2007-02-16 MT  Renamed sql-prop page.deleteVersions to just page.delete
 *   2007-02-15 MT  Fixed migratePages() if a page didn't have an author attribute,
 *                  a SQLException was thrown, since CHANGE_BY is marked NOT NULL in the schema.
 *                  Now the author is simply set to "nobody".
 * 	 2007-02-13 MT  Changed logging to log4j.Logger in stead of deprecateded log4j.Category
 *                  Added method pageExists(String, int) to support the VersioningProvider interface
 *   2007-02-12 MT  Fixed migratePages() to comply with new schema/sql (added PAGE_REVNOTE)
 *   2006-09-30	MCT	Fix for bug: null pointer exception editing new page in getPageText()
 *   2006-08-22 SBG Total rewrite of the tables. Droped WIKI_PAGE_VERSIONS
 *                  Now only using WIKI_PAGE for page versions
 *   2006-03-26 XG  Fix a bug reported by Andreas Kohlbecker where the versions
 *                  table was not getting updated during a continuation edit
 *   2006-02-21 SBG When migrating the page orignal date is preserved. 
 *                  Database creation code example now to be found in database/
 *   2006-02-12 XG  Move all SQL statements into property file.
 *                  Expand implementation of Delete.
 *   2006-01-29 XG  Update for JSPWiki 2.3.72 (with help from Terry Steichen)
 *                   - added engine as argument to some methods
 *                   - added movePage method now required by WikiPageProvider
 *   2005-09-28 XG  Use jspwiki-s as property prefix for security.
 *   2005-09-07 XG  Always use java.util.Date for LastModifield field to friendlier comparisons.
 *   2005-08-31 XG  Remove legacy comment.
 *   2005-08-30 XG  Added changes suggested by Gregor Hagedorn:
 *                   - removed dependence on auto-incrementing ID fields
 *                   - added "continuation edit" time-out
 *                   - added notes for SQL Server DB creation
 *   2005-08-24 XG  Fixed possible resource leak with conditionally closed statements
 */

/**
 * Provides a database-based repository for Wiki pages. MySQL commands to create
 * the tables are provided in the code comments.
 * <br>
 * Based on Thierry Lach's DatabaseProvider, which supported Wiki pages but not
 * attachments.
 *
 * @author Mikkel Troest
 * @author Thierry Lach
 * @author Xan Gregg
 * @author S�ren Berg Glasius
 * @author Milton Taylor
 */
public class JDBCPageProvider extends JDBCBaseProvider implements WikiPageProvider, VersioningProvider
{

    WikiEngine m_WikiEngine;

    protected static final Logger log = Logger.getLogger(JDBCPageProvider.class);

    public void initialize(WikiEngine engine, Properties properties) throws NoRequiredPropertyException, IOException
    {
        debug("Initializing JDBCPageProvider");
        super.initialize(engine, properties);
        m_WikiEngine = engine;
        int count = getPageCount();
        debug("Page count at startup: " + count);
        debug("Database->" + getConfig().toString());
        if (getConfig().hasDesireToMigrate())
        {
            if (count == 0)
            {
                migratePages(engine);
            }
            else
            {
                info("Migration not possible, because the table is not empty.");
                info("   - either truncate table WIKI_PAGE or");
                info("   - remove migration flag");
            }
        }
    }

    public boolean pageExists(String page)
    {
        PreparedStatement pstmt = null;
        boolean found = false;
        Connection con = null;
        try
        {
            con = getConnection();
            // SELECT TOP 1 PAGE_VERSION FROM WIKI_PAGE WHERE PAGE_NAME = ?
            String sql = getSQL("exists");
            pstmt = con.prepareStatement(sql);
            pstmt.setString(1, page);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next())
            {
                found = true;
            }
        }
        catch (SQLException se)
        {
            error("unable to check existence for " + page, se);
        }
        finally
        {
            releaseConnection(pstmt, con);
        }
        return found;
    }

    /*
     * supports the VersioningProvider interface
     */
    public boolean pageExists(String page, int version)
    {
        PreparedStatement pstmt = null;
        boolean found = false;
        Connection con = null;
        try
        {
            con = getConnection();
            // SELECT VERSION FROM WIKI_PAGE WHERE NAME = ? AND VERSION = ?
            String sql = getSQL("versionExists");
            pstmt = con.prepareStatement(sql);
            pstmt.setString(1, page);
            pstmt.setInt(2, version);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next())
            {
                found = true;
            }
        }
        catch (SQLException se)
        {
            error("unable to check existence for " + page + "at version " + version, se);
        }
        finally
        {
            releaseConnection(pstmt, con);
        }
        return found;
    }

    public String getPageText(String page, int version) throws ProviderException
    {
        ResultSet rs = null;
        PreparedStatement pstmt = null;
        if (version == WikiPageProvider.LATEST_VERSION)
        {
            WikiPage current = getCurrentPageInfo(page);

            if (current != null)
                version = current.getVersion();
            else
                return (null);
        }
        debug("Get " + page + " version " + version);
        String pageText = null;
        Connection con = null;
        try
        {
            con = getConnection();
            String sql = getSQL("getVersion");
            // SELECT VERSION_TEXT FROM WIKI_PAGE_VERSIONS WHERE VERSION_NAME =
            // ? AND VERSION_NUM = ?
            pstmt = con.prepareStatement(sql);
            pstmt.setString(1, page);
            pstmt.setInt(2, version);
            rs = pstmt.executeQuery();

            if (rs.next())
            {
                pageText = rs.getString(1);
            }
            else
            {
                // This is okay.
                info("New page '" + page + "'");
            }

        }
        catch (SQLException se)
        {
            error("unable to get page text for " + page + ":" + version, se);
        }
        finally
        {
            releaseConnection(rs, pstmt, con);
        }
        return pageText;
    }

    public void putPageText(WikiPage page, String text)
    {
        String previousAuthor = "";
        Date previousModified = new Date(0l);
        int version = 0;
        PreparedStatement pstmt = null;
        Connection con = null;

        WikiPage latest = getCurrentPageInfo(page.getName());
        if (latest != null)
        {
            version = latest.getVersion();
            previousModified = latest.getLastModified();
            previousAuthor = latest.getAuthor();
        }

        // If same author and saved again within continuationEditTimeout, save
        // by
        // directly overwriting current version.
        // Create version, if not in continuationEdit, or if migrating, or if
        // page is non-existant
        boolean isDifferentAuthor = page.getAuthor() == null || page.getAuthor().equals("")
                                    || !page.getAuthor().equals(previousAuthor);
        boolean isContinuationEditTimeExpired = System.currentTimeMillis() > getConfig().getContinuationEditTimeout()
                                                                             + previousModified.getTime();

        boolean createVersion = m_migrating || !pageExists(page.getName()) || isDifferentAuthor || isContinuationEditTimeExpired;
        try
        {
            con = getConnection();
            if (createVersion)
            {
                // Insert page
                if (!pageExists(page.getName()))
                {
                    page.setVersion(1);
                }
                else
                {
                    page.setVersion(page.getVersion() + 1);
                }
                if (m_migrating && page.getLastModified() != null)
                {
                    page.setLastModified(page.getLastModified());
                }
                else
                {
                    page.setLastModified(new Date());
                }

                debug("Create page version: " + page);
                // Insert the version into database
                String sql = getSQL("insertPage");
                // INSERT INTO WIKI_PAGE (PAGE_NAME, PAGE_VERSION,
                // PAGE_MODIFIED, PAGE_MODIFIED_BY, PAGE_REVNOTE, PAGE_TEXT)
                // VALUES (?, ?, ?, ?, ?)
                pstmt = con.prepareStatement(sql);
                pstmt.setString(1, page.getName());
                pstmt.setInt(2, page.getVersion());

                pstmt.setTimestamp(3, new Timestamp(page.getLastModified().getTime()));
                pstmt.setString(4, page.getAuthor());
                pstmt.setString(5, (String) page.getAttribute(WikiPage.CHANGENOTE));
                pstmt.setString(6, text);
            }
            else
            {
                // Update page
                String sql = getSQL("updatePage");
                // UPDATE WIKI_PAGE SET PAGE_MODIFIED=?, PAGE_MODIFIED_BY=?,
                // PAGE_REVNOTE=?, PAGE_TEXT=? WHERE PAGE_NAME =? AND
                // PAGE_VERSION=?
                debug("Updating version: " + latest + " " + version);
                pstmt = con.prepareStatement(sql);
                pstmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
                pstmt.setString(2, page.getAuthor());
                pstmt.setString(3, (String) page.getAttribute(WikiPage.CHANGENOTE));
                pstmt.setString(4, text);
                pstmt.setString(5, page.getName());
                pstmt.setInt(6, version);
            }
            pstmt.execute();
        }
        catch (SQLException se)
        {
            error("Saving failed", se);
        }
        finally
        {
            releaseConnection(pstmt, con);
        }
    }

    public Collection<WikiPage> getAllPages() throws ProviderException
    {
        ResultSet rs = null;
        PreparedStatement pstmt = null;
        Collection<WikiPage> set = new ArrayList<WikiPage>();

        Connection con = null;
        try
        {
            con = getConnection();
            String sql = getSQL("getAllPages");
            // SELECT P1.PAGE_NAME, P1.PAGE_VERSION, P1.PAGE_MODIFIED,
            // P1.PAGE_MODIFIED_BY, P1.PAGE_REVNOTE FROM WIKI_PAGE P1 WHERE
            // P1.PAGE_VERSION = (SELECT MAX(PAGE_VERSION) FROM WIKI_PAGE P2
            // WHERE P2.PAGE_NAME=P1.PAGE_NAME)
            pstmt = con.prepareStatement(sql);
            rs = pstmt.executeQuery();
            while (rs.next())
            {
                WikiPage page = new WikiPage(getEngine(), rs.getString(1));
                page.setVersion(rs.getInt(2));
                // use Java Date for friendlier comparisons with other dates
                page.setLastModified(new java.util.Date(rs.getTimestamp(3).getTime()));
                page.setAuthor(rs.getString(4));
                if (rs.getString(5) != null)
                    page.setAttribute(WikiPage.CHANGENOTE, rs.getString(5));

                set.add(page);
            }
        }
        catch (SQLException se)
        {
            error("unable to get all pages", se);
        }
        finally
        {
            releaseConnection(rs, pstmt, con);
        }

        return set;
    }

    public Collection<WikiPage> getAllChangedSince(Date date)
    {
        ResultSet rs = null;
        PreparedStatement pstmt = null;
        Collection<WikiPage> set = new ArrayList<WikiPage>();

        Connection con = null;
        try
        {
            con = getConnection();
            String sql = getSQL("getAllChangedSince");
            // SELECT P1.PAGE_NAME, P1.PAGE_VERSION, P1.PAGE_MODIFIED,
            // P1.PAGE_MODIFIED_BY, P1.PAGE_REVNOTE FROM WIKI_PAGE P1 WHERE
            // P1.PAGE_MODIFIED > ? AND P1.PAGE_VERSION = (SELECT
            // MAX(PAGE_VERSION) FROM WIKI_PAGE P2 WHERE
            // P2.PAGE_NAME=P1.PAGE_NAME)
            pstmt = con.prepareStatement(sql);
            pstmt.setTimestamp(1, new Timestamp(date.getTime()));
            rs = pstmt.executeQuery();
            while (rs.next())
            {
                WikiPage page = new WikiPage(getEngine(), rs.getString(1));
                page.setVersion(rs.getInt(2));
                // use Java Date for friendlier comparisons with other dates
                page.setLastModified(new java.util.Date(rs.getTimestamp(3).getTime()));
                page.setAuthor(rs.getString(4));
                if (rs.getString(5) != null)
                    page.setAttribute(WikiPage.CHANGENOTE, rs.getString(5));
                set.add(page);
            }
        }
        catch (SQLException se)
        {
            error("unable to get all pages since " + date, se);
        }
        finally
        {
            releaseConnection(rs, pstmt, con);
        }

        return set;
    }

    public int getPageCount()
    {
        ResultSet rs = null;
        Statement stmt = null;
        int count = 0;
        // Check if the page table exists
        Connection connection = null;
        try
        {
            connection = getConnection();
            String sql = getSQL("getPageCount");
            // SELECT COUNT(DISTINCT PAGE_NAME) from WIKI_PAGE
            stmt = connection.createStatement();
            rs = stmt.executeQuery(sql);
            rs.next();
            count = rs.getInt(1);
        }
        catch (SQLException se)
        {
            error("unable to get page count ", se);
        }
        finally
        {
            releaseConnection(rs, stmt, connection);
        }
        return count;

    }

    public Collection<SearchResult> findPages(QueryItem[] query)
    {
        Collection<SearchResult> res = new TreeSet<SearchResult>(new SearchResultComparator());
        SearchMatcher matcher = new SearchMatcher(getEngine(), query);

        Collection<?> wikipages = null;
        try
        {
            wikipages = getAllPages();
        }
        catch (ProviderException e)
        {
        }

        // nextfile:
        for (Iterator<?> i = wikipages.iterator(); i.hasNext();)
        {
            // debug("Searching page "+wikipages[i].getPath() );
            WikiPage page = (WikiPage) i.next();
            try
            {
                String pagetext = getPageText(page.getName(), page.getVersion());
                SearchResult comparison = matcher.matchPageContent(page.getName(), pagetext);
                if (comparison != null)
                {
                    res.add(comparison);
                }

            }
            catch (IOException se)
            {
                error("Failed to read", se);
            }
            catch (ProviderException pe)
            {
                error("Failed to read page: " + page, pe);
            }
        }

        return res;
    }

    /**
     * Always returns the latest version here.
     */
    private WikiPage getCurrentPageInfo(String pageName)
    {
        ResultSet rs = null;
        PreparedStatement pstmt = null;
        WikiPage page = null;
        Connection con = null;
        try
        {
            con = getConnection();
            String sql = getSQL("getCurrentInfo");
            // SELECT P1.PAGE_VERSION, P1.PAGE_MODIFIED, P1.PAGE_MODIFIED_BY,
            // P1.REVNOTE FROM WIKI_PAGE P1 WHERE P1.PAGE_NAME=? AND
            // P1.PAGE_VERSION = (SELECT MAX(PAGE_VERSION) FROM WIKI_PAGE P2
            // WHERE P2.PAGE_NAME=P1.PAGE_NAME)
            pstmt = con.prepareStatement(sql);
            pstmt.setString(1, pageName);
            rs = pstmt.executeQuery();

            if (rs.next())
            {
                page = new WikiPage(getEngine(), pageName);
                page.setVersion(rs.getInt(1));
                // use Java Date for friendlier comparisons with other dates
                page.setLastModified(new java.util.Date(rs.getTimestamp(2).getTime()));
                page.setAuthor(rs.getString(3));
                if (rs.getString(4) != null)
                    page.setAttribute(WikiPage.CHANGENOTE, rs.getString(4));
            }
        }
        catch (SQLException se)
        {
            error("unable to get current page info for " + pageName, se);
        }
        finally
        {
            releaseConnection(rs, pstmt, con);
        }
        return page;
    }

    /**
     * Return the correct version of the page.
     */
    public WikiPage getPageInfo(String page, int version) throws ProviderException
    {
        ResultSet rs = null;
        PreparedStatement pstmt = null;
        Connection con = null;
        WikiPage p = null;
        if (version == WikiPageProvider.LATEST_VERSION)
        {
            p = getCurrentPageInfo(page);
        }
        else
        {
            try
            {
                con = getConnection();
                String sql = getSQL("getVersionInfo");
                // SELECT PAGE_VERSION, PAGE_MODIFIED, PAGE_MODIFIED_BY,
                // PAGE_REVNOTE FROM WIKI_PAGE WHERE PAGE_NAME = ? AND
                // PAGE_VERSION = ?
                pstmt = con.prepareStatement(sql);
                pstmt.setString(1, page);
                pstmt.setInt(2, version);
                rs = pstmt.executeQuery();

                if (rs.next())
                {
                    p = new WikiPage(getEngine(), page);
                    p.setVersion(rs.getInt(1));
                    // use Java Date for friendlier comparisons with other dates
                    p.setLastModified(new java.util.Date(rs.getTimestamp(2).getTime()));
                    p.setAuthor(rs.getString(3));
                    if (rs.getString(4) != null)
                        p.setAttribute(WikiPage.CHANGENOTE, rs.getString(4));

                }

            }
            catch (SQLException se)
            {
                error("unable to get page info for " + page + ":" + version, se);
            }
            finally
            {
                releaseConnection(rs, pstmt, con);
            }
        }
        return p;
    }

    /**
     * Provide the list of versions.
     */
    public List<WikiPage> getVersionHistory(String page) throws ProviderException
    {
        ResultSet rs = null;
        PreparedStatement pstmt = null;
        List<WikiPage> list = new ArrayList<WikiPage>();

        Connection con = null;
        try
        {
            con = getConnection();
            String sql = getSQL("getVersions");
            // SELECT PAGE_VERSION, PAGE_MODIFIED, PAGE_MODIFIED_BY,
            // PAGE_REVNOTE, FROM WIKI_PAGE WHERE PAGE_NAME = ? ORDER BY
            // PAGE_VERSION DESC
            pstmt = con.prepareStatement(sql);
            pstmt.setString(1, page);
            rs = pstmt.executeQuery();

            while (rs.next())
            {
                WikiPage p = new WikiPage(getEngine(), page);
                p.setVersion(rs.getInt(1));
                // use Java Date for friendlier comparisons with other dates
                p.setLastModified(new java.util.Date(rs.getTimestamp(2).getTime()));
                p.setAuthor(rs.getString(3));
                if (rs.getString(4) != null)
                    p.setAttribute(WikiPage.CHANGENOTE, rs.getString(4));
                list.add(p);
            }
        }
        catch (SQLException se)
        {
            error("unable to get version history for " + page, se);
        }
        finally
        {
            releaseConnection(rs, pstmt, con);
        }
        debug("page versions: " + list);
        return list;
    }

    public String getProviderInfo()
    {
        return "JDBC page provider";
    }

    public void deleteVersion(String pageName, int version) throws ProviderException
    {
        Connection con = null;
        PreparedStatement psVer = null;
        try
        {
            con = getConnection();
            String sql = getSQL("deleteVersion");
            // DELETE FROM WIKI_PAGE WHERE PAGE_NAME = ? AND PAGE_VERSION = ?
            psVer = con.prepareStatement(sql);
            psVer.setString(1, pageName);
            psVer.setInt(2, version);
            psVer.execute();
        }
        catch (SQLException se)
        {
            error("Delete version failed " + pageName + ":" + version, se);
        }
        finally
        {
            releaseConnection(psVer, con);
        }
    }

    public void deletePage(String pageName) throws ProviderException
    {
        Connection connection = null;
        PreparedStatement psVer = null;
        try
        {
            connection = getConnection();
            String sql = getSQL("delete");
            // DELETE FROM WIKI_PAGE WHERE VERSION_NAME = ?
            psVer = connection.prepareStatement(sql);
            psVer.setString(1, pageName);
            psVer.execute();
        }
        catch (SQLException se)
        {
            error("Delete failed " + pageName, se);
        }
        finally
        {
            releaseConnection(psVer, connection);
        }
    }

    /* (non-Javadoc)
     * @see org.apache.wiki.providers.WikiPageProvider#movePage(java.lang.String, java.lang.String)
     */
    public void movePage(String from, String to) throws ProviderException
    {
        PreparedStatement pstmt = null;
        Connection con = null;
        try
        {
            con = getConnection();
            String sql = getSQL("move");
            // UPDATE WIKI_PAGE SET PAGE_NAME = ? WHERE PAGE_NAME = ?
            pstmt = con.prepareStatement(sql);
            pstmt.setString(1, to);
            pstmt.setString(2, from);
            pstmt.execute();

        }
        catch (SQLException se)
        {
            String message = "Moving '" + from + "' to '" + to + "' failed";
            error(message, se);
            throw new ProviderException(message + ": " + se.getMessage());
        }
        finally
        {
            releaseConnection(pstmt, con);
        }
    }

    /**
     * Copies allPages from one provider to this provider. The source, "import"
     * provider is specified by the properties file at the given path.
     * 2007-02-12 MT Fixed migratePages() to comply with new schema/sql (added
     * PAGE_REVNOTE)
     */
    @SuppressWarnings("unchecked")
    private void migratePages(WikiEngine engine) throws IOException
    {
        PreparedStatement pstmt;
        Connection con;
        Properties importProps = new Properties();
        info("Migrating pages from: " + getConfig().getMigrateFrom());
        importProps.load(new FileInputStream(getConfig().getMigrateFrom()));
        String classname = importProps.getProperty(PageManager.PROP_PAGEPROVIDER);

        WikiPageProvider importProvider;
        try
        {
            Class<?> providerclass = ClassUtil.findClass("com.ecyrd.jspwiki.providers", classname);
            importProvider = (WikiPageProvider) providerclass.newInstance();
        }
        catch (Exception e)
        {
            error("Unable to locate/instantiate import provider class " + classname, e);
            return;
        }
        try
        {
            m_migrating = true;
            importProvider.initialize(engine, importProps);

            Collection<WikiPage> allPages = importProvider.getAllPages();
            con = getConnection();
            String sql = getSQL("insertPage");
            // INSERT INTO WIKI_PAGE (PAGE_NAME, PAGE_VERSION, PAGE_MODIFIED,
            // PAGE_MODIFIED_BY, PAGE_REVNOTE, PAGE_TEXT) VALUES (?, ?, ?, ?, ?)
            pstmt = con.prepareStatement(sql);
            for (Iterator<WikiPage> i = allPages.iterator(); i.hasNext();)
            {
                WikiPage latest = i.next();

                List<WikiPage> pages = importProvider.getVersionHistory(latest.getName());
                if (pages.size() > 0)
                {

                    info("Migrating " + pages.size() + " versions of " + latest.getName());
                    for (Iterator<WikiPage> p = pages.iterator(); p.hasNext();)
                    {
                        WikiPage page = p.next();
                        String text = importProvider.getPageText(page.getName(), page.getVersion());
                        // Insert the version into database
                        pstmt.setString(1, page.getName());
                        pstmt.setInt(2, page.getVersion());

                        pstmt.setTimestamp(3, new Timestamp(page.getLastModified().getTime()));
                        if (page.getAuthor() != null)
                            pstmt.setString(4, page.getAuthor());
                        else
                            pstmt.setString(4, "nobody");
                        pstmt.setString(5, (String) page.getAttribute(WikiPage.CHANGENOTE));
                        pstmt.setString(6, text);
                        pstmt.execute();
                    }

                }
                else
                {
                    info("Migrating " + latest.getName());
                    String text = importProvider.getPageText(latest.getName(), latest.getVersion());
                    // Insert the version into database
                    pstmt.setString(1, latest.getName());
                    pstmt.setInt(2, latest.getVersion());

                    pstmt.setTimestamp(3, new Timestamp(latest.getLastModified().getTime()));
                    if (latest.getAuthor() != null)
                        pstmt.setString(4, latest.getAuthor());
                    else
                        pstmt.setString(4, "nobody");
                    pstmt.setString(5, (String) latest.getAttribute(WikiPage.CHANGENOTE));
                    pstmt.setString(6, text);
                    pstmt.execute();
                }
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
        catch (SQLException e)
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
        return super.getSQL("page." + key);
    }

}
