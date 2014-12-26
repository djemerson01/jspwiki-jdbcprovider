jspwiki-jdbcprovider
====================

JSPWiki has a pluggable content provider system. This package supplies providers for page and attachment content backed by a SQL database. This project is an update and refactoring of the JDBCProvider package originally created and maintained as described below. Much of the description below is from the version released in 2007. 

    JDBCProvider - a JDBC-based page provider for JSPWiki.

    Copyright (C) 2004-2007 The JDBCProvider development team.
    Copyright (C) 2014 David Emerson (david@ebizarts.com)
    
    The original JDBCProvider developer team members are:
      Xan Gregg
      Soeren Berg Glasius
      Mikkel Troest
      Milt Taylor

######SUMMARY

JSPWiki has a pluggable content provider system. This package supplies
providers for page and attachment content backed by a SQL database.

######STATUS:

    Beta release
    Released 2014-12-26
    Tested with JSPWiki 2.10.1

######MOST RECENT CHANGES

* Refactor to build against latest JSPWiki and adapt for building with Maven

Previously ...

* Minor bug fixes
* Works with latest jspwiki versions
* Works with multiple wiki instances, i.e. multiple databases
* Change notes are now supported
* The VersioningProvider interface is now implemented
* Added support for PostgreSQL
* Added support for Microsoft SQL Server
* Added support for driver-specific connection properties
* Added support for DBCP connection pools
* Added support for C3P0 connection pools
* Added support for JNDI data sources
* Added support for DBCP- and C3P0-specifc pool properties


RECENT CHANGES

All SQL code has been pulled out into separate properties files for easier
adaption to other databases. Currently there are five flavours supported: mysql, 
sqlanywhere, mssql, postgresql, and sybase.
Adapting it to other databases should be close to trivial :-)


######INSTALL

* If you're upgrading from a previous version, please read the section UPGRADE 
  after reading this section

Basically,

 - Copy the dist/JDBCProvider.jar file into JSPWiki's WEB-INF/lib directory.
 - If you are planing to use DBCP, copy commons-dbcp-1.2.1.jar and commons-pool-1.2.jar in lib  to WEB-INF/lib 
 - If you are planning to use C3P0, copy c3p0-0.9.1.jar in lib to WEB-INF/lib
 - Copy the appropiate databasedriver to WEB-INF/lib
 - Copy the jdbcprovider.properties into WEB-INF directory
 - Edit the jdbcprovider.properties so that it reflects your favorite database-connection option.
 - Copy the jdbcprovider.<flavour>.properties (e.g. jdbcprovider.syase.properties) into WEB-INF directory
 
 - Merge the jspwiki.aditional.properties file into the jspwiki.properties file.
   Remember to remove the current page and attachment providers.
 - Run the appropriate create_tables code to create the database tables (unless
   you are upgrading from a older version of JDBCProvider)
 
Note: the page provider and the attachment provider operate independently
      and you may use only one or both.

Example of changes to jspwiki.properties:

    jspwiki.pageProvider = com.forthgo.jspwiki.jdbcprovider.JDBCPageProvider
    jspwiki.attachmentProvider = com.forthgo.jspwiki.jdbcprovider.JDBCAttachmentProvider
    jspwiki.jdbcprovider.configuration=jdbcprovider.properties


######MIGRATING

If you have an old provider in your JSPWiki you can migrate your repository to
use JDBCProvider. 

Note: Both the WIKI_PAGE and WIKI_ATT table must be empty (truncated).

* Make a backup of your current JSPWiki page and attachment repository to a 
  temp directory

* Copy jspwiki.properties in your temp direcory

* Edit the just copied version of jspwiki.properties so that you have


    jspwiki.pageProvider =VersioningFileProvider (your old page provider)
    jspwiki.fileSystemProvider.pageDir =/data/wiki/pages (the folder with your wiki pages)
    jspwiki.attachmentProvider = BasicAttachmentProvider (your old attachment provider)
    jspwiki.basicAttachmentProvider.storageDir = /data/wiki/attachments (the folder with your attachments)

* Edit the jdbcprovider.properties:

```
    ## Migrate from another page repository. If you define this, both pages 
    ## and attachments will be migrated, if you have set your attachment provider
    ## to JDBCAttachmentProvider. Please provide a full path the the other
    ## provider.
    migrateFromConfiguration = /data/wiki/oldwiki.properties
```

* When done, comment out the above line.


######UPGRADE

Preferably make a copy of your tables / database before proceding.

The new version of the page provider does not use the WIKI_PAGE_VERSIONS any
longer, but all page versions are stored here. Thus, we need to move the pages 
from WIKI_PAGE_VERSIONS to WIKI_PAGE, but first the tables need to be modified. 
It is important to understand that the latest version of a page in the old
version is stored in both WIKI_PAGE and WIKI_PAGE_VERSIONS


On Mysql do this:
```
INSERT INTO WIKI_PAGE (NAME, VERSION, CHANGE_TIME, CHANGE_BY, CONTENT)
  SELECT VERSION_NAME, VERSION_NUM, VERSION_MODIFIED, VERSION_MODIFIED_B, VERSION_TEXT
  FROM <your_old_db>.WIKI_PAGE_VERSIONS;

INSERT INTO WIKI_ATT (PAGENAME, FILENAME, VERSION, CHANGE_TIME, CHANGE_BY, DATA, LENGTH)
  SELECT ATT_PAGENAME, ATT_FILENAME, ATT_VERSION, ATT_MODIFIED, ATT_MODIFIED_BY, ATT_DATA, LENGTH(ATT_DATA)
  FROM <your_old_db>.WIKI_ATT;
```

On Sybase do this:

* Remove the foreign key restraint on WIKI_PAGE_VERSIONS :
```
	ALTER TABLE dbo.WIKI_PAGE_VERSIONS DROP CONSTRAINT FK_WIKI_VERSIONS_WIKI_PAGE
	go
```

* Remove the primary key on WIKI_PAGE and add a new one:
```
    ALTER TABLE WIKI_PAGE DROP CONSTRAINT PK_WIKI_PAGE
    go
    ALTER TABLE dbo.WIKI_PAGE ADD CONSTRAINT PK_WIKI_PAGE
        PRIMARY KEY NONCLUSTERED (PAGE_NAME,PAGE_VERSION)
    go
```
* Remove all pages from WIKI_PAGE:
```
	TRUNCATE TABLE dbo.WIKI_PAGE
	go
```
* Move all pages from WIKI_PAGE_VERSIONS to WIKI_PAGE:
```
	INSERT WIKI_PAGE SELECT * FROM WIKI_PAGE_VERSIONS
```
* Finally remove WIKI_PAGE_VERSIONS
```
	DROP TABLE dbo.WIKI_PAGE_VERSIONS
	go
```

######LICENSE

The JDBCProvider was origially released under the Lesser GNU Public License (LGPL)
  Please see license/lgpl.txt for licensing details

The C3P0 components used in this project are released under the Lesser GNU Public License (LGPL)
  Please see license/lgpl.txt for licensing details
  C3P0 home: http://www.mchange.com/projects/c3p0
  
This project contains software from the Apache Software Foundation (the commons-DBCP and commons-Pool packages)

	Please see license/APACHE-LICENSE-2.0.txt for licensing details.
	commons-DBCP home: http://jakarta.apache.org/commons/dbcp/
	commons-Pool home: http://jakarta.apache.org/commons/pool/

######ORIGINAL TEAM MEMBERS
```
Xan Gregg
Soeren Berg Glasius
Mikkel Troest
Milt Taylor
```
######CURRENT MAINTAINER
David Emerson
