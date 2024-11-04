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

feign_versions=( "12.5" "13.5" )

for feign_version in $feign_versions; do
  echo "Tests with Feign ${version}:"

  printf "\tRun tests with Feign %s...\n" "${feign_version}"
  mvn clean compile test -Dfeign.version="$feign_version" &> /dev/null
  print_result "$feign_version" $?
done

declare -A vertx_versions
vertx_versions=( [v40x]="4.0.x", [v41x]="4.1.x", [v42x]="4.2.x", [v43x]="4.3.x", [v44x]="4.4.x", [v45x]="4.5.x" )
v40x=( "4.0.2" )
v41x=( "4.1.8" )
v42x=( "4.2.7" )
v43x=( "4.3.2" )
v44x=( "4.4.9" )
v45x=( "4.5.10" )

for version in ${(k)vertx_versions}; do
  echo "Tests with Vertx ${vertx_versions[${version}]}:"

  for vertx_version in ${(P)version}; do
    printf "\tRun tests with Vertx %s...\n" "${vertx_version}"
    mvn clean compile test -Dvertx.version="$vertx_version" &> /dev/null
    print_result "$vertx_version" $?
  done
done
