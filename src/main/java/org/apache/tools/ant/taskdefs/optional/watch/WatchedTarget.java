package org.apache.tools.ant.taskdefs.optional.watch;

import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.PatternSet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.*;

public class WatchedTarget {
    private List<FileSet> filesets;
    private Target target;

    private Map<WatchKey, Path> watchedPaths;

    private long lastRun = 0l;

    public long getLastRun() {
        return lastRun;
    }

    public void setLastRun(long lastRun) {
        this.lastRun = lastRun;
    }

    public void addFileset(FileSet fs) {
        if (filesets == null) filesets = new ArrayList<FileSet>();
        filesets.add(fs);
    }

    public void addTarget(Target target) {
        this.target = target;
    }

    public void execute(Project project, Path pathToEvent, WatchTask task) {
        if (this.lastRun == 0l || System.currentTimeMillis() - this.lastRun > 5) {
            String watchedFile = pathToEvent.toAbsolutePath().toString();
            for (FileSet fileset : filesets) {
                PatternSet ps = fileset.mergePatterns(project);
                String[] patterns = ps.getExcludePatterns(project);
                if (patterns != null) {
                    DirectoryScanner ds = fileset.getDirectoryScanner();
                    Path fsDir = fileset.getDir().toPath();
                    Path fileDir = Paths.get(watchedFile).getParent();
                    if (fileDir.startsWith(fsDir)) {
                        String watchedFileRelative = fsDir.relativize(pathToEvent).toString();
                        String[] files = ds.getExcludedFiles();
                        for (String file : files) {
                            if (watchedFileRelative.equals(file)) {
                                task.log("Exclude file:" + file);
                                return;
                            }
                        }
                    }
                }
            }
            if (target.getName() != null) {
                project.setInheritedProperty("watched.file", watchedFile);
                project.executeTarget(target.getName());
            } else {
                project.setInheritedProperty("watched.file", watchedFile);
                target.execute();
            }
        }
        this.lastRun = System.currentTimeMillis();
    }

    public void startWatching(Project project, WatchService watchService) throws IOException {
        watchedPaths = new HashMap<WatchKey, Path>();

        for (FileSet fileset : filesets) {
            File dir = fileset.getDir();
            DirectoryScanner scanner = fileset.getDirectoryScanner(project);
            String[] files = scanner.getIncludedDirectories();
            for (String d : files) {
                Path path = Paths.get(dir.getAbsolutePath(), d);
                System.out.println("Watching: " + path.toString());
                watchedPaths.put(path.register(watchService, ENTRY_DELETE, ENTRY_MODIFY, ENTRY_CREATE), path);
            }

            if (dir != null) {
                Path path = Paths.get(dir.getAbsolutePath());
                watchedPaths.put(path.register(watchService, ENTRY_DELETE, ENTRY_MODIFY, ENTRY_CREATE), path);
            }
        }
    }

    public boolean watching(WatchKey key) {
        return watchedPaths.containsKey(key);
    }

    public void stopWatching(WatchService watcher) {
        for (WatchKey key : watchedPaths.keySet()) {
            key.cancel();
        }
    }

    public void addWatch(Path path, WatchService watchService) throws IOException {
        watchedPaths.put(path.register(watchService, ENTRY_DELETE, ENTRY_MODIFY, ENTRY_CREATE), path);
    }

    public void removeWatch(WatchKey key) {
        watchedPaths.remove(key);
    }

    public Path resolve(WatchKey key, Path context) {
        return watchedPaths.containsKey(key) ? watchedPaths.get(key).resolve(context) : null;
    }
}
