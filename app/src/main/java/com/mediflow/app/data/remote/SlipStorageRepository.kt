package com.mediflow.app.data.remote

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await

class SlipStorageRepository(private val storage: FirebaseStorage) {

    /** Returns the Storage object path, which is what reservations and rules reference. */
    suspend fun uploadSlip(uid: String, source: Uri): Result<String> = runCatching {
        val path = "prescriptions/$uid/rx_${System.currentTimeMillis()}.jpg"
        val ref = storage.reference.child(path)
        ref.putFile(source, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        path
    }

    suspend fun downloadUrl(path: String): Result<String> = runCatching {
        storage.reference.child(path).downloadUrl.await().toString()
    }

    suspend fun delete(path: String): Result<Unit> = runCatching {
        storage.reference.child(path).delete().await()
    }
}
