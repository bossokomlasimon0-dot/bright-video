package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.collectAsState
import com.example.data.model.AiConfig
import com.example.data.model.AiModels
import com.example.data.repository.AiConfigRepository
import com.example.data.service.GeminiAiService
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Publication
import com.example.data.model.PublicationType
import com.example.data.repository.PublicationRepository
import com.example.ui.components.AppCategories
import com.example.ui.theme.BrightAmberAccent
import com.example.ui.theme.BrightVioletPrimary
import com.example.ui.theme.StatusApprovedGreen
import com.example.ui.theme.StatusPendingYellow
import com.example.util.MediaHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishScreen(
    publicationRepository: PublicationRepository,
    onPublishSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    aiConfigRepository: AiConfigRepository? = null
) {
    val context = LocalContext.current
    val effectiveAiRepo = aiConfigRepository ?: remember { AiConfigRepository(context) }
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val isAdmin = publicationRepository.isUserAdmin()

    val aiConfig by effectiveAiRepo.observeAiConfig().collectAsState(initial = AiConfig())
    val aiService = remember { GeminiAiService() }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(PublicationType.VIDEO) }
    var selectedCategory by remember { mutableStateOf(AppCategories.getOrElse(1) { "Technologie" }) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    var externalUrl by remember { mutableStateOf("") }
    var thumbnailUrl by remember { mutableStateOf("") }
    var contentText by remember { mutableStateOf("") }
    var tagsInput by remember { mutableStateOf("") }

    var isGeneratingAi by remember { mutableStateOf(false) }
    var selectedAiModel by remember(aiConfig.defaultModel) {
        mutableStateOf(aiConfig.defaultModel.ifEmpty { "gemini-1.5-flash" })
    }

    var importedFileName by remember { mutableStateOf<String?>(null) }
    var importedFileUri by remember { mutableStateOf<Uri?>(null) }
    var importedFileSize by remember { mutableStateOf<String?>(null) }

    // Launcher 1: Video picker (Android Photo Picker - Videos only)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            importedFileUri = uri
            val name = MediaHelper.queryFileName(context, uri)
            importedFileName = name
            importedFileSize = MediaHelper.queryFileSize(context, uri)
            externalUrl = uri.toString()
            selectedType = PublicationType.VIDEO
            selectedCategory = "Vidéos & Cinéma"
            if (title.isBlank()) {
                title = name.substringBeforeLast(".")
            }
            Toast.makeText(context, "Vidéo locale sélectionnée : $name", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher 2: Photo picker (Android Photo Picker - Images only)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            importedFileUri = uri
            val name = MediaHelper.queryFileName(context, uri)
            importedFileName = name
            importedFileSize = MediaHelper.queryFileSize(context, uri)
            externalUrl = uri.toString()
            thumbnailUrl = uri.toString()
            selectedType = PublicationType.PHOTO
            selectedCategory = "Photos & Création"
            if (title.isBlank()) {
                title = name.substringBeforeLast(".")
            }
            Toast.makeText(context, "Photo locale sélectionnée : $name", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher 3: PDF Document picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            importedFileUri = uri
            val name = MediaHelper.queryFileName(context, uri)
            importedFileName = name
            importedFileSize = MediaHelper.queryFileSize(context, uri)
            externalUrl = uri.toString()
            selectedType = PublicationType.DOCUMENT
            selectedCategory = "Documents & Livres"
            if (title.isBlank()) {
                title = name.substringBeforeLast(".")
            }
            Toast.makeText(context, "Document PDF sélectionné : $name", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher 4: All files picker (audio, zip, etc.)
    val anyFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            importedFileUri = uri
            val name = MediaHelper.queryFileName(context, uri)
            importedFileName = name
            importedFileSize = MediaHelper.queryFileSize(context, uri)
            externalUrl = uri.toString()
            if (title.isBlank()) {
                title = name.substringBeforeLast(".")
            }
            if (name.endsWith(".mp3", ignoreCase = true) || name.endsWith(".wav", ignoreCase = true) || name.endsWith(".m4a", ignoreCase = true)) {
                selectedType = PublicationType.MUSIC
                selectedCategory = "Musique & Audio"
            } else if (name.endsWith(".pdf", ignoreCase = true)) {
                selectedType = PublicationType.DOCUMENT
                selectedCategory = "Documents & Livres"
            } else {
                selectedType = PublicationType.DOWNLOAD
                selectedCategory = "Téléchargements"
            }
            Toast.makeText(context, "Fichier sélectionné : $name", Toast.LENGTH_SHORT).show()
        }
    }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmittedSuccess by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Publier un contenu",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Partagez des liens de vidéos, musiques, tutoriels, articles ou documents",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Community Moderation Notice
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isAdmin) "Mode Administrateur : Publication directe" else "Système de modération actif",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isAdmin) {
                            "En tant qu'administrateur, votre contenu sera publié directement sans passer par la file d'attente."
                        } else {
                            "Pour garantir la qualité et la sécurité de Bright Video, les publications des abonnés sont vérifiées par notre équipe avant d'être visibles publiquement. Aucun fichier vidéo n'est hébergé dans Firebase : utilisez des liens externes légaux."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Content Type Selection Chips
        Text(
            text = "Type de publication",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PublicationType.entries.take(4).forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(type.label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PublicationType.entries.drop(4).forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(type.label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Phone Local Import Option Card (Zero-permission Photo Picker & PDF Picker)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = BrightAmberAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Importer directement depuis votre téléphone",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Accédez aux galeries locales (vidéos, photos) et aux documents PDF stockés sur votre smartphone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Row 1: Video and Photo Gallery
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_video_button")
                    ) {
                        Icon(imageVector = Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Galerie Vidéos", fontSize = 11.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_photo_button")
                    ) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Galerie Photos", fontSize = 11.sp, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row 2: PDF Document and All Files
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            pdfPickerLauncher.launch("application/pdf")
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_pdf_document_button")
                    ) {
                        Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Document PDF", fontSize = 11.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            anyFilePickerLauncher.launch("*/*")
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_all_files_button")
                    ) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tous Fichiers", fontSize = 11.sp, maxLines = 1)
                    }
                }

                if (importedFileName != null && importedFileUri != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StatusApprovedGreen.copy(alpha = 0.12f),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = StatusApprovedGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = importedFileName ?: "Fichier local",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "${importedFileSize ?: "Taille calculée"} • Prêt pour le partage",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        importedFileName = null
                                        importedFileUri = null
                                        importedFileSize = null
                                        externalUrl = ""
                                        thumbnailUrl = ""
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Retirer", modifier = Modifier.size(16.dp))
                                }
                            }

                            // Optional photo preview if it's an image
                            if (selectedType == PublicationType.PHOTO && thumbnailUrl.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = thumbnailUrl,
                                        contentDescription = "Aperçu de la photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        importedFileUri?.let { uri ->
                                            MediaHelper.openFileDirectly(context, uri.toString(), selectedType.id)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Aperçu / Ouvrir", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        importedFileUri?.let { uri ->
                                            MediaHelper.downloadToPhone(
                                                context = context,
                                                urlOrUri = uri.toString(),
                                                title = title.ifBlank { importedFileName ?: "media" },
                                                type = selectedType.id
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Exporter Téléphone", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Title
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Titre de la publication *") },
            placeholder = { Text("Ex: Tutoriel complet Jetpack Compose ou Clip Vidéo officiel") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("publish_title_input"),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Category Dropdown
        ExposedDropdownMenuBox(
            expanded = categoryDropdownExpanded,
            onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedCategory,
                onValueChange = {},
                readOnly = true,
                label = { Text("Catégorie thématique *") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(
                expanded = categoryDropdownExpanded,
                onDismissRequest = { categoryDropdownExpanded = false }
            ) {
                AppCategories.filter { it != "Tous" }.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat) },
                        onClick = {
                            selectedCategory = cat
                            categoryDropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // External URL (Strictly enforced)
        OutlinedTextField(
            value = externalUrl,
            onValueChange = { externalUrl = it },
            label = { Text("Lien externe vers le contenu (URL) *") },
            placeholder = {
                Text(
                    when (selectedType) {
                        PublicationType.VIDEO -> "https://www.youtube.com/watch?v=... ou Vimeo"
                        PublicationType.DOWNLOAD -> "https://github.com/... ou lien direct sécurisé"
                        PublicationType.MUSIC -> "https://soundcloud.com/... ou lien audio"
                        PublicationType.DOCUMENT -> "https://drive.google.com/... ou PDF web"
                        else -> "https://..."
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("publish_url_input"),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Thumbnail URL (Optional)
        OutlinedTextField(
            value = thumbnailUrl,
            onValueChange = { thumbnailUrl = it },
            label = { Text("URL de la miniature / image (optionnel)") },
            placeholder = { Text("https://example.com/image.jpg") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // AI Writing Assistant Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("publish_ai_assistant_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = BrightVioletPrimary.copy(alpha = 0.08f)
            ),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = BrightAmberAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Aide IA à la rédaction",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = AiModels.getById(selectedAiModel).displayName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Générez automatiquement une description captivante et des tags à partir du titre.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AiModels.ALL.forEach { model ->
                        val isSelected = selectedAiModel == model.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedAiModel = model.id },
                            label = { Text(model.displayName.replace("Gemini ", ""), fontSize = 11.sp) },
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (title.isBlank()) {
                            Toast.makeText(context, "Veuillez d'abord saisir un titre pour la publication.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!aiConfig.hasValidApiKey()) {
                            Toast.makeText(context, "L'administrateur configure actuellement l'IA. Revenez vite !", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            isGeneratingAi = true
                            val result = aiService.generateDescriptionAndTags(
                                apiKey = aiConfig.geminiApiKey,
                                model = selectedAiModel,
                                title = title,
                                category = selectedCategory,
                                type = selectedType.id
                            )
                            isGeneratingAi = false
                            result.fold(
                                onSuccess = { (aiDesc, aiTags) ->
                                    description = aiDesc
                                    if (aiTags.isNotEmpty()) {
                                        tagsInput = aiTags.joinToString(", ")
                                    }
                                    Toast.makeText(context, "Description et tags générés avec succès !", Toast.LENGTH_SHORT).show()
                                },
                                onFailure = {
                                    Toast.makeText(context, "Erreur IA : ${it.message}", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    enabled = !isGeneratingAi,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrightVioletPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("publish_generate_ai_button")
                ) {
                    if (isGeneratingAi) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Génération en cours...", fontSize = 12.sp)
                    } else {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Générer description & tags avec l'IA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Short Description
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description courte (optionnel)") },
            placeholder = { Text("Présentation en 1 à 2 phrases du contenu...") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Full Content (For articles and tutorials)
        if (selectedType == PublicationType.TUTORIAL || selectedType == PublicationType.ARTICLE) {
            OutlinedTextField(
                value = contentText,
                onValueChange = { contentText = it },
                label = { Text("Corps de l'article ou étapes du tutoriel") },
                placeholder = { Text("Rédigez ici le détail de votre article ou tutoriel pas à pas...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Tags
        OutlinedTextField(
            value = tagsInput,
            onValueChange = { tagsInput = it },
            label = { Text("Mots-clés / Tags (séparés par des virgules)") },
            placeholder = { Text("android, tutoriel, musique, gratuit") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (isSubmittedSuccess) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = StatusApprovedGreen.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusApprovedGreen
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAdmin) "Publication validée et en ligne !" else "Contenu soumis ! Il est désormais en attente de modération par l'administrateur.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Submit Button
        Button(
            onClick = {
                if (title.trim().length < 3) {
                    errorMessage = "Veuillez saisir un titre d'au moins 3 caractères"
                    return@Button
                }
                val url = externalUrl.trim()
                if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("content://")) {
                    errorMessage = "Veuillez importer un fichier depuis votre téléphone ou saisir une URL valide (http:// ou https://)"
                    return@Button
                }

                isSubmitting = true
                errorMessage = null

                val tags = tagsInput.split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .take(8)

                val pub = Publication(
                    title = title.trim(),
                    description = description.trim(),
                    type = selectedType.id,
                    category = selectedCategory,
                    externalUrl = externalUrl.trim(),
                    thumbnailUrl = thumbnailUrl.trim(),
                    content = contentText.trim(),
                    authorId = currentUser?.uid ?: "",
                    authorName = currentUser?.displayName ?: "Abonné",
                    authorAvatarUrl = currentUser?.photoUrl?.toString() ?: "",
                    tags = tags
                )

                scope.launch {
                    val result = publicationRepository.createPublication(pub)
                    isSubmitting = false
                    if (result.isSuccess) {
                        isSubmittedSuccess = true
                        Toast.makeText(
                            context,
                            if (isAdmin) "Publication approuvée et publiée !" else "Publication soumise en attente de validation",
                            Toast.LENGTH_LONG
                        ).show()
                        onPublishSuccess()
                    } else {
                        errorMessage = "Erreur lors de la publication: ${result.exceptionOrNull()?.message}"
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("submit_publication_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrightVioletPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            enabled = !isSubmitting
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Enregistrement en cours...")
            } else {
                Text(
                    text = if (isAdmin) "Publier immédiatement (Admin)" else "Soumettre pour modération",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
