SUMMARY = "Operational tooling layered on the Witty Pi power controller"
DESCRIPTION = "The supervisor (wittypi-watch), the audit tool, the shutdown \
gate (wittypi-shutdown-gate plus its profile.d layer) and the on-node \
scheduler (wittypi-schedule — a boot oneshot, a clean no-op until its config \
file opts a node in). Operational and diagnostic tooling on TOP of the \
hardware-protocol package, not the protocol itself."

require recipes-bsp/wittypi/wittypi.inc

inherit systemd

SYSTEMD_SERVICE:${PN} = "wittypi-watch.timer wittypi-schedule.service wittypi-schedule.path"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = "wittypi"

do_install() {
    install -d ${D}${libexecdir}/site
    install -m 0755 ${S}/wittypi-watch ${D}${libexecdir}/site/wittypi-watch

    install -d ${D}${bindir}
    install -m 0755 ${S}/wittypi-audit ${D}${bindir}/wittypi-audit

    install -m 0755 ${S}/wittypi-shutdown-gate ${D}${libexecdir}/site/wittypi-shutdown-gate

    install -d ${D}${sysconfdir}/profile.d
    install -m 0644 ${S}/wittypi-shutdown-gate.sh ${D}${sysconfdir}/profile.d/wittypi-shutdown-gate.sh

    install -m 0755 ${S}/wittypi-schedule ${D}${libexecdir}/site/wittypi-schedule

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/systemd/wittypi-watch.service    ${D}${systemd_system_unitdir}/wittypi-watch.service
    install -m 0644 ${S}/systemd/wittypi-watch.timer      ${D}${systemd_system_unitdir}/wittypi-watch.timer
    install -m 0644 ${S}/systemd/wittypi-schedule.service ${D}${systemd_system_unitdir}/wittypi-schedule.service

    install -m 0644 ${S}/systemd/wittypi-schedule.path      ${D}${systemd_system_unitdir}/wittypi-schedule.path
    install -m 0644 ${S}/systemd/wittypi-reschedule.service ${D}${systemd_system_unitdir}/wittypi-reschedule.service
}

python do_check_units_enabled() {
    import os, re
    unitdir = d.expand('${D}${systemd_system_unitdir}')
    listed = set(d.getVar('SYSTEMD_SERVICE:%s' % d.getVar('PN')).split())
    present = {f for f in os.listdir(unitdir)
               if f.endswith('.service') or f.endswith('.timer') or f.endswith('.path')}

    triggered = set()
    for f in present:
        if not (f.endswith('.timer') or f.endswith('.path')) or f not in listed:
            continue
        with open(os.path.join(unitdir, f)) as fh:
            m = re.search(r'^\s*Unit\s*=\s*(\S+)\s*$', fh.read(), re.M)
        if m:
            triggered.add(m.group(1))
        elif f.endswith('.timer'):
            triggered.add(f[:-len('.timer')] + '.service')
        else:
            bb.fatal("wittypi-ops: %s is enabled but names no Unit= — it "
                     "would watch a path and start nothing." % f)

    unaccounted = present - listed - triggered
    missing = listed - present
    if unaccounted or missing:
        bb.fatal("wittypi-ops: SYSTEMD_SERVICE and the installed units disagree.\n"
                 "  installed but NEITHER enabled nor timer/path-triggered: %s\n"
                 "  enabled but NOT installed: %s\n"
                 "A supervisor that ships disabled looks present in the image "
                 "and never checks anything, which is worse than absent — it "
                 "reads as coverage."
                 % (sorted(unaccounted) or 'none',
                    sorted(missing) or 'none'))
}
addtask check_units_enabled after do_install before do_package

FILES:${PN} = "\
    ${libexecdir}/site/wittypi-watch \
    ${bindir}/wittypi-audit \
    ${libexecdir}/site/wittypi-shutdown-gate \
    ${sysconfdir}/profile.d/wittypi-shutdown-gate.sh \
    ${libexecdir}/site/wittypi-schedule \
    ${systemd_system_unitdir}/wittypi-watch.service \
    ${systemd_system_unitdir}/wittypi-watch.timer \
    ${systemd_system_unitdir}/wittypi-schedule.service \
    ${systemd_system_unitdir}/wittypi-schedule.path \
    ${systemd_system_unitdir}/wittypi-reschedule.service \
"
