#!/bin/bash

# Usage: <evaluation_database_name>

# print content of tables which are filled by the evaluation system
# this can be used to make a backup
# script was used for Ubuntu 15.04, but should also run on other *nix systems

db=$1
if [ "$db" == "" ];
then
	db="extern_yago3"
fi

su postgres -c "pg_dump -d $db -t evaluation"
su postgres -c "pg_dump -d $db -t evaluation_settings"

