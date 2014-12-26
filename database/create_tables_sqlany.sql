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

CREATE TABLE "DBA"."WIKI_PAGE"
(
    "NAME"              varchar(100) NOT NULL,
    "VERSION"           integer NOT NULL,
    "CHANGE_TIME"       datetime NULL,
    "CHANGE_BY"         varchar(50) NULL,
    "CHANGE_NOTE"       varchar(100) NULL,
    "CONTENT"           text NULL,
    PRIMARY KEY         ("NAME", "VERSION")
)
go
CREATE TABLE "DBA"."WIKI_ATT"
(
    "PAGENAME"          varchar(100) NOT NULL,
    "FILENAME"          varchar(100) NOT NULL,
    "VERSION"           integer NOT NULL,
    "CHANGE_TIME"       datetime NULL,
    "CHANGE_BY"         varchar(50) NULL,
    "CHANGE_NOTE"       varchar(100) NULL,
    "DATA"              image NULL,
    "LENGTH"            integer NULL,
    PRIMARY KEY         ("PAGENAME", "FILENAME", "VERSION")
)
go
commit work
go

-------------------------------------------------
--   Add indexes
-------------------------------------------------

CREATE INDEX "WIKI_PAGE_CHANGE_TIME_IX" ON "DBA"."WIKI_PAGE"
(
    "CHANGE_TIME" ASC
)
go
commit work
go

CREATE INDEX "WIKI_ATT_CHANGE_TIME_IX" ON "DBA"."WIKI_ATT"
(
    "CHANGE_TIME" ASC
)
go
commit work
go
