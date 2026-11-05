package org.gameyfin.app.core.download.files

import io.mockk.*
import tools.jackson.databind.json.JsonMapper
import org.apache.catalina.startup.Tomcat
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.entities.Game
import org.gameyfin.pluginapi.download.DownloadMetadata
import org.gameyfin.pluginapi.download.RangeDownload
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.annotation.Configuration
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.test.*

/** Exercises the servlet's DeferredResult -> StreamingResponseBody lifecycle over actual HTTP. */
class DownloadHttpTest {
    @TempDir lateinit var directory: Path

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    class WebConfig

    @Test
    @org.junit.jupiter.api.Timeout(30)
    fun `interrupted browser download resumes and HEAD sends no body`() {
        val bytes = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val service = mockk<DownloadService>()
        val games = mockk<GameService>(relaxed = true)
        val game = mockk<Game>(relaxed = true)
        every { game.title } returns "Game"
        every { game.metadata.path } returns "/game.bin"
        every { games.getById(1) } returns game
        every { service.getRangeDownload(any(), any()) } answers {
            object : RangeDownload {
                override val metadata = DownloadMetadata("bin", bytes.size.toLong())
                override val etag = "\"unchanged\""
                override fun openStream(offset: Long, length: Long): InputStream =
                    bytes.inputStream(offset.toInt(), length.toInt())
                override fun close() = Unit
            }
        }
        every { service.processDownload(any(), any(), any(), any(), any(), any()) } answers {
            firstArg<InputStream>().copyTo(secondArg())
        }
        every { service.getRangeDownload(any(), "broken") } returns null
        every { service.getDownload(any(), "broken") } answers {
            org.gameyfin.pluginapi.download.FileDownload(object : InputStream() {
                var remaining = 100_000
                override fun read(): Int {
                    if (remaining-- > 0) return 0
                    throw java.io.IOException("Injected source failure")
                }
            }, "zip")
        }
        every { service.getProviders() } returns listOf("direct", "broken").map {
            org.gameyfin.app.core.download.provider.DownloadProviderDto(it, it, 1, it)
        }
        val catalog = mockk<org.gameyfin.app.api.v1.service.CatalogServiceV1>()
        every { catalog.gameEntity("1") } returns game
        val tomcat = Tomcat()
        tomcat.setBaseDir(directory.resolve("tomcat").toString())
        tomcat.setPort(0)
        val context = tomcat.addContext("", directory.toString())
        val spring = AnnotationConfigWebApplicationContext()
        spring.register(WebConfig::class.java)
        spring.addBeanFactoryPostProcessor { factory ->
            factory.registerSingleton("downloads", DownloadEndpoint(service, games))
            factory.registerSingleton("apiDownloads", org.gameyfin.app.api.v1.controller.DownloadControllerV1(
                org.gameyfin.app.api.v1.allowingAccess(), service, catalog, games, mockk(), JsonMapper()
            ))
            factory.registerSingleton("apiErrors", org.gameyfin.app.api.v1.ApiErrors())
        }
        val servlet = Tomcat.addServlet(context, "dispatcher", DispatcherServlet(spring))
        servlet.isAsyncSupported = true
        context.addServletMappingDecoded("/", "dispatcher")
        tomcat.connector
        try {
            tomcat.start()
            HttpClient.newHttpClient().use { client ->
                for (path in listOf("/download/1", "/v1/games/1/download")) {
                    val url = URI("http://localhost:${tomcat.connector.localPort}$path?provider=direct")
                    val first = client.send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofInputStream())
                    if (first.statusCode() != 200) {
                        val error = first.body().use { it.readAllBytes().decodeToString() }
                        fail("$path returned ${first.statusCode()}: $error")
                    }
                    val prefix = first.body().use { it.readNBytes(8192) }
                    val resumed = client.send(HttpRequest.newBuilder(url)
                        .header("Range", "bytes=${prefix.size}-")
                        .header("If-Range", first.headers().firstValue("ETag").orElseThrow()).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray())
                    assertEquals(206, resumed.statusCode())
                    assertContentEquals(bytes, prefix + resumed.body())
                    val head = client.send(HttpRequest.newBuilder(url).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.ofByteArray())
                    assertEquals(200, head.statusCode())
                    assertEquals(bytes.size.toString(), head.headers().firstValue("Content-Length").orElseThrow())
                    assertTrue(head.body().isEmpty())
                    val broken = URI("http://localhost:${tomcat.connector.localPort}$path?provider=broken")
                    assertFailsWith<java.io.IOException> {
                        client.send(HttpRequest.newBuilder(broken).GET().build(), HttpResponse.BodyHandlers.ofByteArray())
                    }
                }
                verify(exactly = 6) { games.incrementDownloadCount(game) }
            }
        } finally {
            tomcat.stop()
            tomcat.destroy()
            spring.close()
        }
    }
}
