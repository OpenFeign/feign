#!/usr/bin/env zsh

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

CHECK_CHAR='\U2713'
CROSS_CHAR='\U2717'

function print_result() {
  version=$1
  result=$2

  if [[ $result == 0 ]];
  then
    mark=$CHECK_CHAR
    color=$GREEN
  else
    mark=$CROSS_CHAR
    color=$RED
  fi

  echo "\t${color}${version} ${mark}${NC}"
}

declare -A vertx_versions
vertx_versions=( [v50x]="5.0.x" )
v50x=( "5.0.0.CR4" )

for version in ${(k)vertx_versions}; do
  echo "Tests with Vertx ${vertx_versions[${version}]}:"

  for vertx_version in ${(P)version}; do
    printf "\tRun tests with Vertx %s...\n" "${vertx_version}"
    mvn clean compile test -Dvertx.version="$vertx_version" &> /dev/null
    print_result "$vertx_version" $?
  done
done
