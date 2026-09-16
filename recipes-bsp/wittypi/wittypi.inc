
WITTYPI_GIT_URL ?= "git://github.com/roperscrossroads/wittypi.git"
WITTYPI_GIT_BRANCH ?= "main"

SRC_URI = "${WITTYPI_GIT_URL};protocol=https;branch=${WITTYPI_GIT_BRANCH}"

SRCREV = "ed4049325bd2d11046450cb207f4948eb8ac60e3"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

WITTYPI_COMPATIBLE_MACHINE ?= "^(raspberrypi0|raspberrypi0-wifi|raspberrypi0-2w|raspberrypi0-2w-64)$"
COMPATIBLE_MACHINE = "${WITTYPI_COMPATIBLE_MACHINE}"
