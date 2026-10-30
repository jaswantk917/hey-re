package org.stypox.dicio.skills.telephone

import android.content.ContentResolver
import android.provider.ContactsContract
import android.util.Log
import java.util.regex.Pattern

/** [score] is how well the name fits what the user said, see [ContactMatcher]; higher is better. */
class Contact(val name: String, val score: Int, private val id: String) {
    fun getNumbers(contentResolver: ContentResolver): List<String> {
        val numbers: MutableList<String> = ArrayList()
        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, NUMBERS_QUERY, arrayOf(id), null
            )?.use { phoneNumberCursor ->
                val cleanedNumbers: MutableSet<String> = HashSet() // used to check for duplication
                val numberColumnIndex = phoneNumberCursor
                    .getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (phoneNumberCursor.moveToNext()) {
                    val number = phoneNumberCursor.getString(numberColumnIndex)
                    if (!number.isNullOrEmpty()) {
                        val cleanedNumber = NUMBER_CLEANER.matcher(number).replaceAll("")
                        if (!cleanedNumbers.contains(cleanedNumber)) {
                            numbers.add(number)
                            cleanedNumbers.add(cleanedNumber) // prevent duplicated numbers
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get numbers for contact $name", e)
        }
        return numbers
    }

    /** The number WhatsApp knows this contact by, from the row WhatsApp adds to contacts. */
    fun getWhatsAppNumber(contentResolver: ContentResolver): String? = try {
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.DATA1),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(id, WHATSAPP_PROFILE_MIMETYPE),
            null,
        )?.use { cursor ->
            // "919876543210@s.whatsapp.net"
            if (cursor.moveToFirst()) cursor.getString(0)?.substringBefore('@') else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not get WhatsApp number for contact $name", e)
        null
    }

    companion object {
        private const val WHATSAPP_PROFILE_MIMETYPE = "vnd.android.cursor.item/vnd.com.whatsapp.profile"
        private val TAG = Contact::class.java.simpleName
        private const val NUMBERS_QUERY = (ContactsContract.CommonDataKinds.Phone.NUMBER
                + " IS NOT NULL AND " + ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?")
        private val NUMBER_CLEANER = Pattern.compile("\\D")

        fun getFilteredSortedContacts(
            contentResolver: ContentResolver,
            userContactName: String
        ): List<Contact> {
            val contacts: MutableList<Contact> = ArrayList()
            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                null, null, null, null
            )?.use { contactCursor ->
                val contactNameColumnIndex =
                    contactCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val contactIdColumnIndex =
                    contactCursor.getColumnIndex(ContactsContract.Contacts._ID)
                val starredColumnIndex =
                    contactCursor.getColumnIndex(ContactsContract.Contacts.STARRED)
                while (contactCursor.moveToNext()) {
                    val name = contactCursor.getString(contactNameColumnIndex) ?: continue
                    val id = contactCursor.getString(contactIdColumnIndex)
                    val starred = starredColumnIndex >= 0 && contactCursor.getInt(starredColumnIndex) == 1
                    ContactMatcher.score(userContactName, name, starred)?.let {
                        contacts.add(Contact(name, it, id))
                    }
                }
            }
            contacts.sortByDescending { it.score }
            return contacts
        }
    }
}
