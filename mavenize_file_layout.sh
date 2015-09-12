#!/bin/bash

set -eu

# mkdir -p compiler ; git mv pom-main.xml compiler/pom.xml

mkdir -p compiler/src/{main,test}
[[ -d test ]] && mv test compiler/src/test/java
[[ -d gen ]] && mv gen compiler/src/main

cd src

function mv_rel_file() {
  local rel_file=$1
  local to=$2
  mkdir -p $to/`dirname $rel_file`
  mv $rel_file $to/$rel_file
}

for F in `find . -name '*.java'` ; do mv_rel_file $F ../compiler/src/main/java ; done
for F in `find . -name '*.proto'` ; do mv_rel_file $F ../compiler/src/main/proto ; done
for F in `find . -type f` ; do mv_rel_file $F ../compiler/src/main/proto ; done

# Revert with:
# git checkout src test gen ; rm -fR compiler/{src,target}