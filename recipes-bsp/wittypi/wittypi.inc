
WITTYPI_GIT_URL ?= "git://github.com/roperscrossroads/wittypi.git"
WITTYPI_GIT_BRANCH ?= "main"

SRC_URI = "${WITTYPI_GIT_URL};protocol=https;branch=${WITTYPI_GIT_BRANCH}"

SRCREV = "83f06ab9892d950de7fea416e377954089f8f3af"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

WITTYPI_COMPATIBLE_MACHINE ?= "^(raspberrypi0|raspberrypi0-wifi|raspberrypi0-2w|raspberrypi0-2w-64)$"
COMPATIBLE_MACHINE = "${WITTYPI_COMPATIBLE_MACHINE}"

# ── The lock contract, checked on the installed units ─────────────────────
# Every script takes the I2C lock itself (wittypi-lib.sh's wp_lock), so no
# unit may wrap its ExecStart in flock: a wrapper plus the in-script lock
# waits on itself, and the boot hangs at that unit's start timeout. A lock
# timeout is exit 75 and may never be a success status — a run that skipped
# its work must show. The units that must stand down while a power-off is
# under way carry ExecCondition=/usr/bin/wittypi halt-status; rtc-save must
# NOT, because its .path would re-trigger a conditioned unit in a loop (the
# tool stands down itself there, exit 69). The units that WRITE deliver TERM
# to the tool alone (KillMode=mixed): under the default control-group mode a
# stop also TERMs the tool's i2cget children, and a landed write reads back
# empty and is reported as failed (seen on the bench, 2026-09-16). Each
# recipe names its sets.
WITTYPI_STANDDOWN_UNITS ?= ""
WITTYPI_NO_CONDITION_UNITS ?= ""
WITTYPI_KILLMODE_MIXED_UNITS ?= ""

python do_check_lock_contract() {
    import os, re
    unitdir = d.expand('${D}${systemd_system_unitdir}')
    pn = d.getVar('PN')
    standdown = set((d.getVar('WITTYPI_STANDDOWN_UNITS') or '').split())
    nocond = set((d.getVar('WITTYPI_NO_CONDITION_UNITS') or '').split())
    mixed = set((d.getVar('WITTYPI_KILLMODE_MIXED_UNITS') or '').split())
    problems = []
    # Three recipes share this file; wittypi-timing installs no units at all,
    # so a missing unit dir is an empty set, not an error.
    installed = sorted(os.listdir(unitdir)) if os.path.isdir(unitdir) else []
    for f in installed:
        if not f.endswith('.service'):
            continue
        with open(os.path.join(unitdir, f)) as fh:
            lines = [l for l in fh.read().splitlines() if not l.lstrip().startswith('#')]
        body = '\n'.join(lines)
        for m in re.finditer(r'^\s*Exec(Start|StartPre|StartPost|Stop|Condition)\s*=\s*(.*)$', body, re.M):
            if re.search(r'(^|/|\s)flock(\s|$)', m.group(2)):
                problems.append('%s: %s wraps flock — the script locks itself; both would wait on each other'
                                % (f, m.group(0).strip()))
        for m in re.finditer(r'^\s*SuccessExitStatus\s*=\s*(.*)$', body, re.M):
            if '75' in m.group(1).split():
                problems.append('%s: SuccessExitStatus lists 75 — a lock timeout would read as success' % f)
        has_cond = re.search(r'^\s*ExecCondition\s*=\s*/usr/bin/wittypi halt-status\s*$', body, re.M) is not None
        if f in standdown and not has_cond:
            problems.append('%s: no ExecCondition=/usr/bin/wittypi halt-status — it would run into a shutdown' % f)
        if f in nocond and has_cond:
            problems.append('%s: has an ExecCondition — its .path would re-trigger it in a loop' % f)
        km = [m.group(1).strip() for m in re.finditer(r'^\s*KillMode\s*=\s*(.*)$', body, re.M)]
        if f in mixed and (not km or km[-1] != 'mixed'):
            problems.append('%s: KillMode is %s, not mixed — a stop would TERM the tool\'s i2cget children mid-read-back' % (f, km[-1] if km else 'the control-group default'))
    for f in (standdown | nocond | mixed) - set(installed):
        problems.append('%s: named in the lock contract but not installed' % f)
    if problems:
        bb.fatal("%s: the lock contract is broken:\n  %s" % (pn, '\n  '.join(problems)))
}
addtask check_lock_contract after do_install before do_package
