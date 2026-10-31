package com.agent1.android.productivity.logic.data

import android.content.Context
import android.provider.ContactsContract
import org.json.JSONArray
import org.json.JSONObject

/** 通讯录只读搜索（logic.data IO）。 */
class AndroidContactsAccess(private val context: Context) {

    fun search(query: String, limit: Int, withPhones: Boolean, unmaskPhones: Boolean): JSONObject {
        val q = query.trim()
        if (q.isEmpty()) {
            return JSONObject().put("error", "bad_argument").put("message", "query is required")
        }
        val max = limit.coerceIn(1, 50)
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        val args = arrayOf("%$q%")
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
        )
        val contacts = JSONArray()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            selection,
            args,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC LIMIT $max",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val lookupIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val phoneIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)
            while (cursor.moveToNext() && contacts.length() < max) {
                val id = cursor.getLong(idIdx)
                val name = cursor.getString(nameIdx).orEmpty()
                val lookup = cursor.getString(lookupIdx).orEmpty()
                val row = JSONObject()
                    .put("id", id)
                    .put("displayName", name)
                    .put("lookupKey", lookup)
                if (withPhones) {
                    if (cursor.getInt(phoneIdx) != 0) {
                        row.put("phones", phonesForContact(id, mask = !unmaskPhones))
                    } else {
                        row.put("phones", JSONArray())
                    }
                }
                contacts.put(row)
            }
        }
        return JSONObject().put("contacts", contacts).put("count", contacts.length())
    }

    private fun phonesForContact(contactId: Long, mask: Boolean): JSONArray {
        val phones = JSONArray()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        context.contentResolver.query(uri, projection, selection, arrayOf(contactId.toString()), null)?.use { c ->
            val numIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) {
                val raw = c.getString(numIdx).orEmpty()
                phones.put(if (mask) ContactsPhoneMask.mask(raw) else raw)
            }
        }
        return phones
    }
}
