package org.gameyfin.app.core.filesystem

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.libraries.entities.DirectoryMapping
import org.gameyfin.app.libraries.entities.IgnoredPath
import org.gameyfin.app.libraries.entities.IgnoredPathPluginSource
import org.gameyfin.app.libraries.entities.Library
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilesystemServiceTest {

    private lateinit var configService: ConfigService
    private lateinit var filesystemService: FilesystemService

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        configService = mockk()
        filesystemService = FilesystemService(configService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `listContents should return empty or root list when path is empty string`() {
        val result = filesystemService.listContents("")

        // Due to various differences in how OSs handle empty paths, we just check that it returns a non-empty list
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `listContents should return directory contents when valid path provided`() {
        every { configService.get(any<ConfigProperties<Array<String>>>()) } returns arrayOf("exe", "zip")

        val file1 = tempDir.resolve("file1.txt")
        file1.createFile()
        val file2 = tempDir.resolve("file2.exe")
        file2.createFile()
        val dir1 = tempDir.resolve("subdir")
        dir1.createDirectory()

        val result = filesystemService.listContents(tempDir.toString())

        assertEquals(3, result.size)
        assertTrue(result.any { it.name == "file1.txt" && it.type == FileType.FILE })
        assertTrue(result.any { it.name == "file2.exe" && it.type == FileType.FILE })
        assertTrue(result.any { it.name == "subdir" && it.type == FileType.DIRECTORY })
    }

    @Test
    fun `listContents should filter out hidden files`() {
        every { configService.get(any<ConfigProperties<Array<String>>>()) } returns arrayOf("exe", "zip")

        val visibleFile = tempDir.resolve("visible.txt")
        visibleFile.createFile()
        val hiddenFile = createTempFile(tempDir, ".", null)

        if (System.getProperty("os.name").lowercase().contains("win")) {
            hiddenFile.setAttribute("dos:hidden", true)
        }

        val result = filesystemService.listContents(tempDir.toString())

        assertTrue(result.any { it.name == visibleFile.name })
        assertFalse(result.any { it.name == hiddenFile.name })
    }

    @Test
    fun `listContents should return empty list when directory does not exist`() {
        every { configService.get(any<ConfigProperties<Array<String>>>()) } returns arrayOf("exe", "zip")

        val nonExistentPath = tempDir.resolve("nonexistent").toString()

        val result = filesystemService.listContents(nonExistentPath)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listContents should handle paths with different separators`() {
        every { configService.get(any<ConfigProperties<Array<String>>>()) } returns arrayOf("exe", "zip")

        val file = tempDir.resolve("test.txt")
        file.createFile()

        val windowsStylePath = tempDir.toString().replace("/", "\\")
        val result = filesystemService.listContents(windowsStylePath)

        assertTrue(result.any { it.name == "test.txt" })
    }

    @Test
    fun `listSubDirectories should return only directories`() {
        every { configService.get(any<ConfigProperties<Array<String>>>()) } returns arrayOf("exe", "zip")

        val file = tempDir.resolve("file.txt")
        file.createFile()
        val dir1 = tempDir.resolve("dir1")
        dir1.createDirectory()
        val dir2 = tempDir.resolve("dir2")
        dir2.createDirectory()

        val result = filesystemService.listSubDirectories(tempDir.toString())

        assertEquals(2, result.size)
        assertTrue(result.all { it.type == FileType.DIRECTORY })
        assertTrue(result.any { it.name == "dir1" })
        assertTrue(result.any { it.name == "dir2" })
        assertFalse(result.any { it.name == "file.txt" })
    }

    @Test
    fun `listSubDirectories should return empty list when no subdirectories exist`() {
        every { configService.get(any<ConfigProperties<Array<String>>>()) } returns arrayOf("exe", "zip")

        val file1 = tempDir.resolve("file1.txt")
        file1.createFile()
        val file2 = tempDir.resolve("file2.txt")
        file2.createFile()

        val result = filesystemService.listSubDirectories(tempDir.toString())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getHostOperatingSystem should return valid OS type`() {
        val result = filesystemService.getHostOperatingSystem()

        assertTrue(
            result == OperatingSystemType.WINDOWS ||
                    result == OperatingSystemType.MAC ||
                    result == OperatingSystemType.LINUX ||
                    result == OperatingSystemType.UNKNOWN
        )
    }

    @Test
    fun `getHostOperatingSystem should detect Windows correctly`() {
        val osName = System.getProperty("os.name").lowercase()
        val result = filesystemService.getHostOperatingSystem()

        if (osName.contains("win")) {
            assertEquals(OperatingSystemType.WINDOWS, result)
        }
    }

    @Test
    fun `getHostOperatingSystem should detect Mac correctly`() {
        val osName = System.getProperty("os.name").lowercase()
        val result = filesystemService.getHostOperatingSystem()

        if (osName.contains("mac")) {
            assertEquals(OperatingSystemType.MAC, result)
        }
    }

    @Test
    fun `getHostOperatingSystem should detect Linux correctly`() {
        val osName = System.getProperty("os.name").lowercase()
        val result = filesystemService.getHostOperatingSystem()

        if (osName.contains("nux")) {
            assertEquals(OperatingSystemType.LINUX, result)
        }
    }

    @Test
    fun `scanLibraryForGamefiles should find new game files`() {
        every { configService.get(ConfigProperties.Libraries.Scan.GameFileExtensions) } returns arrayOf("exe", "zip")
        every { configService.get(ConfigProperties.Libraries.Scan.ScanEmptyDirectories) } returns false

        val libraryDir = tempDir.resolve("library")
        libraryDir.createDirectory()
        val gameFile1 = libraryDir.resolve("game1.exe")
        gameFile1.createFile()
        val gameFile2 = libraryDir.resolve("game2.zip")
        gameFile2.createFile()

        val mockLibraryDirectory = mockk<DirectoryMapping>()
        every { mockLibraryDirectory.internalPath } returns libraryDir.toString()

        val mockLibrary = mockk<Library>()
        every { mockLibrary.name } returns "Test Library"
        every { mockLibrary.directories } returns mutableListOf(mockLibraryDirectory)
        every { mockLibrary.games } returns mutableListOf()
        every { mockLibrary.ignoredPaths } returns mutableListOf()

        val result = filesystemService.scanLibraryForGamefiles(mockLibrary)

        assertEquals(2, result.newPaths.size)
        assertTrue(result.newPaths.any { it.name == "game1.exe" })
        assertTrue(result.newPaths.any { it.name == "game2.zip" })
        assertTrue(result.removedGamePaths.isEmpty())
        assertTrue(result.removedIgnoredPaths.isEmpty())
    }

    @Test
    fun `scanLibraryForGamefiles should find new directories`() {
        every { configService.get(ConfigProperties.Libraries.Scan.GameFileExtensions) } returns arrayOf("exe", "zip")
        every { configService.get(ConfigProperties.Libraries.Scan.ScanEmptyDirectories) } returns false

        val libraryDir = tempDir.resolve("library")
        libraryDir.createDirectory()
        val gameDir = libraryDir.resolve("game-folder")
        gameDir.createDirectory()
        gameDir.resolve("data.dat").createFile()

        val mockLibraryDirectory = mockk<DirectoryMapping>()
        every { mockLibraryDirectory.internalPath } returns libraryDir.toString()

        val mockLibrary = mockk<Library>()
        every { mockLibrary.name } returns "Test Library"
        every { mockLibrary.directories } returns mutableListOf(mockLibraryDirectory)
        every { mockLibrary.games } returns mutableListOf()
        every { mockLibrary.ignoredPaths } returns mutableListOf()

        val result = filesystemService.scanLibraryForGamefiles(mockLibrary)

        assertEquals(1, result.newPaths.size)
        assertTrue(result.newPaths.any { it.name == "game-folder" })
    }

    @Test
    fun `scanLibraryForGamefiles should exclude empty directories when configuration is false`() {
        every { configService.get(ConfigProperties.Libraries.Scan.GameFileExtensions) } returns arrayOf("exe")
        every { configService.get(ConfigProperties.Libraries.Scan.ScanEmptyDirectories) } returns false

        val libraryDir = tempDir.resolve("library")
        libraryDir.createDirectory()
        val emptyDir = libraryDir.resolve("empty-folder")
        emptyDir.createDirectory()
        val nonEmptyDir = libraryDir.resolve("non-empty-folder")
        nonEmptyDir.createDirectory()
        nonEmptyDir.resolve("file.txt").createFile()

        val mockLibraryDirectory = mockk<DirectoryMapping>()
        every { mockLibraryDirectory.internalPath } returns libraryDir.toString()

        val mockLibrary = mockk<Library>()
        every { mockLibrary.name } returns "Test Library"
        every { mockLibrary.directories } returns mutableListOf(mockLibraryDirectory)
        every { mockLibrary.games } returns mutableListOf()
        every { mockLibrary.ignoredPaths } returns mutableListOf()

        val result = filesystemService.scanLibraryForGamefiles(mockLibrary)

        assertEquals(1, result.newPaths.size)
        assertTrue(result.newPaths.any { it.name == "non-empty-folder" })
        assertFalse(result.newPaths.any { it.name == "empty-folder" })
    }

    @Test
    fun `scanLibraryForGamefiles should include empty directories when configuration is true`() {
        every { configService.get(ConfigProperties.Libraries.Scan.GameFileExtensions) } returns arrayOf("exe")
        every { configService.get(ConfigProperties.Libraries.Scan.ScanEmptyDirectories) } returns true

        val libraryDir = tempDir.resolve("library")
        libraryDir.createDirectory()
        val emptyDir = libraryDir.resolve("empty-folder")
        emptyDir.createDirectory()

        val mockLibraryDirectory = mockk<DirectoryMapping>()
        every { mockLibraryDirectory.internalPath } returns libraryDir.toString()

        val mockLibrary = mockk<Library>()
        every { mockLibrary.name } returns "Test Library"
        every { mockLibrary.directories } returns mutableListOf(mockLibraryDirectory)
        every { mockLibrary.games } returns mutableListOf()
        every { mockLibrary.ignoredPaths } returns mutableListOf()

        val result = filesystemService.scanLibraryForGamefiles(mockLibrary)

        assertEquals(1, result.newPaths.size)
        assertTrue(result.newPaths.any { it.name == "empty-folder" })
    }

    @Test
    fun `scanLibraryForGamefiles should filter by game file extensions`() {
        every { configService.get(ConfigProperties.Libraries.Scan.GameFileExtensions) } returns arrayOf("exe", "zip")
        every { configService.get(ConfigProperties.Libraries.Scan.ScanEmptyDirectories) } returns false

        val libraryDir = tempDir.resolve("library")
        libraryDir.createDirectory()
        val gameFile = libraryDir.resolve("game.exe")
        gameFile.createFile()
        val textFile = libraryDir.resolve("readme.txt")
        textFile.createFile()
        val imageFile = libraryDir.resolve("cover.jpg")
        imageFile.createFile()

        val mockLibraryDirectory = mockk<DirectoryMapping>()
        every { mockLibraryDirectory.internalPath } returns libraryDir.toString()

        val mockLibrary = mockk<Library>()
        every { mockLibrary.name } returns "Test Library"
        every { mockLibrary.directories } returns mutableListOf(mockLibraryDirectory)
        every { mockLibrary.games } returns mutableListOf()
        every { mockLibrary.ignoredPaths } returns mutableListOf()

        val result = filesystemService.scanLibraryForGamefiles(mockLibrary)

        assertEquals(1, result.newPaths.size)
        assertTrue(result.newPaths.any { it.name == "game.exe" })
        assertFalse(result.newPaths.any { it.name == "readme.txt" })
        assertFalse(result.newPaths.any { it.name == "cover.jpg" })
    }

    @Test
    fun `scanLibraryForGamefiles should be case insensitive for extensions`() {
        every { configService.get(ConfigProperties.Libraries.Scan.GameFileExtensions) } returns arrayOf("exe")
        every { configService.get(ConfigProperties.Libraries.Scan.ScanEmptyDirectories) } returns false

        val libraryDir = tempDir.resolve("library")
        libraryDir.createDirectory()
        val gameFile1 = libraryDir.resolve("game1.exe")
        gameFile1.createFile()
        val gameFile2 = libraryDir.resolve("game2.EXE")
        gameFile2.createFile()
        val gameFile3 = libraryDir.resolve("game3.Exe")
        gameFile3.createFile()

        val mockLibraryDirectory = mockk<DirectoryMapping>()
        every { mockLibraryDirectory.internalPath } returns libraryDir.toString()

        val mockLibrary = mockk<Library>()
        every { mockLibrary.name } returns "Test Library"
        every { mockLibrary.directories } returns mutableListOf(mockLibraryDirectory)
        every { mockLibrary.games } returns mutableListOf()
        every { mockLibrary.ignoredPaths } returns mutableListOf()

        val result = filesystemService.scanLibraryForGamefiles(mockLibrary)

        assertEquals(3, result.newPaths.size)
    }

    @Test
    fun `scanLibraryForGamefiles should detect removed game paths`() {
        every { configService.get(ConfigProperties.Libraries.Scan.GameFileExtensions) } returns arrayOf("exe")
        every { configService.get(ConfigProperties.Libraries.Scan.ScanEmptyDirectories) } returns false

        val libraryDir = tempDir.resolve("library")
        libraryDir.createDirectory()
        val existingGame = libraryDir.resolve("existing.exe")
        existingGame.createFile()

        val removedGamePath = libraryDir.resolve("removed.exe").toString()

        val mockGameMetadata1 = mockk<org.gameyfin.app.games.entities.GameMetadata>()
        every { mockGameMetadata1.path } returns existingGame.toString()
        val mockGame1 = mockk<Game>()
        every { mockGame1.metadata } returns mockGameMetadata1

        val mockGameMetadata2 = mockk<org.gameyfin.app.games.entities.GameMetadata>()
        every { mockGameMetadata2.path } returns removedGamePath
        val mockGame2 = mockk<Game>()
        every { mockGame2.metadata } returns mockGameMetadata2

        val mockLibraryDirectory = mockk<DirectoryMapping>()
        every { mockLibraryDirectory.internalPath } returns libraryDir.toString()

        val mockLibrary = mockk<Library>()
        every { mockLibrary.name } returns "Test Library"
        every { mockLibrary.directories } returns mutableListOf(mockLibraryDirectory)
        every { mockLibrary.games } returns mutableListOf(mockGame1, mockGame2)
        every { mockLibrary.ignoredPaths } returns mutableListOf()

        val result = filesystemService.scanLibraryForGamefiles(mockLibrary)

        assertEquals(0, result.newPaths.size)
        assertEquals(1, result.removedGamePaths.size)
        assertEquals(Path(removedGamePath), result.removedGamePaths[0])
        assertEquals(0, result.removedIgnoredPaths.size)
    }

    @Test
    fun `scanLibraryForGamefiles should detect removed unmatched paths`() {
        every { configService.get(ConfigProperties.Libraries.Scan.GameFileExtensions) } returns arrayOf("exe")
        every { configService.get(ConfigProperties.Libraries.Scan.ScanEmptyDirectories) } returns false

        val libraryDir = tempDir.resolve("library")
        libraryDir.createDirectory()
        val existingFile = libraryDir.resolve("existing.exe")
        existingFile.createFile()

        val removedIgnoredPathStr = libraryDir.resolve("removed.exe").toString()
        val removedIgnoredPath =
            IgnoredPath(path = removedIgnoredPathStr, source = IgnoredPathPluginSource(mutableListOf()))

        val mockLibraryDirectory = mockk<DirectoryMapping>()
        every { mockLibraryDirectory.internalPath } returns libraryDir.toString()

        val mockGame = mockk<Game>()
        every { mockGame.metadata.path } returns existingFile.toString()

        val mockLibrary = mockk<Library>()
        every { mockLibrary.name } returns "Test Library"
        every { mockLibrary.directories } returns mutableListOf(mockLibraryDirectory)
        every { mockLibrary.games } returns mutableListOf(mockGame)
        every { mockLibrary.ignoredPaths } returns mutableListOf(removedIgnoredPath)

        val result = filesystemService.scanLibraryForGamefiles(mockLibrary)

        assertEquals(0, result.newPaths.size)
        assertEquals(0, result.removedGamePaths.size)
        assertEquals(1, result.removedIgnoredPaths.size)
        assertEquals(removedIgnoredPathStr, result.removedIgnoredPaths[0].path)
    }

    @Test
    fun `scanLibraryForGamefiles should not report existing games as new`() {
        every { configService.get(ConfigProperties.Libraries.Scan.GameFileExtensions) } returns arrayOf("exe")
        every { configService.get(ConfigProperties.Libraries.Scan.ScanEmptyDirectories) } returns false

        val libraryDir = tempDir.resolve("library")
        libraryDir.createDirectory()
        val existingGame = libraryDir.resolve("game.exe")
        existingGame.createFile()

        val mockGameMetadata = mockk<org.gameyfin.app.games.entities.GameMetadata>()
        every { mockGameMetadata.path } returns existingGame.toString()
        val mockGame = mockk<Game>()
        every { mockGame.metadata } returns mockGameMetadata

        val mockLibraryDirectory = mockk<DirectoryMapping>()
        every { mockLibraryDirectory.internalPath } returns libraryDir.toString()

        val mockLibrary = mockk<Library>()
        every { mockLibrary.name } returns "Test Library"
        every { mockLibrary.directories } returns mutableListOf(mockLibraryDirectory)
        every { mockLibrary.games } returns mutableListOf(mockGame)
        every { mockLibrary.ignoredPaths } returns mutableListOf()

        val result = filesystemService.scanLibraryForGamefiles(mockLibrary)

        assertEquals(0, result.newPaths.size)
        assertEquals(0, result.removedGamePaths.size)
        assertEquals(0, result.removedIgnoredPaths.size)
    }

    @Test
    fun `scanLibraryForGamefiles should not report existing unmatched paths as new`() {
        every { configService.get(ConfigProperties.Libraries.Scan.GameFileExtensions) } returns arrayOf("exe")
        every { configService.get(ConfigProperties.Libraries.Scan.ScanEmptyDirectories) } returns false

        val libraryDir = tempDir.resolve("library")
        libraryDir.createDirectory()
        val existingFile = libraryDir.resolve("unmatched.exe")
        existingFile.createFile()

        val mockLibraryDirectory = mockk<DirectoryMapping>()
        every { mockLibraryDirectory.internalPath } returns libraryDir.toString()

        val existingIgnoredPath =
            IgnoredPath(path = existingFile.toString(), source = IgnoredPathPluginSource(mutableListOf()))

        val mockLibrary = mockk<Library>()
        every { mockLibrary.name } returns "Test Library"
        every { mockLibrary.directories } returns mutableListOf(mockLibraryDirectory)
        every { mockLibrary.games } returns mutableListOf()
        every { mockLibrary.ignoredPaths } returns mutableListOf(existingIgnoredPath)

        val result = filesystemService.scanLibraryForGamefiles(mockLibrary)

        assertEquals(0, result.newPaths.size)
        assertEquals(0, result.removedGamePaths.size)
        assertEquals(0, result.removedIgnoredPaths.size)
    }

    @Test
    fun `scanLibraryForGamefiles should skip invalid library directories`() {
        every { configService.get(ConfigProperties.Libraries.Scan.GameFileExtensions) } returns arrayOf("exe")
        every { configService.get(ConfigProperties.Libraries.Scan.ScanEmptyDirectories) } returns false

        val validLibraryDir = tempDir.resolve("valid")
        validLibraryDir.createDirectory()
        val gameFile = validLibraryDir.resolve("game.exe")
        gameFile.createFile()

        val invalidLibraryDir = tempDir.resolve("invalid")

        val mockValidDirectory = mockk<DirectoryMapping>()
        every { mockValidDirectory.internalPath } returns validLibraryDir.toString()

        val mockInvalidDirectory = mockk<DirectoryMapping>()
        every { mockInvalidDirectory.internalPath } returns invalidLibraryDir.toString()

        val mockLibrary = mockk<Library>()
        every { mockLibrary.name } returns "Test Library"
        every { mockLibrary.directories } returns mutableListOf(mockValidDirectory, mockInvalidDirectory)
        every { mockLibrary.games } returns mutableListOf()
        every { mockLibrary.ignoredPaths } returns mutableListOf()

        val result = filesystemService.scanLibraryForGamefiles(mockLibrary)

        assertEquals(1, result.newPaths.size)
        assertTrue(result.newPaths.any { it.name == "game.exe" })
    }

    @Test
    fun `scanLibraryForGamefiles should handle multiple library directories`() {
        every { configService.get(ConfigProperties.Libraries.Scan.GameFileExtensions) } returns arrayOf("exe")
        every { configService.get(ConfigProperties.Libraries.Scan.ScanEmptyDirectories) } returns false

        val libraryDir1 = tempDir.resolve("library1")
        libraryDir1.createDirectory()
        val gameFile1 = libraryDir1.resolve("game1.exe")
        gameFile1.createFile()

        val libraryDir2 = tempDir.resolve("library2")
        libraryDir2.createDirectory()
        val gameFile2 = libraryDir2.resolve("game2.exe")
        gameFile2.createFile()

        val mockLibraryDirectory1 = mockk<DirectoryMapping>()
        every { mockLibraryDirectory1.internalPath } returns libraryDir1.toString()

        val mockLibraryDirectory2 = mockk<DirectoryMapping>()
        every { mockLibraryDirectory2.internalPath } returns libraryDir2.toString()

        val mockLibrary = mockk<Library>()
        every { mockLibrary.name } returns "Test Library"
        every { mockLibrary.directories } returns mutableListOf(mockLibraryDirectory1, mockLibraryDirectory2)
        every { mockLibrary.games } returns mutableListOf()
        every { mockLibrary.ignoredPaths } returns mutableListOf()

        val result = filesystemService.scanLibraryForGamefiles(mockLibrary)

        assertEquals(2, result.newPaths.size)
        assertTrue(result.newPaths.any { it.name == "game1.exe" })
        assertTrue(result.newPaths.any { it.name == "game2.exe" })
    }

    @Test
    fun `scanLibraryForGamefiles should handle inaccessible directories gracefully`() {
        every { configService.get(ConfigProperties.Libraries.Scan.GameFileExtensions) } returns arrayOf("exe")
        every { configService.get(ConfigProperties.Libraries.Scan.ScanEmptyDirectories) } returns false

        val libraryDir = tempDir.resolve("library")
        libraryDir.createDirectory()

        val mockLibraryDirectory = mockk<DirectoryMapping>()
        every { mockLibraryDirectory.internalPath } returns libraryDir.toString()

        val mockLibrary = mockk<Library>()
        every { mockLibrary.name } returns "Test Library"
        every { mockLibrary.directories } returns mutableListOf(mockLibraryDirectory)
        every { mockLibrary.games } returns mutableListOf()
        every { mockLibrary.ignoredPaths } returns mutableListOf()

        libraryDir.toFile().setReadable(false)

        val result = filesystemService.scanLibraryForGamefiles(mockLibrary)

        libraryDir.toFile().setReadable(true)

        assertTrue(result.newPaths.isEmpty())
    }

    @Test
    fun `calculateFileSize should return file size for regular files`() {
        every { configService.get(any<ConfigProperties<Array<String>>>()) } returns arrayOf("exe")

        val file = tempDir.resolve("test.txt")
        file.writeText("Hello World")

        val result = filesystemService.calculateFileSize(file.toString())

        assertEquals(11L, result)
    }

    @Test
    fun `calculateFileSize should return sum of all files in directory`() {
        every { configService.get(any<ConfigProperties<Array<String>>>()) } returns arrayOf("exe")

        val dir = tempDir.resolve("testdir")
        dir.createDirectory()
        val file1 = dir.resolve("file1.txt")
        file1.writeText("12345")
        val file2 = dir.resolve("file2.txt")
        file2.writeText("6789")

        val result = filesystemService.calculateFileSize(dir.toString())

        assertEquals(9L, result)
    }

    @Test
    fun `calculateFileSize should handle nested directories`() {
        every { configService.get(any<ConfigProperties<Array<String>>>()) } returns arrayOf("exe")

        val dir = tempDir.resolve("parent")
        dir.createDirectory()
        val file1 = dir.resolve("file1.txt")
        file1.writeText("123")

        val subdir = dir.resolve("subdir")
        subdir.createDirectory()
        val file2 = subdir.resolve("file2.txt")
        file2.writeText("456")

        val result = filesystemService.calculateFileSize(dir.toString())

        assertEquals(6L, result)
    }

    @Test
    fun `calculateFileSize should return 0 for empty directory`() {
        every { configService.get(any<ConfigProperties<Array<String>>>()) } returns arrayOf("exe")

        val dir = tempDir.resolve("emptydir")
        dir.createDirectory()

        val result = filesystemService.calculateFileSize(dir.toString())

        assertEquals(0L, result)
    }

    @Test
    fun `calculateFileSize should return 0 for non-existent path`() {
        every { configService.get(any<ConfigProperties<Array<String>>>()) } returns arrayOf("exe")

        val nonExistentPath = tempDir.resolve("nonexistent").toString()

        val result = filesystemService.calculateFileSize(nonExistentPath)

        assertEquals(0L, result)
    }

    @Test
    fun `calculateFileSize should return 0 on exception`() {
        every { configService.get(any<ConfigProperties<Array<String>>>()) } returns arrayOf("exe")

        val invalidPath = "\u0000invalid"

        val result = filesystemService.calculateFileSize(invalidPath)

        assertEquals(0L, result)
    }
}