package org.gameyfin.db.h2

import com.vanniktech.blurhash.BlurHash
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

/**
 * Helper methods for calculating blurhashes during database migration.
 */
object BlurhashMigration {

    private data class ImageRecord(val id: Long, val contentId: String)

    /**
     * Scale down image for faster blurhash calculation.
     * Blurhash doesn't need full resolution - 100px width is plenty for a good blur.
     */
    private fun scaleImageForBlurhash(original: BufferedImage, maxWidth: Int = 100): BufferedImage {
        val originalWidth = original.width
        val originalHeight = original.height

        // If image is already small enough, return as-is
        if (originalWidth <= maxWidth) {
            return original
        }

        val scale = maxWidth.toDouble() / originalWidth
        val targetWidth = maxWidth
        val targetHeight = (originalHeight * scale).toInt()

        val scaled = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = scaled.createGraphics()

        // Use fast scaling for blurhash - quality doesn't matter much for a blur
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)

        g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null)
        g2d.dispose()

        return scaled
    }

    /**
     * Calculate blurhash for all images in the database.
     * This method is called from Flyway migration V2.3.0.6.
     * Uses multithreading and batch updates for performance.
     */
    @JvmStatic
    fun calculateBlurhashesForAllImages(conn: Connection, dataPath: String) {
        val startTime = System.currentTimeMillis()

        // Fetch all images first (fast)
        val images = mutableListOf<ImageRecord>()
        conn.prepareStatement("SELECT ID, CONTENT_ID FROM IMAGE WHERE CONTENT_ID IS NOT NULL").use { stmt ->
            val rs = stmt.executeQuery()
            while (rs.next()) {
                images.add(ImageRecord(rs.getLong("ID"), rs.getString("CONTENT_ID")))
            }
        }

        println("Found ${images.size} images to process")

        if (images.isEmpty()) {
            println("No images to process")
            return
        }

        // Calculate blurhashes in parallel
        val blurhashes = ConcurrentHashMap<Long, String>()
        val processedCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)
        val failedCount = AtomicInteger(0)

        // Use available processors, but cap at reasonable limit
        val threadCount = minOf(Runtime.getRuntime().availableProcessors(), 8)
        val executor = Executors.newFixedThreadPool(threadCount)

        println("Warning: This operation may take a while depending on the number of images and their sizes.")
        println("Don't interrupt the process to avoid corrupting your database!")

        images.forEach { imageRecord ->
            executor.submit {
                try {
                    val imageFile = File(dataPath, imageRecord.contentId)

                    if (imageFile.exists() && imageFile.canRead()) {
                        val originalImage = ImageIO.read(imageFile)

                        if (originalImage != null) {
                            // Scale down for much faster processing
                            val scaledImage = scaleImageForBlurhash(originalImage)

                            val blurhash = if (scaledImage.width > scaledImage.height) {
                                // Landscape
                                BlurHash.encode(scaledImage, componentX = 4, componentY = 3)
                            } else if (scaledImage.width < scaledImage.height) {
                                // Portrait
                                BlurHash.encode(scaledImage, componentX = 3, componentY = 4)
                            } else {
                                // Square
                                BlurHash.encode(scaledImage, componentX = 3, componentY = 3)
                            }

                            blurhashes[imageRecord.id] = blurhash
                            successCount.incrementAndGet()
                        } else {
                            failedCount.incrementAndGet()
                        }
                    } else {
                        failedCount.incrementAndGet()
                    }
                } catch (_: Exception) {
                    failedCount.incrementAndGet()
                    // Silently fail individual images to avoid spam
                } finally {
                    val processed = processedCount.incrementAndGet()
                    if (processed % 100 == 0 || processed == images.size) {
                        println("Progress: $processed/${images.size} images processed ($successCount successful, $failedCount failed)")
                    }
                }
            }
        }

        // Wait for all tasks to complete
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.HOURS)

        // Batch update the database (fast)
        println("Updating database with ${blurhashes.size} blurhashes...")
        conn.autoCommit = false

        try {
            conn.prepareStatement("UPDATE IMAGE SET BLURHASH = ? WHERE ID = ?").use { updateStmt ->
                var batchCount = 0

                blurhashes.forEach { (imageId, blurhash) ->
                    updateStmt.setString(1, blurhash)
                    updateStmt.setLong(2, imageId)
                    updateStmt.addBatch()
                    batchCount++

                    // Execute batch every 500 records
                    if (batchCount % 500 == 0) {
                        updateStmt.executeBatch()
                        conn.commit()
                        batchCount = 0
                    }
                }

                // Execute remaining batch
                if (batchCount > 0) {
                    updateStmt.executeBatch()
                    conn.commit()
                }
            }
        } finally {
            conn.autoCommit = true
        }

        val duration = (System.currentTimeMillis() - startTime) / 1000.0
        println(
            "Blurhash migration completed in %.2f seconds: %d of %d images processed successfully (%d failed)".format(
                duration, successCount.get(), images.size, failedCount.get()
            )
        )
    }
}

