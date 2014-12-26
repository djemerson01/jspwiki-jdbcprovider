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

CREATE TABLE "WIKI_PAGE"
    (
         "NAME" character varying(100) NOT NULL,
         "VERSION" integer NOT NULL,
         "CHANGE_TIME" timestamp without time zone,
         "CHANGE_BY" character varying(50),
         "CHANGE_NOTE" character varying(100),
         "CONTENT" text
    );

ALTER TABLE ONLY "WIKI_PAGE"
    ADD CONSTRAINT "WIKI_PAGE_UNIQUE_KEY" UNIQUE ("NAME", "VERSION");

ALTER TABLE ONLY "WIKI_PAGE"
    ADD CONSTRAINT "WIKI_PAGE_PKEY" PRIMARY KEY ("NAME", "VERSION");
    
CREATE INDEX "WIKI_PAGE_CHANGE_TIME_IX" ON "WIKI_PAGE" USING btree ("CHANGE_TIME");


CREATE TABLE "WIKI_ATT"
    (
         "PAGENAME" character varying(100) NOT NULL,
         "FILENAME" character varying(100) NOT NULL,
         "VERSION" integer NOT NULL,
         "CHANGE_TIME" timestamp without time zone,
         "CHANGE_BY" character varying(50),
         "CHANGE_NOTE" character varying(100),
         "LENGTH" integer,
         "DATA" bytea
    );

ALTER TABLE ONLY "WIKI_ATT"
    ADD CONSTRAINT "WIKI_ATT_UNIQUE_KEY" UNIQUE ("PAGENAME", "FILENAME", "VERSION");

ALTER TABLE ONLY "WIKI_ATT"
    ADD CONSTRAINT "WIKI_ATT_PKEY" PRIMARY KEY ("PAGENAME", "FILENAME", "VERSION");
    
CREATE INDEX "WIKI_ATT_CHANGE_TIME_IX" ON "WIKI_ATT" USING btree ("CHANGE_TIME");
