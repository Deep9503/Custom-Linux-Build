#!/bin/sh

set -e

. ../../common.sh

echo "Preparing the Minimal Linux utilities folder. This may take a while."
rm -rf $WORK_DIR/overlay/$BUNDLE_NAME
mkdir -p $WORK_DIR/overlay/$BUNDLE_NAME/sbin

echo "Miminal Linux utilities folder has been prepared."

cd $SRC_DIR
