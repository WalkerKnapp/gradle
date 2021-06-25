/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.watch.registry.impl

import net.rubygrapefruit.platform.file.FileSystemInfo
import net.rubygrapefruit.platform.file.FileWatcher
import org.gradle.internal.watch.registry.FileWatcherUpdater

import java.util.stream.Stream

class NonHierarchicalFileWatcherUpdaterTest extends AbstractFileWatcherUpdaterTest {

    @Override
    FileWatcherUpdater createUpdater(FileWatcher watcher, WatchableHierarchies watchableHierarchies) {
        new NonHierarchicalFileWatcherUpdater(watcher, watchableHierarchies)
    }

    def "only watches directories in hierarchies to watch"() {
        def watchableHierarchies = ["first", "second", "third"].collect { file(it).createDir() }
        def fileInWatchableHierarchies = file("first/inside/root/dir/file.txt")
        def fileOutsideOfWatchableHierarchies = file("forth").file("someFile.txt")

        when:
        registerWatchableHierarchies(watchableHierarchies)
        then:
        0 * _

        when:
        fileInWatchableHierarchies.createFile()
        addSnapshot(snapshotRegularFile(fileInWatchableHierarchies))
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [fileInWatchableHierarchies.parentFile]) })
        0 * _

        when:
        fileOutsideOfWatchableHierarchies.createFile()
        addSnapshot(snapshotRegularFile(fileOutsideOfWatchableHierarchies))
        then:
        0 * _
        vfsHasSnapshotsAt(fileOutsideOfWatchableHierarchies)

        when:
        buildFinished()
        then:
        _ * watchFilter.test(_) >> true
        0 * _
        !vfsHasSnapshotsAt(fileOutsideOfWatchableHierarchies)
    }

    def "watchers are stopped when removing the last watched snapshot"() {
        def rootDir = file("root").createDir()
        ["first", "second", "third"].collect { rootDir.createFile(it) }
        def rootDirSnapshot = snapshotDirectory(rootDir)

        when:
        registerWatchableHierarchies([rootDir])
        addSnapshot(rootDirSnapshot)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [rootDir.parentFile, rootDir]) })
        0 * _

        when:
        invalidate(rootDirSnapshot.children[0])
        invalidate(rootDirSnapshot.children[1])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [rootDir.parentFile]) })
        0 * _

        when:
        invalidate(rootDirSnapshot.children[2])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [rootDir]) })
        0 * _
    }

    def "removes content on unsupported file systems at the end of the build"() {
        def watchableHierarchy = file("watchable").createDir()
        def watchableContent = watchableHierarchy.file("some/dir/file.txt").createFile()
        def unsupportedFileSystemMountPoint = watchableHierarchy.file("unsupported")
        def unwatchableContent = unsupportedFileSystemMountPoint.file("some/file.txt").createFile()
        def unsupportedFileSystem = Stub(FileSystemInfo) {
            getMountPoint() >> unsupportedFileSystemMountPoint
            getFileSystemType() >> "unsupported"
        }
        watchableFileSystemDetector.detectUnsupportedFileSystems() >> Stream.of(unsupportedFileSystem)

        when:
        registerWatchableHierarchies([watchableHierarchy])
        addSnapshot(snapshotRegularFile(watchableContent))
        then:
        vfsHasSnapshotsAt(watchableContent)
        1 * watcher.startWatching({ it as List == [watchableContent.parentFile] })
        0 * _

        when:
        addSnapshot(snapshotRegularFile(unwatchableContent))
        then:
        vfsHasSnapshotsAt(unwatchableContent)
        1 * watcher.startWatching({ it as List == [unwatchableContent.parentFile] })
        0 * _

        when:
        buildFinished()
        then:
        vfsHasSnapshotsAt(watchableContent)
        !vfsHasSnapshotsAt(unwatchableContent)
        1 * watcher.stopWatching({ it as List == [unwatchableContent.parentFile] })
        0 * _
    }
}
