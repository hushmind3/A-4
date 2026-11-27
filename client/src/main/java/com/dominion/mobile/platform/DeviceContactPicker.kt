package com.dominion.mobile.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.dominion.mobile.data.ContactTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DeviceContactPicker {
    fun intent(email: Boolean) = Intent(Intent.ACTION_PICK).setType(
        if (email) Email.CONTENT_TYPE else Phone.CONTENT_TYPE)

    suspend fun readNamed(context: Context, uri: Uri, email: Boolean): com.dominion.mobile.data.LocalContactCard =
        withContext(Dispatchers.IO) {
            require(uri.scheme == "content" && uri.authority == "com.android.contacts")
            val column = if (email) Email.ADDRESS else Phone.NUMBER
            val row = requireNotNull(context.contentResolver.query(uri,
                arrayOf("display_name", column), null, null, null)).use {
                check(it.moveToFirst())
                val name = it.getString(0).orEmpty().ifBlank { "연락처" }
                val target = requireNotNull(it.getString(1))
                check(!it.moveToNext())
                if (email) com.dominion.mobile.data.LocalContactCard(name, email = target)
                else com.dominion.mobile.data.LocalContactCard(name, phone = target)
            }
            row.validated()
        }

    /** Read only the selected phone/email row using the picker grant. */
    suspend fun read(context: Context, uri: Uri, email: Boolean): String = withContext(Dispatchers.IO) {
        require(uri.scheme == "content" && uri.authority == "com.android.contacts")
        val column = if (email) Email.ADDRESS else Phone.NUMBER
        val raw = requireNotNull(context.contentResolver.query(uri, arrayOf(column), null, null, null)).use {
            check(it.moveToFirst())
            val value = requireNotNull(it.getString(0))
            check(!it.moveToNext()) { "Select one contact field" }
            value
        }
        if (email) ContactTarget.email(raw) else ContactTarget.phone(raw)
    }
}
