package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DocumentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DocViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).documentDao()

    val allDocuments: Flow<List<DocumentEntity>> = dao.getAllDocuments()

    private val _docTitle = MutableStateFlow("")
    val docTitle: StateFlow<String> = _docTitle.asStateFlow()

    private val _frontUri = MutableStateFlow<Uri?>(null)
    val frontUri: StateFlow<Uri?> = _frontUri.asStateFlow()

    private val _backUri = MutableStateFlow<Uri?>(null)
    val backUri: StateFlow<Uri?> = _backUri.asStateFlow()

    private val _layoutStyle = MutableStateFlow("STACKED") // "STACKED" or "SIDE_BY_SIDE"
    val layoutStyle: StateFlow<String> = _layoutStyle.asStateFlow()

    private val _filterType = MutableStateFlow("COLOR") // "COLOR", "BW"
    val filterType: StateFlow<String> = _filterType.asStateFlow()

    private val _cardPrintSize = MutableStateFlow(com.example.utils.CardPrintSize.AUTO_BEST)
    val cardPrintSize: StateFlow<com.example.utils.CardPrintSize> = _cardPrintSize.asStateFlow()

    private val _showCutGuides = MutableStateFlow(true)
    val showCutGuides: StateFlow<Boolean> = _showCutGuides.asStateFlow()

    private val _showLabels = MutableStateFlow(false)
    val showLabels: StateFlow<Boolean> = _showLabels.asStateFlow()

    fun setDocTitle(title: String) {
        _docTitle.value = title
    }

    fun setFrontUri(uri: Uri?) {
        _frontUri.value = uri
    }

    fun setBackUri(uri: Uri?) {
        _backUri.value = uri
    }

    fun setLayoutStyle(style: String) {
        _layoutStyle.value = style
    }

    fun setFilterType(filter: String) {
        _filterType.value = filter
    }

    fun setCardPrintSize(size: com.example.utils.CardPrintSize) {
        _cardPrintSize.value = size
    }

    fun setShowCutGuides(show: Boolean) {
        _showCutGuides.value = show
    }

    fun setShowLabels(show: Boolean) {
        _showLabels.value = show
    }

    fun saveDocument(onSaved: (Long) -> Unit) {
        val title = _docTitle.value.ifBlank { "Untitled Document" }
        val front = _frontUri.value?.toString() ?: ""
        val back = _backUri.value?.toString() ?: ""
        val layout = _layoutStyle.value
        val filter = _filterType.value

        viewModelScope.launch {
            val entity = DocumentEntity(
                title = title,
                frontUri = front,
                backUri = back,
                layoutStyle = layout,
                filterType = filter
            )
            val id = dao.insertDocument(entity)
            onSaved(id)
        }
    }

    fun deleteDocument(entity: DocumentEntity) {
        viewModelScope.launch {
            dao.deleteDocument(entity)
        }
    }

    fun loadDocument(entity: DocumentEntity) {
        _docTitle.value = entity.title
        _frontUri.value = if (entity.frontUri.isNotBlank()) Uri.parse(entity.frontUri) else null
        _backUri.value = if (entity.backUri.isNotBlank()) Uri.parse(entity.backUri) else null
        _layoutStyle.value = entity.layoutStyle
        _filterType.value = entity.filterType
    }

    fun clearCurrent() {
        _docTitle.value = ""
        _frontUri.value = null
        _backUri.value = null
        _layoutStyle.value = "STACKED"
        _filterType.value = "COLOR"
    }
}
