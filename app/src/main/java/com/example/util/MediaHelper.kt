package com.example.util

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.DecimalFormat

object MediaHelper {

    /**
     * Récupère le nom réel d'un fichier sélectionné depuis la galerie ou les documents
     */
    fun queryFileName(context: Context, uri: Uri): String {
        var name = "fichier_importe"
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = it.getString(index) ?: name
                    }
                }
            }
        } catch (_: Exception) {
            name = uri.lastPathSegment ?: name
        }
        return name
    }

    /**
     * Calcule la taille lisible du fichier (ex: 4.2 Mo, 320 Ko)
     */
    fun queryFileSize(context: Context, uri: Uri): String {
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0) {
                        val bytes = it.getLong(sizeIndex)
                        if (bytes > 0) {
                            return formatFileSize(bytes)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore
        }
        return "Taille inconnue"
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 Ko"
        val units = arrayOf("B", "Ko", "Mo", "Go")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val formatted = DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble()))
        return "$formatted ${units[digitGroups]}"
    }

    /**
     * Détecte le type MIME d'un URI
     */
    fun queryMimeType(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.getType(uri) ?: "application/octet-stream"
        } catch (_: Exception) {
            "application/octet-stream"
        }
    }

    /**
     * Télécharge ou exporte directement une vidéo, une image ou un PDF dans la mémoire / galerie du téléphone
     */
    fun downloadToPhone(context: Context, urlOrUri: String, title: String, type: String) {
        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30).ifEmpty { "media" }

        // Si l'URL est un contenu local (content:// ou file://), on l'exporte directement vers la galerie/téléchargements publics
        if (urlOrUri.startsWith("content://") || urlOrUri.startsWith("file://")) {
            exportContentUriToPublicStorage(context, Uri.parse(urlOrUri), cleanTitle, type)
            return
        }

        // Si c'est une URL web standard (http/https)
        if (!urlOrUri.startsWith("http://") && !urlOrUri.startsWith("https://")) {
            Toast.makeText(context, "Lien non téléchargeable directement : $urlOrUri", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val extension = when (type.lowercase()) {
                "video" -> "mp4"
                "photo" -> "jpg"
                "document" -> "pdf"
                "music" -> "mp3"
                else -> "bin"
            }
            val mimeType = when (type.lowercase()) {
                "video" -> "video/mp4"
                "photo" -> "image/jpeg"
                "document" -> "application/pdf"
                "music" -> "audio/mpeg"
                else -> "application/octet-stream"
            }
            val fileName = "BrightVideo_${cleanTitle}_${System.currentTimeMillis()}.$extension"

            val targetDirectory = when (type.lowercase()) {
                "video" -> Environment.DIRECTORY_MOVIES
                "photo" -> Environment.DIRECTORY_PICTURES
                else -> Environment.DIRECTORY_DOWNLOADS
            }

            val request = DownloadManager.Request(Uri.parse(urlOrUri)).apply {
                setTitle("Bright Vidéo : $title")
                setDescription("Téléchargement vers la galerie et la mémoire du téléphone...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(targetDirectory, fileName)
                setMimeType(mimeType)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)

            val destLabel = when (type.lowercase()) {
                "video" -> "Galerie Vidéos / Téléchargements"
                "photo" -> "Galerie Photos"
                "document" -> "Dossier Téléchargements (PDF)"
                else -> "Dossier Téléchargements"
            }

            Toast.makeText(
                context,
                "Téléchargement démarré ! Votre fichier sera disponible dans : $destLabel",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Échec du téléchargement : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Copie un URI local (sélectionné depuis le sélecteur Android) vers le stockage public du téléphone
     * (Galerie ou Téléchargements)
     */
    private fun exportContentUriToPublicStorage(
        context: Context,
        sourceUri: Uri,
        baseName: String,
        type: String
    ) {
        try {
            val resolver = context.contentResolver
            val extension = when (type.lowercase()) {
                "video" -> "mp4"
                "photo" -> "jpg"
                "document" -> "pdf"
                "music" -> "mp3"
                else -> "bin"
            }
            val mimeType = when (type.lowercase()) {
                "video" -> "video/mp4"
                "photo" -> "image/jpeg"
                "document" -> "application/pdf"
                "music" -> "audio/mpeg"
                else -> "application/octet-stream"
            }
            val fileName = "BrightVideo_${baseName}_${System.currentTimeMillis()}.$extension"

            var outputStream: FileOutputStream? = null
            var inputStream: InputStream? = null

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val targetFile = File(downloadsDir, fileName)

            inputStream = resolver.openInputStream(sourceUri)
            if (inputStream != null) {
                outputStream = FileOutputStream(targetFile)
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()

                Toast.makeText(
                    context,
                    "Fichier exporté avec succès dans le dossier Téléchargements : ${targetFile.name}",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(context, "Impossible de lire le fichier source sélectionné.", Toast.LENGTH_SHORT).show()
            }

            inputStream?.close()
            outputStream?.close()
        } catch (e: Exception) {
            Toast.makeText(context, "Erreur d'enregistrement : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Ouvre directement un fichier média ou PDF avec les applications par défaut du téléphone
     */
    fun openFileDirectly(context: Context, uriString: String, type: String) {
        try {
            val uri = Uri.parse(uriString)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    uri,
                    when (type.lowercase()) {
                        "video" -> "video/*"
                        "photo" -> "image/*"
                        "document" -> "application/pdf"
                        "music" -> "audio/*"
                        else -> "*/*"
                    }
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Ouvrir avec..."))
        } catch (e: Exception) {
            // Repli sur l'ouverture standard de lien web
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (err: Exception) {
                Toast.makeText(context, "Aucune application disponible pour ouvrir ce fichier.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
