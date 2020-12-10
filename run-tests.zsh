#!/usr/bin/env zsh

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

CHECK_CHAR='\U2713'
CROSS_CHAR='\U2717'

declare -A versions
versions=( [v35x]="3.5.x" [v36x]="3.6.x" [v37x]="3.7.x" [v38x]="3.8.x" [v39x]="3.9.x" )

v35x=( "3.5.1" "3.5.3" "3.5.4" )
v36x=( "3.6.0" "3.6.1" "3.6.2" "3.6.3" )
v37x=( "3.7.0" "3.7.1" )
v38x=( "3.8.0" "3.8.1" "3.8.2" "3.8.3" "3.8.4" "3.8.5" )
v39x=( "3.9.0" "3.9.1" "3.9.2" "3.9.3" "3.9.4" )

for version in ${(k)versions}; do
  echo "Tests ${versions[${version}]}:"

  for vertx_version in ${(P)version}; do
    echo "\tRun tests with Vertx ${vertx_version}..."

    mvn clean compile test -Dvertx.version="$vertx_version" &> /dev/null

    if [[ $? == 0 ]];
    then
      mark=$CHECK_CHAR
      color=$GREEN
    else
      mark=$CROSS_CHAR
      color=$RED
    fi

    echo "\t${color}${vertx_version} ${mark}${NC}"
  done
done

