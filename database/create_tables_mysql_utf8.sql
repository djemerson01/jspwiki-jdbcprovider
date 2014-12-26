-- 
-- JDBCProvider - a RDBMS backed page- and attachment provider for
-- JSPWiki.
-- 
-- Copyright (C) 2006-2007 The JDBCProvider development team.
-- 
-- The JDBCProvider developer team members are:
--   Xan Gregg
--   Soeren Berg Glasius
--   Mikkel Troest
--   Milt Taylor
-- 
-- This program is free software; you can redistribute it and/or modify
-- it under the terms of the GNU Lesser General Public License as published by
-- the Free Software Foundation; either version 2.1 of the License, or
-- (at your option) any later version.
-- 
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU Lesser General Public License for more details.
-- 
-- You should have received a copy of the GNU Lesser General Public License
-- along with this program; if not, write to the Free Software
-- Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
--

CREATE TABLE WIKI_PAGE
    (
        NAME               VARCHAR (100)    CHARACTER SET utf8 COLLATE utf8_bin NOT NULL,
        VERSION            INTEGER          NOT NULL,
        CHANGE_TIME        DATETIME,
        CHANGE_BY          VARCHAR (50)     CHARACTER SET utf8 COLLATE utf8_bin NOT NULL,
        CHANGE_NOTE        VARCHAR (100)    CHARACTER SET utf8 COLLATE utf8_bin,
        CONTENT            MEDIUMTEXT       CHARACTER SET utf8 COLLATE utf8_bin NOT NULL,
        
        PRIMARY KEY        (NAME, VERSION),
        UNIQUE KEY         NAME             (NAME, VERSION),
        KEY                WIKI_PAGE_CHANGE_TIME_IX   (CHANGE_TIME)
    );

CREATE TABLE WIKI_ATT
    (
        PAGENAME           VARCHAR (100)    CHARACTER SET utf8 COLLATE utf8_bin NOT NULL,
        FILENAME           VARCHAR (100)    CHARACTER SET utf8 COLLATE utf8_bin NOT NULL,
        VERSION            INTEGER          NOT NULL,
        CHANGE_TIME        DATETIME,
        CHANGE_BY          VARCHAR (50)     CHARACTER SET utf8 COLLATE utf8_bin NOT NULL,
        CHANGE_NOTE        VARCHAR (100)    CHARACTER SET utf8 COLLATE utf8_bin,
        DATA               MEDIUMBLOB,
        LENGTH             INTEGER,
        
        PRIMARY KEY        (PAGENAME,FILENAME,VERSION),
        UNIQUE KEY         PAGENAME         (PAGENAME,FILENAME,VERSION),
        KEY                WIKI_ATT_CHANGE_TIME_IX   (CHANGE_TIME)
    );
