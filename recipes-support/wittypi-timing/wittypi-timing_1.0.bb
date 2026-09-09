SUMMARY = "Timing-window inventory for a duty-cycled node"
DESCRIPTION = "Reads every timing constant from the source that uses it, \
prints the relationships between them, and names the hazards. Ships the \
measured and firmware terms as data so the node and the build tree read the \
same bytes."

require recipes-bsp/wittypi/wittypi.inc

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/timing-windows ${D}${bindir}/timing-windows

    install -d ${D}${nonarch_libdir}/site
    install -m 0644 ${S}/timing-terms ${D}${nonarch_libdir}/site/timing-terms
}

FILES:${PN} = "\
    ${bindir}/timing-windows \
    ${nonarch_libdir}/site/timing-terms \
"
