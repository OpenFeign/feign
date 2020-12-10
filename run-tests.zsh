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

feign_versions=( "9.0.0" "9.1.0" "9.2.0" "9.3.0" "9.3.1" "9.4.0" "9.5.0" "9.5.1" "9.6.0" "9.7.0" )

for feign_version in $feign_versions; do
  echo "Tests with Feign ${version}:"

  printf "\tRun tests with Feign %s...\n" "${feign_version}"
  mvn clean compile test -Dfeign.version="$feign_version" &> /dev/null
  print_result "$feign_version" $?
done

declare -A vertx_versions
vertx_versions=( [v35x]="3.5.x" [v36x]="3.6.x" [v37x]="3.7.x" [v38x]="3.8.x" [v39x]="3.9.x" )

v35x=( "3.5.1" "3.5.3" "3.5.4" )
v36x=( "3.6.0" "3.6.1" "3.6.2" "3.6.3" )
v37x=( "3.7.0" "3.7.1" )
v38x=( "3.8.0" "3.8.1" "3.8.2" "3.8.3" "3.8.4" "3.8.5" )
v39x=( "3.9.0" "3.9.1" "3.9.2" "3.9.3" "3.9.4" )

for version in ${(k)vertx_versions}; do
  echo "Tests with Vertx ${vertx_versions[${version}]}:"

  for vertx_version in ${(P)version}; do
    printf "\tRun tests with Vertx %s...\n" "${vertx_version}"
    mvn clean compile test -Dvertx.version="$vertx_version" &> /dev/null
    print_result "$vertx_version" $?
  done
done
