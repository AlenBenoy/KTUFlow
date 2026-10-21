package com.ktu.scholar.core.service;
 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
 
/**
 * FocusBlockerService actively blocks designated applications and websites
 * during focus sessions.
 *
 * Fixes applied (see inline comments for details):
 *  1. Website blocking failed completely (and silently) on most machines because
 *     writing to the hosts file needs administrator rights and the failure was
 *     only ever written to a log file the user never sees. We now check for
 *     write access up front and expose a human-readable warning the UI can show.
 *  2. App blocking used a "does the command line contain this text" match, which
 *     both under-blocks (many processes' command lines are inaccessible without
 *     elevation) and over-blocks (e.g. blocking "photos" would also force-kill
 *     "Photoshop.exe", and a generic name could even match the app's own JVM
 *     process). Matching is now done on the exact process image name, the
 *     current process is always excluded, and a denylist protects core OS
 *     processes from ever being force-killed.
 *  3. The website "window closing" fallback ran a PowerShell process for every
 *     blocked site every 1.5 seconds and would force-close *any* window whose
 *     title merely contained the domain text, regardless of what application
 *     owned it. It's now scoped to known browser processes only and runs on a
 *     slower interval so it doesn't spam PowerShell processes.
 */
public class FocusBlockerService {
 
    private static final Logger log = LoggerFactory.getLogger(FocusBlockerService.class);
    private static final String HOSTS_START_MARKER = "# KTU-SCHOLAR-FOCUS-START";
    private static final String HOSTS_END_MARKER   = "# KTU-SCHOLAR-FOCUS-END";
 
    // Fix #2: never force-kill these regardless of what the user typed into the
    // blocked-apps list. Prevents a typo/generic name from destabilizing the OS.
    private static final Set<String> PROTECTED_PROCESSES = new HashSet<>(Arrays.asList(
            "system", "system idle process", "registry", "smss", "csrss", "wininit",
            "winlogon", "services", "lsass", "svchost", "explorer", "dwm",
            "fontdrvhost", "taskhostw", "sihost", "ctfmon", "conhost",
            "runtimebroker", "searchindexer", "searchapp", "searchui",
            "shellexperiencehost", "startmenuexperiencehost", "spoolsv",
            "wmiprvse", "audiodg", "userinit", "logonui", "taskmgr",
            "java", "javaw"
    ));
 
    private static final Set<String> BROWSER_PROCESSES = new HashSet<>(Arrays.asList(
            "chrome", "msedge", "firefox", "opera", "opera_gx", "brave"
    ));
 
    private static final FocusBlockerService INSTANCE = new FocusBlockerService();
 
    private ScheduledExecutorService scheduler;
    private boolean isBlockingActive = false;
 
    private final List<String> activeBlockedApps = new CopyOnWriteArrayList<>();
    private final List<String> activeBlockedWebsites = new CopyOnWriteArrayList<>();
 
    // Fix #1: surfaced to the UI so the user knows *why* website blocking isn't working.
    private volatile String lastWebsiteBlockingWarning;
 
    private int tickCount = 0;
 
    private FocusBlockerService() {
        // Register shutdown hook to clean up hosts file when app exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                stopBlocking();
            } catch (Exception ignored) {}
        }));
    }
 
    public static FocusBlockerService getInstance() {
        return INSTANCE;
    }
 
    public synchronized void startBlocking(List<String> apps, List<String> websites) {
        stopBlocking(); // Clean up any existing state
 
        activeBlockedApps.clear();
        if (apps != null) {
            for (String a : apps) {
                String clean = a.trim();
                if (!clean.isEmpty()) activeBlockedApps.add(clean.toLowerCase());
            }
        }
 
        activeBlockedWebsites.clear();
        if (websites != null) {
            for (String w : websites) {
                String clean = w.trim().toLowerCase()
                        .replace("https://", "")
                        .replace("http://", "")
                        .replace("www.", "");
                int slashIdx = clean.indexOf('/');
                if (slashIdx > 0) clean = clean.substring(0, slashIdx);
                if (!clean.isEmpty()) activeBlockedWebsites.add(clean);
            }
        }
 
        isBlockingActive = true;
        tickCount = 0;
 
        // 1. Block websites via Hosts File
        applyHostsFileBlocking(activeBlockedWebsites);
 
        // 2. Start periodic background process checker for Apps & Websites
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FocusBlockerWorker");
            t.setDaemon(true);
            return t;
        });
 
        scheduler.scheduleAtFixedRate(this::enforceBlocking, 0, 1500, TimeUnit.MILLISECONDS);
        log.info("Focus Mode active: blocking {} apps and {} websites",
                activeBlockedApps.size(), activeBlockedWebsites.size());
    }
 
    public synchronized void stopBlocking() {
        if (!isBlockingActive) return;
        isBlockingActive = false;
 
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
 
        // Restore hosts file
        restoreHostsFile();
        log.info("Focus Mode deactivated: blocking stopped and hosts file restored");
    }
 
    public boolean isBlockingActive() {
        return isBlockingActive;
    }
 
    /**
     * Fix #1: lets the FocusPanel show a warning (e.g. "run as administrator")
     * instead of leaving the user to wonder why sites still load.
     */
    public String getLastWebsiteBlockingWarning() {
        return lastWebsiteBlockingWarning;
    }
 
    // ── Process & Window Enforcement ──────────────────────────────────────
 
    private void enforceBlocking() {
        if (!isBlockingActive) return;
        tickCount++;
 
        // Block Apps - every tick (1.5s)
        for (String target : activeBlockedApps) {
            killMatchingProcesses(target);
        }
 
        // Secondary website blocker via browser window title inspection.
        // This is a best-effort backstop for already-open tabs; the hosts file
        // is the primary mechanism. Runs every ~4th tick (~6s) since spawning
        // PowerShell is comparatively expensive and doesn't need to be instant.
        if (!activeBlockedWebsites.isEmpty() && tickCount % 4 == 0) {
            closeMatchingBrowserWindows(activeBlockedWebsites);
        }
    }
 
    private void killMatchingProcesses(String target) {
        String baseName = target.endsWith(".exe") ? target.substring(0, target.length() - 4) : target;
        baseName = baseName.toLowerCase();
 
        // Fix #2: never touch protected system processes or ourselves.
        if (PROTECTED_PROCESSES.contains(baseName)) {
            log.warn("Refusing to block protected process '{}': not allowed for safety reasons", baseName);
            return;
        }
 
        String procName = baseName + ".exe";
        long ownPid = ProcessHandle.current().pid();
        String finalBaseName = baseName;
 
        // 1. Destroy via Java ProcessHandle - exact image-name match only, not a
        //    substring match, so "photos" can no longer collateral-kill "Photoshop".
        ProcessHandle.allProcesses().forEach(ph -> {
            try {
                if (ph.pid() == ownPid) return; // never kill ourselves
                if (!ph.isAlive()) return;
                String cmd = ph.info().command().orElse("");
                if (cmd.isEmpty()) return;
                String imageName = imageNameOf(cmd);
                if (imageName.equalsIgnoreCase(finalBaseName) && !PROTECTED_PROCESSES.contains(imageName.toLowerCase())) {
                    ph.destroyForcibly();
                }
            } catch (Exception ignored) {}
        });
 
        // 2. Force kill via OS taskkill on Windows. This is the more reliable path
        //    since ProcessHandle.info().command() is frequently unavailable
        //    (returns empty) on Windows without elevated permissions.
        if (isWindows()) {
            try {
                new ProcessBuilder("taskkill", "/F", "/IM", procName)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
            } catch (Exception ignored) {}
        }
    }
 
    /** Extracts just the executable's file name (no path, no extension) from a full command path. */
    private String imageNameOf(String command) {
        String name = command.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        if (name.toLowerCase().endsWith(".exe")) name = name.substring(0, name.length() - 4);
        return name;
    }
 
    private void closeMatchingBrowserWindows(List<String> domains) {
        if (!isWindows()) return;
 
        // Fix #3: scope to known browser processes so we never force-close an
        // unrelated app just because its window title happens to contain the text.
        StringBuilder domainPattern = new StringBuilder();
        for (String d : domains) {
            if (d == null || d.isEmpty()) continue;
            if (domainPattern.length() > 0) domainPattern.append("|");
            // Escape for .NET/PowerShell regex (not Java's \Q..\E, which
            // PowerShell would treat as literal garbage characters).
            domainPattern.append(d.replace(".", "\\."));
        }
        if (domainPattern.length() == 0) return;
 
        String browserList = String.join(",", BROWSER_PROCESSES);
        try {
            String script = String.format(
                "$names = '%s'.Split(','); " +
                "Get-Process | Where-Object { $names -contains $_.ProcessName.ToLower() -and $_.MainWindowTitle -match '%s' } | Stop-Process -Force",
                browserList,
                domainPattern.toString().replace("'", "''")
            );
            new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (Exception ignored) {}
    }
 
    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
 
    // ── Hosts File Website Blocking ────────────────────────────────────────
 
    private Path getHostsPath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String windir = System.getenv("WINDIR");
            if (windir == null) windir = "C:\\Windows";
            return Paths.get(windir, "System32", "drivers", "etc", "hosts");
        } else {
            return Paths.get("/etc/hosts");
        }
    }
 
    private void applyHostsFileBlocking(List<String> domains) {
        lastWebsiteBlockingWarning = null;
        if (domains.isEmpty()) return;
 
        Path hostsFile = getHostsPath();
        if (!Files.exists(hostsFile)) {
            lastWebsiteBlockingWarning = "Could not find the system hosts file; website blocking is disabled for this session.";
            log.warn(lastWebsiteBlockingWarning);
            return;
        }
 
        // Fix #1: check permissions up front and surface a clear reason instead
        // of silently doing nothing when the app isn't running elevated.
        if (!Files.isWritable(hostsFile)) {
            lastWebsiteBlockingWarning = "Website blocking needs administrator rights to edit the system hosts file. "
                    + "Right-click the app and choose \"Run as administrator\", then start the focus session again.";
            log.warn("Hosts file is not writable without elevated permissions; website blocking will rely on window title monitoring only");
            return;
        }
 
        try {
            List<String> lines = Files.readAllLines(hostsFile, StandardCharsets.UTF_8);
            List<String> cleanedLines = removeFocusBlockerLines(lines);
 
            List<String> newLines = new ArrayList<>(cleanedLines);
            newLines.add(HOSTS_START_MARKER);
            for (String domain : domains) {
                newLines.add("127.0.0.1 " + domain);
                newLines.add("127.0.0.1 www." + domain);
                newLines.add("::1 " + domain);
                newLines.add("::1 www." + domain);
            }
            newLines.add(HOSTS_END_MARKER);
 
            Files.write(hostsFile, newLines, StandardCharsets.UTF_8);
            flushDnsCache();
        } catch (Exception e) {
            lastWebsiteBlockingWarning = "Could not write to the hosts file (" + e.getMessage()
                    + "); website blocking will rely on window title monitoring only.";
            log.warn("Could not write to hosts file ({}); website blocking will rely on window title monitoring", e.getMessage());
        }
    }
 
    private void restoreHostsFile() {
        Path hostsFile = getHostsPath();
        if (!Files.exists(hostsFile)) return;
        if (!Files.isWritable(hostsFile)) return; // nothing we can do without permission
 
        try {
            List<String> lines = Files.readAllLines(hostsFile, StandardCharsets.UTF_8);
            List<String> cleanedLines = removeFocusBlockerLines(lines);
            if (cleanedLines.size() != lines.size()) {
                Files.write(hostsFile, cleanedLines, StandardCharsets.UTF_8);
                flushDnsCache();
            }
        } catch (Exception e) {
            log.warn("Could not restore hosts file: {}", e.getMessage());
        }
    }
 
    private List<String> removeFocusBlockerLines(List<String> lines) {
        List<String> result = new ArrayList<>();
        boolean insideBlock = false;
        for (String line : lines) {
            if (line.trim().equalsIgnoreCase(HOSTS_START_MARKER)) {
                insideBlock = true;
                continue;
            }
            if (line.trim().equalsIgnoreCase(HOSTS_END_MARKER)) {
                insideBlock = false;
                continue;
            }
            if (!insideBlock) {
                result.add(line);
            }
        }
        return result;
    }
 
    private void flushDnsCache() {
        if (isWindows()) {
            try {
                new ProcessBuilder("ipconfig", "/flushdns")
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
            } catch (Exception ignored) {}
        }
        // Note: this only clears the OS-level DNS cache. Chrome/Edge/Firefox keep
        // their own internal DNS cache independent of the OS, so a domain visited
        // moments earlier in the same browser session may still load until the
        // browser is restarted or its own cache expires. There's no supported way
        // to flush that from outside the browser; the UI should hint that
        // restarting the browser makes blocking take effect immediately.
    }
}
 