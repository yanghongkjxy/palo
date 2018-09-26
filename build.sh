#!/usr/bin/env bash
# Copyright (c) 2017, Baidu.com, Inc. All Rights Reserved

# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

##############################################################
# This script is used to compile Palo.
# Usage:
#    sh build.sh        build both Backend and Frontend.
#    sh build.sh -clean clean previous output and build.
#
# You need to make sure all thirdparty libraries have been
# compiled and installed correctly.
##############################################################

set -eo pipefail
ROOT=`dirname "$0"`
ROOT=`cd "$ROOT"; pwd`
export PALO_HOME=$ROOT

PARALLEL=8

# Check java version
if [ -z $JAVA_HOME ]; then
    echo "Error: JAVA_HOME is not set, use thirdparty/installed/jdk1.8.0_131"
    export JAVA_HOME=${PALO_HOME}/thirdparty/installed/jdk1.8.0_131
fi

JAVA=${JAVA_HOME}/bin/java
JAVA_VER=$(${JAVA} -version 2>&1 | sed 's/.* version "\(.*\)\.\(.*\)\..*"/\1\2/; 1q' | cut -f1 -d " ")
if [ $JAVA_VER -lt 18 ]; then
    echo "Require JAVA with JDK version at least 1.8"
    exit 1
fi

ANT=ant
# Check ant
if ! ${ANT} -version; then
    echo "ant is not found, use thirdparty/installed/ant"
    ANT=${PALO_HOME}/thirdparty/installed/ant/bin/ant
fi

# check python
export PYTHON=python
if ! ${PYTHON} --version; then
    export PYTHON=python2.7
    if ! ${PYTHON} --version; then
        echo "python is not found"
        exit
    fi
fi

# Check args
usage() {
  echo "
Usage: $0 <options>
  Optional options:
     --be       build Backend
     --fe       build Frontend
     --clean    clean and build target
     
  Eg.
    $0                      build Backend and Frontend without clean
    $0 --be                 build Backend without clean
    $0 --fe --clean         clean and build Frontend
    $0 --fe --be --clean    clean and build both Frontend and Backend
  "
  exit 1
}

OPTS=$(getopt \
  -n $0 \
  -o '' \
  -l 'be' \
  -l 'fe' \
  -l 'clean' \
  -- "$@")

if [ $? != 0 ] ; then
    usage
fi

eval set -- "$OPTS"

BUILD_BE=
BUILD_FE=
CLEAN=
RUN_UT=
if [ $# == 1 ] ; then
    # defuat
    BUILD_BE=1
    BUILD_FE=1
    CLEAN=0
    RUN_UT=0
else
    BUILD_BE=0
    BUILD_FE=0
    CLEAN=0
    RUN_UT=0
    while true; do 
        case "$1" in
            --be) BUILD_BE=1 ; shift ;;
            --fe) BUILD_FE=1 ; shift ;;
            --clean) CLEAN=1 ; shift ;;
            --ut) RUN_UT=1   ; shift ;;
            --) shift ;  break ;;
            *) ehco "Internal error" ; exit 1 ;;
        esac
    done
fi

if [ ${CLEAN} -eq 1 -a ${BUILD_BE} -eq 0 -a ${BUILD_FE} -eq 0 ]; then
    echo "--clean can not be specified without --fe or --be"
    exit 1
fi

echo "Get params:
    BUILD_BE -- $BUILD_BE
    BUILD_FE -- $BUILD_FE
    CLEAN    -- $CLEAN
    RUN_UT   -- $RUN_UT
"

# Clean and build generated code
echo "Build generated code"
cd ${PALO_HOME}/gensrc
if [ ${CLEAN} -eq 1 ]; then
   make clean
fi 
make
cd ${PALO_HOME}

# Clean and build Backend
if [ ${BUILD_BE} -eq 1 ] ; then
    echo "Build Backend"
    if [ ${CLEAN} -eq 1 ]; then
        rm ${PALO_HOME}/be/build/ -rf
        rm ${PALO_HOME}/be/output/ -rf
    fi
    mkdir -p ${PALO_HOME}/be/build/
    cd ${PALO_HOME}/be/build/
    cmake ../
    make -j${PARALLEL}
    make install
    cd ${PALO_HOME}
fi

# Build docs, should be built before Frontend
echo "Build docs"
cd ${PALO_HOME}/docs
if [ ${CLEAN} -eq 1 ]; then
    make clean
fi
make
cd ${PALO_HOME}

# Clean and build Frontend
if [ ${BUILD_FE} -eq 1 ] ; then
    echo "Build Frontend"
    cd ${PALO_HOME}/fe
    if [ ${CLEAN} -eq 1 ]; then
        ${ANT} clean
    fi
    ${ANT} install
    cd ${PALO_HOME}
fi

# Clean and prepare output dir
PALO_OUTPUT=${PALO_HOME}/output/
mkdir -p ${PALO_OUTPUT}

#Copy Frontend and Backend
if [ ${BUILD_FE} -eq 1 ]; then
    install -d ${PALO_OUTPUT}/fe/bin ${PALO_OUTPUT}/fe/conf \
               ${PALO_OUTPUT}/fe/webroot/ ${PALO_OUTPUT}/fe/lib/

    cp -r -p ${PALO_HOME}/fe/output/bin/* ${PALO_OUTPUT}/fe/bin/
    cp -r -p ${PALO_HOME}/fe/output/conf/* ${PALO_OUTPUT}/fe/conf/
    cp -r -p ${PALO_HOME}/fe/output/lib/* ${PALO_OUTPUT}/fe/lib/
    cp -r -p ${PALO_HOME}/fe/output/webroot/* ${PALO_OUTPUT}/fe/webroot/
fi
if [ ${BUILD_BE} -eq 1 ]; then
    install -d ${PALO_OUTPUT}/be/bin ${PALO_OUTPUT}/be/conf \
               ${PALO_OUTPUT}/be/lib/

    cp -r -p ${PALO_HOME}/be/output/bin/* ${PALO_OUTPUT}/be/bin/ 
    cp -r -p ${PALO_HOME}/be/output/conf/* ${PALO_OUTPUT}/be/conf/
    cp -r -p ${PALO_HOME}/be/output/lib/* ${PALO_OUTPUT}/be/lib/
fi

echo "***************************************"
echo "Successfully build Palo."
echo "***************************************"

exit 0
