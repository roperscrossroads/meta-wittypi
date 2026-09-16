SUMMARY = "Witty Pi 4 power controller — driver, daemon and CLI"
DESCRIPTION = "Signals SYS_UP to the Witty Pi 4's ATtiny841, waits for its \
shutdown request on GPIO-4, runs a BOUNDED pre-shutdown gate and powers off. \
Replaces the vendor's Raspberry Pi OS installer, which cannot run on a \
read-only rootfs."

require wittypi.inc

inherit systemd

SYSTEMD_SERVICE:${PN} = "wittypi-clock.service wittypi.service wittypi-configure.service wittypi-rtc-save.path"
SYSTEMD_AUTO_ENABLE = "enable"

# util-linux-flock: the lock is flock(1) inside wittypi-lib.sh's wp_lock now,
# not a wrapper on ExecStart — the dependency stays, the wrappers are gone.
RDEPENDS:${PN} = "i2c-tools libgpiod-tools util-linux-flock"

# The lock contract (wittypi.inc): configure stands down while a power-off is
# under way; rtc-save is the one unit that must carry no halt-status
# condition, because its .path would re-trigger it in a loop; both write,
# so both deliver TERM to the tool alone.
WITTYPI_STANDDOWN_UNITS = "wittypi-configure.service"
WITTYPI_NO_CONDITION_UNITS = "wittypi-rtc-save.service"
WITTYPI_KILLMODE_MIXED_UNITS = "wittypi-rtc-save.service wittypi-configure.service"

WITTYPI_BOARDS_DIRS ?= "${S}/boards ${UNPACKDIR}/boards"

do_install() {
    install -d ${D}${libexecdir}/site
    install -m 0644 ${S}/wittypi-lib.sh          ${D}${libexecdir}/site/wittypi-lib.sh
    install -m 0755 ${S}/wittypi-daemon          ${D}${libexecdir}/site/wittypi-daemon
    install -m 0755 ${S}/wittypi-before-shutdown ${D}${libexecdir}/site/wittypi-before-shutdown

    install -d ${D}${bindir}
    install -m 0755 ${S}/wittypi ${D}${bindir}/wittypi

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/systemd/wittypi.service           ${D}${systemd_system_unitdir}/wittypi.service
    install -m 0644 ${S}/systemd/wittypi-configure.service ${D}${systemd_system_unitdir}/wittypi-configure.service

    install -m 0644 ${S}/systemd/wittypi-clock.service    ${D}${systemd_system_unitdir}/wittypi-clock.service
    install -m 0644 ${S}/systemd/wittypi-rtc-save.service ${D}${systemd_system_unitdir}/wittypi-rtc-save.service
    install -m 0644 ${S}/systemd/wittypi-rtc-save.path    ${D}${systemd_system_unitdir}/wittypi-rtc-save.path

    install -d ${D}${nonarch_libdir}/site/wittypi-boards
    for wp_d in ${WITTYPI_BOARDS_DIRS}; do
        for wp_b in "$wp_d"/*.env; do
            [ -e "$wp_b" ] || continue
            install -m 0644 "$wp_b" ${D}${nonarch_libdir}/site/wittypi-boards/
        done
    done
}

python do_check_units_enabled() {
    import os, re
    unitdir = d.expand('${D}${systemd_system_unitdir}')
    listed = set(d.getVar('SYSTEMD_SERVICE:%s' % d.getVar('PN')).split())
    present = {f for f in os.listdir(unitdir)
               if f.endswith('.service') or f.endswith('.path')}

    triggered = set()
    for f in present:
        if not f.endswith('.path') or f not in listed:
            continue
        with open(os.path.join(unitdir, f)) as fh:
            m = re.search(r'^\s*Unit\s*=\s*(\S+)\s*$', fh.read(), re.M)
        if m:
            triggered.add(m.group(1))
        else:
            bb.fatal("wittypi: %s is enabled but names no Unit= — it would "
                     "watch a path and start nothing." % f)

    unaccounted = present - listed - triggered
    missing = listed - present
    if unaccounted or missing:
        bb.fatal("wittypi: SYSTEMD_SERVICE and the installed units disagree.\n"
                 "  installed but NEITHER enabled nor .path-triggered: %s\n"
                 "  enabled but NOT installed: %s\n"
                 "A unit that ships disabled looks present in the image and "
                 "never runs, which on this package means the rail is never cut "
                 "or the clock is never set."
                 % (sorted(unaccounted) or 'none',
                    sorted(missing) or 'none'))
}
addtask check_units_enabled after do_install before do_package

python do_check_sysup_pulse() {
    import re
    daemon = d.expand('${D}${libexecdir}/site/wittypi-daemon')
    with open(daemon) as f:
        body = ''.join(l for l in f if not l.lstrip().startswith('#'))
    calls = re.findall(r'\bgpioset\b', body)
    if len(calls) != 1:
        bb.fatal("wittypi: expected exactly ONE gpioset invocation in "
                 "wittypi-daemon, found %d. The SYS_UP pulse train must be a "
                 "single invocation: libgpiod releases the line when the "
                 "process holding it exits, so split calls drop GPIO-17 between "
                 "pulses and emit a different waveform than the one intended."
                 % len(calls))
    if not re.search(r'(^|\s)(-t\s|--toggle)', body, re.M):
        bb.fatal("wittypi: the gpioset call in wittypi-daemon has no --toggle. "
                 "A single set cannot produce the 1-0-1-0 SYS_UP train.")
}
addtask check_sysup_pulse after do_install before do_package

FILES:${PN} += " \
    ${libexecdir}/site \
    ${bindir}/wittypi \
    ${nonarch_libdir}/site/wittypi-boards \
    ${systemd_system_unitdir}/wittypi.service \
    ${systemd_system_unitdir}/wittypi-configure.service \
    ${systemd_system_unitdir}/wittypi-clock.service \
    ${systemd_system_unitdir}/wittypi-rtc-save.service \
    ${systemd_system_unitdir}/wittypi-rtc-save.path \
"
